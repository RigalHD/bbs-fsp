package mchorse.bbs_fsp.mixin;

import mchorse.bbs_fsp.IntArrayText;
import mchorse.bbs_mod.data.DataParser;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntArrayType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets copied pixels be pasted: see {@link IntArrayText}. Every nested value goes through
 * {@code parse}, so catching the int array here covers it wherever it sits in the data.
 */
@Mixin(value = DataParser.class, remap = false)
public class DataParserMixin
{
    @Inject(method = "parse", at = @At("HEAD"), cancellable = true)
    private static void bbsFsp$parseIntArray(String string, CallbackInfoReturnable<BaseType> callback)
    {
        if (string != null)
        {
            IntArrayType array = IntArrayText.parse(string.trim());

            if (array != null)
            {
                callback.setReturnValue(array);
            }
        }
    }
}
