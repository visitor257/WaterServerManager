package com.ponko.craftfriend.body;

import com.ponko.craftfriend.CraftFriend;
import net.minecraft.server.MinecraftServer;

/**
 * YSM（Yes Steve Model）桥：只通过指令交互，不依赖 mod 内部 API。
 */
public final class YsmBridge {

    private YsmBridge() {
    }

    public static boolean isAvailable(MinecraftServer server) {
        return server.getCommandManager().getDispatcher().getRoot().getChild("ysm") != null;
    }

    public static boolean setModel(MinecraftServer server, String playerName, String modelId) {
        // 模型 ID 可能带 "/"（如 wine_fox/01_taisho_maid），必须加引号才能过 YSM 的参数解析。
        String quoted = "\"" + modelId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        try {
            server.getCommandManager().executeWithPrefix(
                    server.getCommandSource(),
                    "ysm model set " + playerName + " " + quoted + " -");
            return true;
        } catch (RuntimeException e) {
            CraftFriend.LOGGER.warn("[WaterServerManager] YSM 绑定失败: {}", e.getMessage());
            return false;
        }
    }
}
