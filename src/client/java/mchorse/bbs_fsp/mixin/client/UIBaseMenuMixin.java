package mchorse.bbs_fsp.mixin.client;

import mchorse.bbs_fsp.ui.CanvasEyedropper;
import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiEditor;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UIBaseMenu.class)
public abstract class UIBaseMenuMixin
{
    @Shadow
    public UIContext context;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbsFsp$pickScreenColor(int mouseX, int mouseY, int mouseButton, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (CanvasEyedropper.mouseClicked(mouseX, mouseY, mouseButton))
        {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void bbsFsp$swallowPickRelease(int mouseX, int mouseY, int mouseButton, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        UIGraffitiEditor.mouseReleased();

        if (mouseButton == 0 && CanvasViewportController.isDrawing())
        {
            CanvasViewportController.finish();
            callbackInfo.setReturnValue(true);

            return;
        }

        if (CanvasEyedropper.mouseReleased())
        {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "handleKey", at = @At("HEAD"), cancellable = true)
    private void bbsFsp$cancelScreenPick(int key, int scanCode, int action, int mods, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (CanvasEyedropper.keyPressed(key, action))
        {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "renderMenu", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/UIContext;applyCursor()V"))
    private void bbsFsp$renderScreenPick(UIRenderingContext renderingContext, int mouseX, int mouseY, CallbackInfo callbackInfo)
    {
        CanvasEyedropper.render(this.context);
    }
}
