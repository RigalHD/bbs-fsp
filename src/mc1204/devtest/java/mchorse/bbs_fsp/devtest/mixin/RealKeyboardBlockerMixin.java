package mchorse.bbs_fsp.devtest.mixin;

import mchorse.bbs_fsp.devtest.CanvasDevInput;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Keyboard.class, priority = 100)
public abstract class RealKeyboardBlockerMixin
{
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void bbsFspDevtest$blockKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        if (!CanvasDevInput.isSynthetic())
        {
            info.cancel();
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void bbsFspDevtest$blockChar(long window, int codePoint, int modifiers, CallbackInfo info)
    {
        if (!CanvasDevInput.isSynthetic())
        {
            info.cancel();
        }
    }
}
