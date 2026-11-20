package mchorse.bbs_fsp.mixin.client;

import mchorse.bbs_fsp.bbmodel.BBModelLoader;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.resources.Link;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A model folder with a Blockbench project in it is a model: it shows in the model list, and saving
 * the project in Blockbench reloads it (see {@link BBModelLoader}).
 */
@Mixin(value = ModelManager.class, remap = false)
public class ModelManagerMixin
{
    @Inject(method = "isRelodable", at = @At("HEAD"), cancellable = true)
    private void bbsFsp$blockbenchProject(Link link, CallbackInfoReturnable<Boolean> callback)
    {
        if (link.path.startsWith(ModelManager.MODELS_PREFIX) && BBModelLoader.isProject(link.path) && !link.path.contains("/animations/"))
        {
            callback.setReturnValue(true);
        }
    }
}
