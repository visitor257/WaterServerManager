package com.ponko.craftfriend.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版 {@code getPlayerListName()} 返回 null，Tab 列表会退回档案名。
 * 这里改成返回 custom name，未登录显示"未登录n"，登录后显示账户名。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class PlayerListNameMixin {

    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void waterservermanager$useCustomName(CallbackInfoReturnable<Text> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        Text custom = self.getCustomName();
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
