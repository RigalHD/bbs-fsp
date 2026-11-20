package mchorse.bbs_fsp.mixin.client;

import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class CanvasMouseMixin
{
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onCursorPos", at = @At("HEAD"), require = 1)
    private void bbsFsp$drawOnMouseMove(long handle, double x, double y, CallbackInfo callbackInfo)
    {
        if (!CanvasViewportController.isDrawing())
        {
            return;
        }

        Window window = this.client.getWindow();

        if (handle != window.getHandle() || !(this.client.currentScreen instanceof UIScreen))
        {
            CanvasViewportController.finish();

            return;
        }

        CanvasViewportController.continueDrawing(
            x * window.getScaledWidth() / window.getWidth(),
            y * window.getScaledHeight() / window.getHeight()
        );
    }
}
