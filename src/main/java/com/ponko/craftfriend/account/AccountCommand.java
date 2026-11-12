package com.ponko.craftfriend.account;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ponko.craftfriend.CraftFriendServer;
import com.ponko.craftfriend.config.CraftFriendConfig;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** 内部账号相关指令：/login /register /account ... */
public final class AccountCommand {

    private AccountCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("login")
                .executes(context -> {
                    context.getSource().sendFeedback(() -> Text.literal(
                            "用法：/login <账户名> <密码>"), false);
                    return 1;
                })
                .then(CommandManager.argument("account", StringArgumentType.word())
                        .then(CommandManager.argument("password", StringArgumentType.word())
                                .executes(context -> login(context,
                                        StringArgumentType.getString(context, "account"),
                                        StringArgumentType.getString(context, "password"))))));

        dispatcher.register(CommandManager.literal("token")
                .executes(AccountCommand::token));

        dispatcher.register(CommandManager.literal("signin")
                .then(CommandManager.argument("token", StringArgumentType.word())
                        .executes(context -> signin(context,
                                StringArgumentType.getString(context, "token")))));

        dispatcher.register(CommandManager.literal("register")
                .then(CommandManager.argument("account", StringArgumentType.word())
                        .then(CommandManager.argument("password", StringArgumentType.word())
                                .then(CommandManager.argument("confirm", StringArgumentType.word())
                                        .executes(context -> register(context,
                                                StringArgumentType.getString(context, "account"),
                                                StringArgumentType.getString(context, "password"),
                                                StringArgumentType.getString(context, "confirm"),
                                                null))
                                        .then(CommandManager.argument("invite", StringArgumentType.word())
                                                .executes(context -> register(context,
                                                        StringArgumentType.getString(context, "account"),
                                                        StringArgumentType.getString(context, "password"),
                                                        StringArgumentType.getString(context, "confirm"),
                                                        StringArgumentType.getString(context, "invite"))))))));

        dispatcher.register(CommandManager.literal("account")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("invite")
                        .executes(context -> invite(context, "player"))
                        .then(CommandManager.argument("role", StringArgumentType.word())
                                .executes(context -> invite(context,
                                        StringArgumentType.getString(context, "role")))))
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .then(CommandManager.argument("password", StringArgumentType.greedyString())
                                        .executes(context -> create(context,
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "password"))))))
                .then(CommandManager.literal("password")
                        .then(CommandManager.argument("old", StringArgumentType.greedyString())
                                .then(CommandManager.argument("new", StringArgumentType.greedyString())
                                        .executes(context -> changePassword(context,
                                                StringArgumentType.getString(context, "old"),
                                                StringArgumentType.getString(context, "new"))))))
                .then(CommandManager.literal("role")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .then(CommandManager.argument("role", StringArgumentType.word())
                                        .executes(context -> role(context,
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "role"))))))
                .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(context -> delete(context,
                                        StringArgumentType.getString(context, "name")))))
                .then(CommandManager.literal("list").executes(AccountCommand::list))
                .then(CommandManager.literal("enable")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> enable(context,
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(CommandManager.literal("priority")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> priority(context,
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(CommandManager.literal("stableuuid")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> stableUuid(context,
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(CommandManager.literal("open")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> open(context,
                                        BoolArgumentType.getBool(context, "enabled"))))));
    }

    private static int login(CommandContext<ServerCommandSource> context,
                             String account, String password) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("只有玩家能登录"));
            return 0;
        }
        if (AuthService.isAuthenticated(player)) {
            player.sendMessage(Text.literal("你已经登录了"), false);
            return 0;
        }
        return AuthService.loginWith(player, account, password) ? 1 : 0;
    }

    private static int token(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("只有玩家能生成令牌"));
            return 0;
        }
        if (!AuthService.isAuthenticated(player)) {
            player.sendMessage(Text.literal("请先登录再生成令牌"), false);
            return 0;
        }
        AccountStore.Account account =
                CraftFriendServer.ACCOUNTS.findByMcName(player.getGameProfile().getName());
        if (account == null) {
            player.sendMessage(Text.literal("当前游戏名没有绑定账号"), false);
            return 0;
        }
        String token = CraftFriendServer.ACCOUNTS.generateToken(account.name);
        player.sendMessage(Text.literal("登录令牌（点击复制）：")
                .append(Text.literal(token)
                        .styled(style -> style.withClickEvent(
                                new net.minecraft.text.ClickEvent(
                                        net.minecraft.text.ClickEvent.Action.COPY_TO_CLIPBOARD, token)))), false);
        player.sendMessage(Text.literal("下次进服输入 /signin " + token), false);
        return 1;
    }

    private static int signin(CommandContext<ServerCommandSource> context, String token) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("只有玩家能登录"));
            return 0;
        }
        if (AuthService.isAuthenticated(player)) {
            player.sendMessage(Text.literal("你已经登录了"), false);
            return 0;
        }
        return AuthService.tokenLogin(player, token) ? 1 : 0;
    }

    private static int changePassword(CommandContext<ServerCommandSource> context,
                                       String oldPassword, String newPassword) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("只有玩家能改密码"));
            return 0;
        }
        if (!CraftFriendServer.ACCOUNTS.changePassword(
                player.getGameProfile().getName(), oldPassword, newPassword)) {
            player.sendMessage(Text.literal("旧密码不对"), false);
            return 0;
        }
        player.sendMessage(Text.literal("密码已更新"), false);
        return 1;
    }

    private static int register(CommandContext<ServerCommandSource> context,
                                String account, String password, String confirm, String invite) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("只有玩家能注册"));
            return 0;
        }
        if (AuthService.isAuthenticated(player)) {
            player.sendMessage(Text.literal("你已经登录了"), false);
            return 0;
        }
        return AuthService.registerPlayer(player, account, password, confirm, invite) ? 1 : 0;
    }

    private static int priority(CommandContext<ServerCommandSource> context, boolean enabled) {
        CraftFriendConfig config = CraftFriendConfig.get();
        config.auth.priority = enabled;
        if (enabled) {
            config.auth.stableUuid = true;
            UuidMode.stable = true;
        }
        CraftFriendConfig.save();
        UuidMode.priority = enabled;
        context.getSource().sendFeedback(() -> Text.literal(
                "正版优先顶号：" + (enabled ? "开启（固定 UUID 模式下生效）" : "关闭")), false);
        return 1;
    }

    private static int stableUuid(CommandContext<ServerCommandSource> context, boolean enabled) {
        CraftFriendConfig config = CraftFriendConfig.get();
        config.auth.stableUuid = enabled;
        CraftFriendConfig.save();
        UuidMode.stable = enabled;
        context.getSource().sendFeedback(() -> Text.literal(
                "UUID 模式：" + (enabled
                        ? "按名字固定（YSM 缓存命中，同名不能共存）"
                        : "随机会话（同名可共存，YSM 每次同步）")), false);
        return 1;
    }

    private static int open(CommandContext<ServerCommandSource> context, boolean open) {
        CraftFriendConfig config = CraftFriendConfig.get();
        config.auth.requireInvite = !open;
        CraftFriendConfig.save();
        context.getSource().sendFeedback(() -> Text.literal(
                "注册方式：" + (open ? "开放（不需要邀请码）" : "必须带邀请码")), false);
        return 1;
    }

    private static int invite(CommandContext<ServerCommandSource> context, String role) {
        String code = CraftFriendServer.ACCOUNTS.createInvite(role, 60L * 60L * 1000L);
        context.getSource().sendFeedback(() -> Text.literal(
                "邀请码：" + code + "（1 小时内有效，角色 " + role + "）"), false);
        return 1;
    }

    private static int create(CommandContext<ServerCommandSource> context,
                              String name, String password) {
        if (!CraftFriendServer.ACCOUNTS.create(name, password, "player", name)) {
            context.getSource().sendError(Text.literal("账号已存在：" + name));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("已创建账号 " + name), false);
        return 1;
    }

    private static int role(CommandContext<ServerCommandSource> context, String name, String role) {
        if (!CraftFriendServer.ACCOUNTS.setRole(name, role)) {
            context.getSource().sendError(Text.literal("没有这个账号：" + name));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal(
                "已把 " + name + " 的角色设为 " + role), false);
        return 1;
    }

    private static int delete(CommandContext<ServerCommandSource> context, String name) {
        if (!CraftFriendServer.ACCOUNTS.delete(name)) {
            context.getSource().sendError(Text.literal("没有这个账号：" + name));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("已删除账号 " + name), false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        var accounts = CraftFriendServer.ACCOUNTS.all();
        if (accounts.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("还没有内部账号"), false);
            return 1;
        }
        String body = accounts.stream()
                .map(account -> account.name + "(" + account.mcName + "," + account.role + ")")
                .collect(java.util.stream.Collectors.joining("、"));
        context.getSource().sendFeedback(() -> Text.literal("账号：" + body), false);
        return 1;
    }

    private static int enable(CommandContext<ServerCommandSource> context, boolean enabled) {
        CraftFriendConfig config = CraftFriendConfig.get();
        config.auth.enabled = enabled;
        CraftFriendConfig.save();
        context.getSource().sendFeedback(() -> Text.literal(
                "内部账号登录：" + (enabled ? "开启（未登录进 limbo）" : "关闭")), false);
        return 1;
    }
}
