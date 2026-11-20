package mchorse.bbs_fsp.devtest.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The checks own the window: switching to another app must not change what they see. */
@Mixin(MinecraftClient.class)
public abstract class TestFocusMixin
{
    @Inject(method = "isWindowFocused", at = @At("HEAD"), cancellable = true)
    private void bbsFspDevtest$alwaysFocused(CallbackInfoReturnable<Boolean> info)
    {
        info.setReturnValue(true);
    }
}
