package com.ponko.craftfriend.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ponko.craftfriend.CraftFriend;
import com.ponko.craftfriend.CraftFriendServer;
import com.ponko.craftfriend.account.AuthService;
import com.ponko.craftfriend.body.YsmBridge;
import com.ponko.craftfriend.config.CraftFriendConfig;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** 管理员控制接口：/wsm status、/wsm model。 */
public final class WaterCommand {

    private WaterCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("wsm")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("status").executes(WaterCommand::status))
                .then(CommandManager.literal("limbo")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(WaterCommand::limbo)))
                .then(CommandManager.literal("model")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .then(CommandManager.argument("model", StringArgumentType.greedyString())
                                        .executes(WaterCommand::model)))));
    }

    private static int status(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        boolean ysm = YsmBridge.isAvailable(source.getServer());
        source.sendFeedback(() -> Text.literal(
                "账号 " + CraftFriendServer.ACCOUNTS.size()
                        + "，登录门禁 " + (CraftFriendConfig.get().auth.enabled ? "开" : "关")
                        + "，YSM " + (ysm ? "可用" : "未安装")), false);
        return 1;
    }

    private static int limbo(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "player");
        ServerCommandSource source = context.getSource();
        var player = source.getServer().getPlayerManager().getPlayer(name);
        if (player == null) {
            source.sendError(Text.literal("玩家不在线：" + name));
            return 0;
        }
        AuthService.enterLimbo(player);
        source.sendFeedback(() -> Text.literal("已把 " + name + " 送入 limbo"), false);
        return 1;
    }

    private static int model(CommandContext<ServerCommandSource> context) {
        String player = StringArgumentType.getString(context, "player");
        String model = StringArgumentType.getString(context, "model");
        ServerCommandSource source = context.getSource();
        if (!YsmBridge.setModel(source.getServer(), player, model)) {
            source.sendError(Text.literal("YSM 绑定失败：" + player + " -> " + model));
            return 0;
        }
        CraftFriend.LOGGER.info("[WaterServerManager] 已把 {} 的模型设为 {}", player, model);
        source.sendFeedback(() -> Text.literal("已设置 " + player + " 的模型为 " + model), false);
        return 1;
    }
}
