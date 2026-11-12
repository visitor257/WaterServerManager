package com.ponko.craftfriend;

import com.ponko.craftfriend.account.AccountCommand;
import com.ponko.craftfriend.account.AccountStore;
import com.ponko.craftfriend.account.AuthService;
import com.ponko.craftfriend.account.PlayerDataService;
import com.ponko.craftfriend.command.WaterCommand;
import com.ponko.craftfriend.skin.SkinService;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * 服务器侧入口：内部账号、正版信息还原、YSM 绑定。
 */
public final class CraftFriendServer implements DedicatedServerModInitializer {

    /** 内部账号。 */
    public static final AccountStore ACCOUNTS = new AccountStore();

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AccountCommand.register(dispatcher);
            WaterCommand.register(dispatcher);
        });

        ACCOUNTS.load();
        com.ponko.craftfriend.account.UuidMode.stable =
                com.ponko.craftfriend.config.CraftFriendConfig.get().auth.stableUuid;
        com.ponko.craftfriend.account.UuidMode.priority =
                com.ponko.craftfriend.config.CraftFriendConfig.get().auth.priority;
        AuthService.register();
        PlayerDataService.register();
        SkinService.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                CraftFriend.LOGGER.info("[WaterServerManager] 服务器已启动（{} 个内部账号）",
                        ACCOUNTS.size()));
    }
}
