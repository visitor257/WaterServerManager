package com.ponko.craftfriend.account;

import com.ponko.craftfriend.CraftFriend;
import com.ponko.craftfriend.CraftFriendServer;
import com.ponko.craftfriend.config.CraftFriendConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录门禁：未登录的玩家被钉在出生点上空，不能移动、不能交互、不能聊天，
 * 只允许 /login 和 /register。登录后恢复原来的模式和位置。
 */
public final class AuthService {

    private static final Set<UUID> AUTHENTICATED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, GameMode> PREVIOUS_MODE = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3d> ANCHORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PROMPT = new ConcurrentHashMap<>();
    private static final Map<String, Attempt> ATTEMPTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LIMBO_INDEX = new ConcurrentHashMap<>();

    private record Attempt(int failures, long lockedUntil) {
    }

    private static int ticks;

    private AuthService() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (!CraftFriendConfig.get().auth.enabled) {
                AUTHENTICATED.add(player.getUuid());
                return;
            }
            if (!AUTHENTICATED.contains(player.getUuid())) {
                // 未登录绝不允许带 OP 进来（离线服按名字算 UUID，改名就能冒充）。
                server.getPlayerManager().removeFromOperators(player.getGameProfile());
                enterLimbo(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            server.getPlayerManager().removeFromOperators(handler.player.getGameProfile());
            PremiumVerifier.removeOnline(handler.player.getGameProfile().getName());
            AUTHENTICATED.remove(handler.player.getUuid());
            PREVIOUS_MODE.remove(handler.player.getUuid());
            ANCHORS.remove(handler.player.getUuid());
            LAST_PROMPT.remove(handler.player.getUuid());
            LIMBO_INDEX.remove(handler.player.getUuid());
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
                !CraftFriendConfig.get().auth.enabled || isAuthenticated(sender));

        ServerTickEvents.END_SERVER_TICK.register(AuthService::tick);
    }

    public static boolean isAuthenticated(ServerPlayerEntity player) {
        return AUTHENTICATED.contains(player.getUuid());
    }

    public static void enterLimbo(ServerPlayerEntity player) {
        // 如果上次是在 limbo 里退出的，存档里的模式已经是旁观；
        // 不能把它当成"原模式"，否则登录后还是旁观。
        GameMode before = player.interactionManager.getGameMode();
        if (before == GameMode.SPECTATOR) {
            before = GameMode.SURVIVAL;
        }
        PREVIOUS_MODE.putIfAbsent(player.getUuid(), before);
        ServerWorld world = player.getServer().getOverworld();
        BlockPos spawn = world.getSpawnPos();
        Vec3d anchor = new Vec3d(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D);

        ANCHORS.put(player.getUuid(), anchor);
        Integer index = LIMBO_INDEX.get(player.getUuid());
        if (index == null) {
            java.util.Set<Integer> used = new java.util.HashSet<>(LIMBO_INDEX.values());
            int n = 1;
            while (used.contains(n)) {
                n++;
            }
            index = n;
            LIMBO_INDEX.put(player.getUuid(), index);
        }
        player.setCustomName(Text.literal("未登录" + index));
        player.setCustomNameVisible(true);
        refreshTabName(player);
        player.changeGameMode(GameMode.SPECTATOR);
        player.setNoGravity(true);
        player.setInvulnerable(true);
        player.setVelocity(Vec3d.ZERO);
        player.teleport(world, anchor.x, anchor.y, anchor.z, 0.0F, 0.0F);

        player.sendMessage(Text.literal(
                "你还没有登录。请 /login <账户名> <密码>；没有账号：/register <账户名> <密码> <重复密码>"), false);
        player.sendMessage(Text.literal(
                "未登录：/login <账户名> <密码>  |  注册：/register <账户名> <密码> <重复密码>"), true);
        LAST_PROMPT.put(player.getUuid(), System.currentTimeMillis());
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (ANCHORS.isEmpty()) {
            return;
        }
        ticks++;
        boolean everyOther = ticks % 2 == 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Vec3d> entry : ANCHORS.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                ANCHORS.remove(entry.getKey());
                continue;
            }
            if (isAuthenticated(player)) {
                release(player);
                continue;
            }
            if (!everyOther) {
                continue;
            }

            Vec3d anchor = entry.getValue();
            if (player.getPos().squaredDistanceTo(anchor) > 0.01D
                    || player.getVelocity().lengthSquared() > 1.0E-4D) {
                player.setVelocity(Vec3d.ZERO);
                player.teleport((ServerWorld) player.getWorld(),
                        anchor.x, anchor.y, anchor.z, 0.0F, 0.0F);
            }

            Long last = LAST_PROMPT.get(entry.getKey());
            if (last == null || now - last > 5_000L) {
                LAST_PROMPT.put(entry.getKey(), now);
                player.sendMessage(Text.literal(
                        "未登录：/login <账户名> <密码>  |  注册：/register <账户名> <密码> <重复密码>"), true);
            }
        }
    }

    private static void refreshTabName(ServerPlayerEntity player) {
        player.getServer().getPlayerManager().sendToAll(
                new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    private static void release(ServerPlayerEntity player) {
        ANCHORS.remove(player.getUuid());
        LAST_PROMPT.remove(player.getUuid());
        LIMBO_INDEX.remove(player.getUuid());
        player.setNoGravity(false);
        player.setInvulnerable(false);
    }

    public static void authenticate(ServerPlayerEntity player) {
        AUTHENTICATED.add(player.getUuid());
        release(player);

        // 权限只跟账号角色走：admin 才给 OP，普通玩家永远是 0 级。
        String role = CraftFriendServer.ACCOUNTS.roleOf(player.getGameProfile().getName());
        if ("admin".equals(role)) {
            player.getServer().getPlayerManager().addToOperators(player.getGameProfile());
        } else {
            player.getServer().getPlayerManager().removeFromOperators(player.getGameProfile());
        }
        GameMode mode = PREVIOUS_MODE.remove(player.getUuid());
        if (mode == null || mode == GameMode.SPECTATOR) {
            mode = GameMode.SURVIVAL;
        }
        player.changeGameMode(mode);
        ServerWorld world = player.getServer().getOverworld();
        BlockPos spawn = world.getSpawnPos();
        player.setVelocity(Vec3d.ZERO);
        player.teleport(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        PlayerDataService.onAuthenticated(player);

        // 显示名用账户名，而不是客户端的档案名。
        AccountStore.Account account =
                CraftFriendServer.ACCOUNTS.resolve(player.getGameProfile().getName());
        if (account != null) {
            player.setCustomName(Text.literal(account.name));
            player.setCustomNameVisible(true);
            refreshTabName(player);
        }

        player.sendMessage(Text.literal("登录成功。"), false);
        CraftFriend.LOGGER.info("[Auth] {} 已登录", player.getGameProfile().getName());
    }

    public static boolean registerPlayer(ServerPlayerEntity player, String accountName,
                                         String password, String confirm, String inviteCode) {
        if (!accountName.matches("[A-Za-z0-9_]{3,16}")) {
            player.sendMessage(Text.literal("账户名只能是 3-16 位字母、数字、下划线"), false);
            return false;
        }
        if (!password.equals(confirm)) {
            player.sendMessage(Text.literal("两次输入的密码不一致"), false);
            return false;
        }
        AccountStore accounts = CraftFriendServer.ACCOUNTS;
        if (accounts.exists(accountName)) {
            player.sendMessage(Text.literal("账户名已被占用"), false);
            return false;
        }
        String mcName = player.getGameProfile().getName();
        if (accounts.findByMcName(mcName) != null) {
            player.sendMessage(Text.literal("当前游戏名已经绑定了账号，请用 /login <账户名> <密码>"), false);
            return false;
        }

        String role = "player";
        if (inviteCode != null && !inviteCode.isBlank()) {
            AccountStore.Invite invite = accounts.consumeInvite(inviteCode);
            if (invite == null) {
                player.sendMessage(Text.literal("邀请码无效或已过期"), false);
                return false;
            }
            role = invite.role;
        } else if (CraftFriendConfig.get().auth.requireInvite) {
            player.sendMessage(Text.literal(
                    "本服注册需要邀请码：/register <账户名> <密码> <重复密码> <邀请码>"), false);
            return false;
        }

        if (!accounts.create(accountName, password, role, mcName)) {
            player.sendMessage(Text.literal("注册失败"), false);
            return false;
        }
        authenticate(player);
        player.sendMessage(Text.literal("账户名：" + accountName), false);
        return true;
    }

    public static boolean loginWith(ServerPlayerEntity player, String accountName, String password) {
        String limitKey = player.getGameProfile().getName().toLowerCase();
        Attempt attempt = ATTEMPTS.get(limitKey);
        if (attempt != null && attempt.lockedUntil() > System.currentTimeMillis()) {
            player.sendMessage(Text.literal("尝试次数过多，请稍后再试"), false);
            return false;
        }
        if (!CraftFriendServer.ACCOUNTS.verify(accountName, password)) {
            int failures = (attempt == null ? 0 : attempt.failures()) + 1;
            long lock = failures >= 5 ? System.currentTimeMillis() + 5L * 60L * 1000L : 0L;
            ATTEMPTS.put(limitKey, new Attempt(failures, lock));
            player.sendMessage(Text.literal("账户名或密码不对"), false);
            return false;
        }
        kickSameAccount(player, accountName);
        if (!CraftFriendServer.ACCOUNTS.bind(accountName, player.getGameProfile().getName())) {
            player.sendMessage(Text.literal("当前游戏名已绑定其他账号"), false);
            return false;
        }
        // 密码登录让之前的令牌全部失效。
        CraftFriendServer.ACCOUNTS.clearToken(accountName);
        ATTEMPTS.remove(limitKey);
        authenticate(player);
        return true;
    }

    /** 令牌登录：不使令牌失效。 */
    public static boolean tokenLogin(ServerPlayerEntity player, String token) {
        AccountStore.Account account = CraftFriendServer.ACCOUNTS.findByToken(token);
        if (account == null) {
            player.sendMessage(Text.literal("令牌无效或已失效"), false);
            return false;
        }
        kickSameAccount(player, account.name);
        if (!CraftFriendServer.ACCOUNTS.bind(account.name, player.getGameProfile().getName())) {
            player.sendMessage(Text.literal("当前游戏名已绑定其他账号"), false);
            return false;
        }
        authenticate(player);
        return true;
    }

    /** 同一账号不允许两个在线会话：保存旧会话进度后踢掉。 */
    private static void kickSameAccount(ServerPlayerEntity player, String accountName) {
        AccountStore.Account account = CraftFriendServer.ACCOUNTS.get(accountName);
        if (account == null) {
            return;
        }
        for (ServerPlayerEntity other : player.getServer().getPlayerManager().getPlayerList()) {
            if (other.getUuid().equals(player.getUuid()) || !isAuthenticated(other)) {
                continue;
            }
            if (!account.id.equals(CraftFriendServer.ACCOUNTS.idOf(other.getGameProfile().getName()))) {
                continue;
            }
            PlayerDataService.detachAndSave(other);
            other.networkHandler.disconnect(Text.literal("该账号已在另一处登录"));
            CraftFriend.LOGGER.info("[Auth] 踢掉同账号旧会话 {}", other.getGameProfile().getName());
        }
    }
}
