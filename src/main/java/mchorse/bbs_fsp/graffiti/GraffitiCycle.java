package mchorse.bbs_fsp.graffiti;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The bounds of a graffiti's animation: its pixel art frames make a cycle that starts at the
 * first frame and lasts {@link GraffitiForm#cycleLength} ticks (or, at 0, until the last frame
 * has shown as long as the one before it). Past the end the cycle starts again, holds its last
 * frame, or ends and the graffiti shows nothing.
 */
public final class GraffitiCycle
{
    public static final int LOOP = 0;
    public static final int HOLD = 1;
    public static final int ONCE = 2;

    /** What an ended graffiti shows. Never painted on. */
    public static final GraffitiPicture ENDED = new GraffitiPicture(1, 1);

    /**
     * While this says so, every graffiti shows the frame its track has at the tick, so the frame
     * being painted is the one in the scene. Set by the client to "the pixel art editor is open".
     */
    public static BooleanSupplier paused = () -> false;

    /** A cycle ends just short of its length, where the next one's first frame would already be. */
    private static final float END = 0.001F;

    private GraffitiCycle()
    {}

    /**
     * The tick of the pixel art track to show at the given tick, or NaN once a cycle played
     * once has ended. Before the first frame and without frames the tick stays as it is.
     */
    public static float remap(int mode, int length, List<? extends Keyframe<?>> frames, float tick)
    {
        if (frames.isEmpty())
        {
            return tick;
        }

        float first = frames.get(0).getTick();
        float cycle = length(length, frames);
        float time = tick - first;

        if (cycle <= 0F || time < 0F)
        {
            return tick;
        }

        if (mode == LOOP)
        {
            return first + time % cycle;
        }

        if (time < cycle)
        {
            return tick;
        }

        return mode == ONCE ? Float.NaN : first + cycle - END;
    }

    /** How long the cycle lasts in ticks with the given length setting; 0 when it has no end. */
    public static float length(int length, List<? extends Keyframe<?>> frames)
    {
        return length > 0 ? length : autoLength(frames);
    }

    /** From the first frame until the last one has shown as long as the one before it. */
    private static float autoLength(List<? extends Keyframe<?>> frames)
    {
        int count = frames.size();

        if (count < 2)
        {
            return 0F;
        }

        float last = frames.get(count - 1).getTick();

        return last - frames.get(0).getTick() + (last - frames.get(count - 2).getTick());
    }

    /**
     * Shows the frame of the cycle on the graffiti a pixel art track drives, right after BBS
     * laid the track over the form at the given tick.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void apply(Form root, String key, KeyframeChannel channel, float tick)
    {
        if (root == null || paused.getAsBoolean() || channel.getFactory() != GraffitiKeyframeFactory.INSTANCE || channel.isEmpty())
        {
            return;
        }

        BaseValueBasic property = FormUtils.getProperty(root, key);

        if (!(property instanceof ValueGraffitiPicture) || !(FormUtils.getForm(property) instanceof GraffitiForm form))
        {
            return;
        }

        float shown = remap(form.cycleMode.get(), form.cycleLength.get(), channel.getKeyframes(), tick);

        if (Float.isNaN(shown))
        {
            property.setRuntimeValue(ENDED);
        }
        else if (shown != tick)
        {
            property.setRuntimeValue(channel.interpolate(shown));
        }
    }
}
