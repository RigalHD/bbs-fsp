package mchorse.bbs_fsp.mixin.client;

import mchorse.bbs_fsp.ui.graffiti.UIGraffitiEditor;
import mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * No free look while the graffiti editor is open. Free look hides the pointer and turns the
 * camera with every move of the mouse, and the editor needs the pointer to paint with: there
 * the camera looks around while the right button is held. The film editor asks for free look
 * on every frame, so it comes back on its own once the editor is closed.
 */
@Mixin(UIOrbitCamera.class)
public abstract class UIOrbitCameraMixin
{
    @ModifyVariable(method = "setFreeLook", at = @At("HEAD"), argsOnly = true)
    private boolean bbsFsp$noFreeLookInGraffiti(boolean freeLook)
    {
        return freeLook && !UIGraffitiEditor.isOpen();
    }
}
