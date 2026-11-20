package mchorse.bbs_fsp.mixin.client;

import mchorse.bbs_fsp.render.CanvasPreviewOverlay;
import mchorse.bbs_fsp.render.GraffitiPreviewOverlay;
import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UIFilmPreview.class)
public abstract class UIFilmPreviewMixin
{
    @Shadow
    private UIFilmPanel panel;

    @Unique
    private UIIcon bbsFsp$graffitiButton;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbsFsp$addGraffitiButton(UIFilmPanel filmPanel, CallbackInfo callbackInfo)
    {
        UIFilmPreview preview = (UIFilmPreview) (Object) this;

        this.bbsFsp$graffitiButton = GraffitiPreviewOverlay.createButton(preview, filmPanel);
        preview.icons.add(this.bbsFsp$graffitiButton);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/elements/UIElement;render(Lmchorse/bbs_mod/ui/framework/UIContext;)V"))
    private void bbsFsp$renderCanvasOverlay(UIContext context, CallbackInfo callbackInfo)
    {
        CanvasPreviewOverlay.render((UIFilmPreview) (Object) this, this.panel, context);
        GraffitiPreviewOverlay.render((UIFilmPreview) (Object) this, this.panel, this.bbsFsp$graffitiButton, context);
    }

    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbsFsp$beginCanvasStroke(UIContext context, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (context.mouseButton != 0 || this.panel == null || this.panel.cameraEditor == null)
        {
            return;
        }

        if (this.panel.cameraEditor.getClip() instanceof CanvasClip clip)
        {
            int localTick = Math.max(0, this.panel.getCursor() - clip.tick.get());

            if (CanvasViewportController.begin(clip, localTick, ((UIFilmPreview) (Object) this).getViewport(), context.mouseX, context.mouseY))
            {
                callbackInfo.setReturnValue(true);
            }
        }
    }

    @Inject(method = "subMouseReleased", at = @At("HEAD"), cancellable = true)
    private void bbsFsp$finishCanvasStroke(UIContext context, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (CanvasViewportController.isDrawing())
        {
            CanvasViewportController.finish();
            callbackInfo.setReturnValue(true);
        }
    }
}
