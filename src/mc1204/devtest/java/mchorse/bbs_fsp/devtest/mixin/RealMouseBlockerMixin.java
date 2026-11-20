package mchorse.bbs_fsp.devtest.mixin;

import mchorse.bbs_fsp.devtest.CanvasDevInput;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Mouse.class, priority = 100)
public abstract class RealMouseBlockerMixin
{
    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void bbsFspDevtest$blockCursor(long window, double x, double y, CallbackInfo info)
    {
        if (!CanvasDevInput.isSynthetic())
        {
            info.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void bbsFspDevtest$blockButton(long window, int button, int action, int mods, CallbackInfo info)
    {
        if (!CanvasDevInput.isSynthetic())
        {
            info.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void bbsFspDevtest$blockScroll(long window, double horizontal, double vertical, CallbackInfo info)
    {
        if (!CanvasDevInput.isSynthetic())
        {
            info.cancel();
        }
    }
}
