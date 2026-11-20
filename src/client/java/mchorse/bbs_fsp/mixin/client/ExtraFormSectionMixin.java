package mchorse.bbs_fsp.mixin.client;

import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.sections.ExtraFormSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the graffiti into the "Extra" category, next to the video, the mobs and the rest. */
@Mixin(ExtraFormSection.class)
public abstract class ExtraFormSectionMixin
{
    @Shadow
    private FormCategory extra;

    @Inject(method = "initiate", at = @At("TAIL"))
    private void bbsFsp$addGraffiti(CallbackInfo callbackInfo)
    {
        this.extra.addForm(new GraffitiForm());
    }
}
