package mchorse.bbs_fsp.mixin.client;

import mchorse.bbs_fsp.render.CanvasRuntimeOverlay;
import mchorse.bbs_mod.client.BBSRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BBSRendering.class)
public class BBSRenderingMixin
{
    @Inject(method = "onRenderBeforeScreen", at = @At("HEAD"))
    private static void bbsFsp$renderCanvas(CallbackInfo callbackInfo)
    {
        CanvasRuntimeOverlay.render();
    }
}
