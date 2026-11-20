package mchorse.bbs_fsp.devtest.mixin;

import mchorse.bbs_fsp.devtest.CanvasDevInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Held keys as the checks hold them, never as the real keyboard does. */
@Mixin(InputUtil.class)
public abstract class TestKeysMixin
{
    @Inject(method = "isKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void bbsFspDevtest$heldKeys(long window, int key, CallbackInfoReturnable<Boolean> info)
    {
        info.setReturnValue(CanvasDevInput.isAltHeld() && (key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT));
    }
}
