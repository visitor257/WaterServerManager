package com.ponko.craftfriend;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CraftFriend 的公共入口。
 *
 * <p>真正的服务器逻辑在 {@link CraftFriendServer}，这里只放两端都会用到的东西。</p>
 */
public final class CraftFriend implements ModInitializer {

    public static final String MOD_ID = "waterservermanager";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[WaterServerManager] 模组已加载，等待服务器启动");
    }
}
