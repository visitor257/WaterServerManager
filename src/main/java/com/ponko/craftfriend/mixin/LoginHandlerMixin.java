package com.ponko.craftfriend.mixin;

import com.mojang.authlib.GameProfile;
import com.ponko.craftfriend.CraftFriend;
import com.ponko.craftfriend.account.PremiumVerifier;
import com.ponko.craftfriend.account.ProtectedList;
import com.ponko.craftfriend.account.UuidMode;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 登录阶段：
 * <ul>
 *   <li>随机/固定 UUID 切换；</li>
 *   <li>固定 + priority 时按正版优先规则处理同名顶号。</li>
 * </ul>
 */
@Mixin(ServerLoginNetworkHandler.class)
public abstract class LoginHandlerMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    @Final
    private ClientConnection connection;

    @Shadow
    private GameProfile profile;

    @Unique
    private UUID waterservermanager$sessionUuid;

    @Unique
    private UUID waterservermanager$clientUuid;

    @Inject(method = "onHello", at = @At("HEAD"))
    private void waterservermanager$captureClientUuid(LoginHelloC2SPacket packet, CallbackInfo ci) {
        this.waterservermanager$clientUuid = packet.profileId();
    }

    @Inject(method = "tickVerify", at = @At("HEAD"), cancellable = true)
    private void waterservermanager$priority(GameProfile profile, CallbackInfo ci) {
        if (!UuidMode.stable || profile == null) {
            return;
        }
        String name = profile.getName();
        ServerPlayerEntity existing = server.getPlayerManager().getPlayer(name);
        boolean newProtected = ProtectedList.isProtected(name);

        if (existing != null) {
            if (ProtectedList.isOnlineProtected(name)) {
                connection.disconnect(Text.literal("该玩家受保护，无法顶号"));
                CraftFriend.LOGGER.info("[Auth] 拒绝顶号：{}（受保护）", name);
                ci.cancel();
                return;
            }
            if (newProtected) {
                // 保护名单成员后到：允许他接管，原版会踢掉旧会话。
                ProtectedList.markOnline(name, true);
                CraftFriend.LOGGER.info("[Auth] 受保护玩家 {} 接管在线会话", name);
                return;
            }
            if (UuidMode.priority) {
                boolean newPremium = PremiumVerifier.isPremium(name, waterservermanager$clientUuid);
                if (PremiumVerifier.isOnlinePremium(name) && !newPremium) {
                    connection.disconnect(Text.literal("正版账号在线，离线客户端不能顶号"));
                    CraftFriend.LOGGER.info("[Auth] 拒绝离线顶号：{}（正版在线）", name);
                    ci.cancel();
                    return;
                }
                PremiumVerifier.markOnline(name, newPremium);
            }
            return;
        }

        ProtectedList.markOnline(name, newProtected);
        if (UuidMode.priority) {
            PremiumVerifier.markOnline(name,
                    PremiumVerifier.isPremium(name, waterservermanager$clientUuid));
        }
    }

    @ModifyVariable(method = "tickVerify", at = @At("HEAD"), argsOnly = true)
    private GameProfile waterservermanager$assignSessionUuid(GameProfile profile) {
        if (profile == null || UuidMode.stable) {
            return profile;
        }
        if (waterservermanager$sessionUuid == null) {
            waterservermanager$sessionUuid = UUID.randomUUID();
            CraftFriend.LOGGER.info("[Auth] {} 使用会话 UUID {}",
                    profile.getName(), waterservermanager$sessionUuid);
        }
        GameProfile assigned = new GameProfile(waterservermanager$sessionUuid, profile.getName());
        this.profile = assigned;
        return assigned;
    }
}
