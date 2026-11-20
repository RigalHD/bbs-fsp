package mchorse.bbs_fsp.mixin;

import mchorse.bbs_fsp.graffiti.GraffitiCycle;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * After BBS lays a replay's tracks over its form, the graffiti's pixel art shows the frame of
 * its animation cycle instead: see {@link GraffitiCycle}. Every playback (the film editor,
 * the world, export) goes through this method.
 */
@Mixin(value = FormProperties.class, remap = false)
public class FormPropertiesMixin
{
    @Shadow
    @Final
    public Map<TrackId, KeyframeChannel> tracks;

    @Inject(method = "apply(Lmchorse/bbs_mod/film/replays/tracks/TrackContext;FF)V", at = @At("RETURN"))
    private void bbsFsp$cycleGraffiti(TrackContext context, float tick, float blend, CallbackInfo callback)
    {
        if (context.root() == null)
        {
            return;
        }

        for (Map.Entry<TrackId, KeyframeChannel> entry : this.tracks.entrySet())
        {
            GraffitiCycle.apply(context.root(), entry.getKey().toKey(), entry.getValue(), tick);
        }
    }
}
