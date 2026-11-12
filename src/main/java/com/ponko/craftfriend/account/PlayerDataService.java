package com.ponko.craftfriend.account;

import com.ponko.craftfriend.CraftFriend;
import com.ponko.craftfriend.CraftFriendServer;
import com.ponko.craftfriend.config.CraftFriendConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录前的身份与存档隔离。
 *
 * <p>未登录玩家的背包/状态先被快照并清空，避免用别人的名字进来就看到别人的东西；
 * 登录成功后再从该账号自己的存档恢复。退出时：已登录存回账号，未登录还原快照。</p>
 *
 * <p>当前覆盖背包、血量、饥饿、经验；位置、成就、统计、末影箱暂未迁移。</p>
 */
public final class PlayerDataService {

    private static final Map<UUID, NbtCompound> ORIGINAL = new ConcurrentHashMap<>();
    /** 本次会话是否已经登录成功；不能依赖 AuthService 的集合，事件顺序会把状态先清掉。 */
    private static final java.util.Set<UUID> LOGGED_IN = ConcurrentHashMap.newKeySet();

    private PlayerDataService() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!CraftFriendConfig.get().auth.enabled) {
                return;
            }
            ServerPlayerEntity player = handler.player;
            LOGGED_IN.remove(player.getUuid());
            if (AuthService.isAuthenticated(player)) {
                return;
            }
            NbtCompound original = snapshot(player);
            ORIGINAL.put(player.getUuid(), original);
            write(stashFile(player), original);
            clear(player);
            CraftFriend.LOGGER.info("[Data] 已隔离 {} 的登录前存档", player.getGameProfile().getName());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!CraftFriendConfig.get().auth.enabled) {
                return;
            }
            ServerPlayerEntity player = handler.player;
            if (LOGGED_IN.remove(player.getUuid())) {
                saveAccount(player);
                saveAdvancements(player);
            }
            // 无论是否登录，退出前都把"这个名字原本的存档"还原回去，
            // 避免冒名登录把原主人的原版 playerdata 覆盖掉。
            NbtCompound original = ORIGINAL.remove(player.getUuid());
            if (original != null) {
                apply(player, original);
            }
        });
    }

    /** 登录成功后调用：加载这个账号自己的存档。 */
    public static void onAuthenticated(ServerPlayerEntity player) {
        LOGGED_IN.add(player.getUuid());
        String name = player.getGameProfile().getName();
        String accountId = CraftFriendServer.ACCOUNTS.idOf(name);
        if (accountId == null) {
            return;
        }
        NbtCompound saved = read(dataFile(accountId));
        if (saved == null) {
            // 第一次登录：把原版旧存档当作初始数据。
            saved = read(vanillaFile(player));
        }
        if (saved != null) {
            apply(player, saved);
            applyPosition(player, saved);
        }
        syncAdvancementsFromAccount(player);
    }

    private static void syncAdvancementsFromAccount(ServerPlayerEntity player) {
        String accountId = CraftFriendServer.ACCOUNTS.idOf(player.getGameProfile().getName());
        if (accountId == null) {
            return;
        }
        Path accountFile = advancementFile(player, accountId);
        if (!Files.exists(accountFile)) {
            return;
        }
        try {
            Files.copy(accountFile, advancementFile(player, player.getUuidAsString()),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            player.getAdvancementTracker()
                    .reload(player.getServer().getAdvancementLoader());
        } catch (IOException e) {
            CraftFriend.LOGGER.warn("[Data] 迁移成就失败: {}", e.getMessage());
        }
    }

    private static void saveAdvancements(ServerPlayerEntity player) {
        String accountId = CraftFriendServer.ACCOUNTS.idOf(player.getGameProfile().getName());
        if (accountId == null) {
            return;
        }
        try {
            player.getAdvancementTracker().save();
            Path sessionFile = advancementFile(player, player.getUuidAsString());
            if (Files.exists(sessionFile)) {
                Files.copy(sessionFile, advancementFile(player, accountId),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            CraftFriend.LOGGER.warn("[Data] 保存成就失败: {}", e.getMessage());
        }
    }

    private static Path advancementFile(ServerPlayerEntity player, String uuid) {
        return player.getServer().getSavePath(net.minecraft.util.WorldSavePath.ADVANCEMENTS)
                .resolve(uuid + ".json");
    }

    /** 回到退出前的坐标、朝向和维度；存档里没有就留在出生点。 */
    private static void applyPosition(ServerPlayerEntity player, NbtCompound nbt) {
        double x;
        double y;
        double z;
        if (nbt.contains("Pos")) {
            NbtList pos = nbt.getList("Pos", NbtElement.DOUBLE_TYPE);
            x = pos.getDouble(0);
            y = pos.getDouble(1);
            z = pos.getDouble(2);
        } else if (nbt.contains("X")) {
            x = nbt.getDouble("X");
            y = nbt.getDouble("Y");
            z = nbt.getDouble("Z");
        } else {
            return;
        }

        float yaw = nbt.getFloat("Yaw");
        float pitch = nbt.getFloat("Pitch");
        if (nbt.contains("Rotation")) {
            NbtList rotation = nbt.getList("Rotation", NbtElement.FLOAT_TYPE);
            yaw = rotation.getFloat(0);
            pitch = rotation.getFloat(1);
        }

        String dimension = nbt.contains("Dimension")
                ? nbt.getString("Dimension") : "minecraft:overworld";
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(dimension);
        if (id == null) {
            return;
        }
        net.minecraft.server.world.ServerWorld world = player.getServer().getWorld(
                net.minecraft.registry.RegistryKey.of(
                        net.minecraft.registry.RegistryKeys.WORLD, id));
        if (world != null) {
            player.teleport(world, x, y, z, yaw, pitch);
        }
    }

    /** 同账号在别处登录时：先保存当前进度，再把名字原本的存档还原，最后才断开。 */
    public static void detachAndSave(ServerPlayerEntity player) {
        if (LOGGED_IN.remove(player.getUuid())) {
            saveAccount(player);
        }
        NbtCompound original = ORIGINAL.remove(player.getUuid());
        if (original != null) {
            apply(player, original);
        }
    }

    private static void saveAccount(ServerPlayerEntity player) {
        String accountId = CraftFriendServer.ACCOUNTS.idOf(player.getGameProfile().getName());
        if (accountId != null) {
            write(dataFile(accountId), snapshot(player));
        }
    }

    private static NbtCompound snapshot(ServerPlayerEntity player) {
        NbtCompound nbt = new NbtCompound();
        NbtList inventory = new NbtList();
        player.getInventory().writeNbt(inventory);
        nbt.put("Inventory", inventory);
        nbt.putFloat("Health", player.getHealth());
        nbt.putInt("Food", player.getHungerManager().getFoodLevel());
        nbt.putFloat("Saturation", player.getHungerManager().getSaturationLevel());
        nbt.putInt("XpLevel", player.experienceLevel);
        nbt.putFloat("XpProgress", player.experienceProgress);
        nbt.putInt("XpTotal", player.totalExperience);

        nbt.put("EnderChest",
                player.getEnderChestInventory().toNbtList(player.getServer().getRegistryManager()));
        nbt.put("Attributes", player.getAttributes().toNbt());
        nbt.putFloat("Exhaustion", player.getHungerManager().getExhaustion());

        net.minecraft.util.math.BlockPos spawn = player.getSpawnPointPosition();
        if (spawn != null) {
            nbt.putInt("SpawnX", spawn.getX());
            nbt.putInt("SpawnY", spawn.getY());
            nbt.putInt("SpawnZ", spawn.getZ());
            nbt.putString("SpawnDimension",
                    player.getSpawnPointDimension().getValue().toString());
            nbt.putFloat("SpawnAngle", player.getSpawnAngle());
        }

        NbtList effects = new NbtList();
        for (net.minecraft.entity.effect.StatusEffectInstance effect : player.getStatusEffects()) {
            effects.add(effect.writeNbt());
        }
        nbt.put("Effects", effects);
        nbt.putInt("FireTicks", player.getFireTicks());

        nbt.putDouble("X", player.getX());
        nbt.putDouble("Y", player.getY());
        nbt.putDouble("Z", player.getZ());
        nbt.putFloat("Yaw", player.getYaw());
        nbt.putFloat("Pitch", player.getPitch());
        nbt.putString("Dimension", player.getWorld().getRegistryKey().getValue().toString());
        return nbt;
    }

    private static void apply(ServerPlayerEntity player, NbtCompound nbt) {
        if (nbt.contains("Inventory")) {
            player.getInventory().readNbt(nbt.getList("Inventory", NbtElement.COMPOUND_TYPE));
        }
        if (nbt.contains("Health")) {
            player.setHealth(Math.max(1.0F, nbt.getFloat("Health")));
        }
        if (nbt.contains("Food")) {
            player.getHungerManager().setFoodLevel(nbt.getInt("Food"));
        }
        if (nbt.contains("Saturation")) {
            player.getHungerManager().setSaturationLevel(nbt.getFloat("Saturation"));
        }
        if (nbt.contains("XpLevel")) {
            player.experienceLevel = nbt.getInt("XpLevel");
        }
        if (nbt.contains("XpProgress")) {
            player.experienceProgress = nbt.getFloat("XpProgress");
        }
        if (nbt.contains("XpTotal")) {
            player.totalExperience = nbt.getInt("XpTotal");
        }
        if (nbt.contains("EnderChest")) {
            player.getEnderChestInventory().readNbtList(
                    nbt.getList("EnderChest", NbtElement.COMPOUND_TYPE),
                    player.getServer().getRegistryManager());
        }
        if (nbt.contains("Attributes")) {
            player.getAttributes().readNbt(nbt.getList("Attributes", NbtElement.COMPOUND_TYPE));
        }
        if (nbt.contains("Exhaustion")) {
            player.getHungerManager().setExhaustion(nbt.getFloat("Exhaustion"));
        }
        if (nbt.contains("SpawnX") && nbt.contains("SpawnDimension")) {
            net.minecraft.util.Identifier id =
                    net.minecraft.util.Identifier.tryParse(nbt.getString("SpawnDimension"));
            if (id != null) {
                player.setSpawnPoint(
                        net.minecraft.registry.RegistryKey.of(
                                net.minecraft.registry.RegistryKeys.WORLD, id),
                        new net.minecraft.util.math.BlockPos(
                                nbt.getInt("SpawnX"), nbt.getInt("SpawnY"), nbt.getInt("SpawnZ")),
                        nbt.getFloat("SpawnAngle"), false, false);
            }
        }
        if (nbt.contains("Effects")) {
            player.clearStatusEffects();
            for (var element : nbt.getList("Effects", NbtElement.COMPOUND_TYPE)) {
                net.minecraft.entity.effect.StatusEffectInstance effect =
                        net.minecraft.entity.effect.StatusEffectInstance.fromNbt((NbtCompound) element);
                if (effect != null) {
                    player.addStatusEffect(effect);
                }
            }
        }
        if (nbt.contains("FireTicks")) {
            player.setFireTicks(nbt.getInt("FireTicks"));
        }
    }

    private static void clear(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.setHealth(20.0F);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(5.0F);
        player.experienceLevel = 0;
        player.experienceProgress = 0.0F;
        player.totalExperience = 0;
        player.clearStatusEffects();
        player.setFireTicks(0);
    }

    private static Path base() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("craftfriend");
    }

    private static Path dataFile(String accountId) {
        return base().resolve("playerdata").resolve(accountId + ".dat");
    }

    private static Path stashFile(ServerPlayerEntity player) {
        return base().resolve("stash")
                .resolve(player.getGameProfile().getName() + "-" + player.getUuid() + ".dat");
    }

    private static Path vanillaFile(ServerPlayerEntity player) {
        return player.getServer().getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA)
                .resolve(player.getUuidAsString() + ".dat");
    }

    private static NbtCompound read(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        } catch (IOException | RuntimeException e) {
            CraftFriend.LOGGER.warn("[Data] 读取存档失败 {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static void write(Path path, NbtCompound nbt) {
        try {
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(nbt, path);
        } catch (IOException e) {
            CraftFriend.LOGGER.warn("[Data] 保存存档失败 {}: {}", path, e.getMessage());
        }
    }
}
