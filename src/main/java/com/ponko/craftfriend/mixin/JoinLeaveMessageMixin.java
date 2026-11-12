package com.ponko.craftfriend.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 隐藏“加入了游戏 / 退出了游戏”广播。 */
@Mixin(PlayerManager.class)
public abstract class JoinLeaveMessageMixin {

    @Inject(method = "broadcast(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
    private void waterservermanager$hideJoinLeave(Text message, boolean overlay, CallbackInfo ci) {
        if (message.getContent() instanceof TranslatableTextContent translatable) {
            String key = translatable.getKey();
            if ("multiplayer.player.joined".equals(key)
                    || "multiplayer.player.joined.renamed".equals(key)
                    || "multiplayer.player.left".equals(key)) {
                ci.cancel();
            }
        }
    }
}
