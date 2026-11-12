package com.ponko.craftfriend.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 加入/退出、聊天、计分板等都用 getDisplayName()；让它优先返回 custom name，
 * 这样提示里就是"未登录n"或账户名，而不是档案名。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void waterservermanager$useCustomName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        Text custom = self.getCustomName();
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
