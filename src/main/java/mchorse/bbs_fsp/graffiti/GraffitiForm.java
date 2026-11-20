package mchorse.bbs_fsp.graffiti;

import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Function;

/**
 * A drawn picture that lives in the scene like any other form: moved, scaled, turned and parented
 * through the usual transform, hidden behind models or hiding them by where it stands. What it
 * shows comes from the pixel art track, a drawn frame per keyframe; the quad itself is BBS's own
 * billboard, so lighting, translucency and shader packs treat it like one.
 */
public class GraffitiForm extends BillboardForm
{
    public static final Icon ICON = Icons.SPRAY;
    public static final String PIXEL_ART = "pixel_art";

    /** Readable track and form names, provided by the client (the common side has no languages). */
    public static Function<String, String> labels = (key) -> null;

    public final ValueGraffitiPicture pixelArt = new ValueGraffitiPicture(PIXEL_ART,
        new GraffitiPicture(GraffitiPicture.DEFAULT_SIZE, GraffitiPicture.DEFAULT_SIZE));
    public final ValueFloat blur = new ValueFloat("blur", 0F, 0F, 32F);
    public final ValueFloat glow = new ValueFloat("glow", 1F, 0F, 8F);
    public final ValueFloat glowRadius = new ValueFloat("glow_radius", 2F, 0F, 32F);
    public final ValueFloat lightIntensity = new ValueFloat("light_intensity", 2F, 0F, 64F);
    public final ValueFloat lightRadius = new ValueFloat("light_radius", 8F, 0F, 64F);
    public final ValueFloat gridOpacity = new ValueFloat("grid_opacity", 0.5F, 0F, 1F);
    public final ValueFloat onionSkin = new ValueFloat("onion_skin", 0.3F, 0F, 1F);
    /** Drawn at full brightness without shading, so lamps and other lights around leave no glare on it. */
    public final ValueBoolean unlit = new ValueBoolean("unlit", false);
    /** How long the animation lasts in ticks, from its first frame; 0 runs until its last frame. See {@link GraffitiCycle}. */
    public final ValueInt cycleLength = new ValueInt("cycle_length", 0, 0, 100000);
    /** What happens after the animation: {@link GraffitiCycle#LOOP}, {@link GraffitiCycle#HOLD} or {@link GraffitiCycle#ONCE}. */
    public final ValueInt cycleMode = new ValueInt("cycle_mode", GraffitiCycle.HOLD, GraffitiCycle.LOOP, GraffitiCycle.ONCE);

    public GraffitiForm()
    {
        super();

        /* The picture comes from the pixel art track, the texture and the crop of the quad
         * would only fight it, and a shifted UV would no longer match the painted cells. */
        this.texture.invisible();
        this.crop.invisible();
        this.offsetX.invisible();
        this.offsetY.invisible();
        this.rotation.invisible();

        /* Editor settings, not something to animate. */
        this.gridOpacity.invisible();
        this.onionSkin.invisible();
        this.unlit.invisible();
        this.cycleLength.invisible();
        this.cycleMode.invisible();

        this.add(this.pixelArt);
        this.add(this.blur);
        this.add(this.glow);
        this.add(this.glowRadius);
        this.add(this.lightIntensity);
        this.add(this.lightRadius);
        this.add(this.gridOpacity);
        this.add(this.onionSkin);
        this.add(this.unlit);
        this.add(this.cycleLength);
        this.add(this.cycleMode);
    }

    /**
     * A setting as it is used: a track can animate it past the range of its field, and a blur,
     * halo or light that wide would stall every frame it is drawn.
     */
    public static float clamped(ValueFloat value)
    {
        return Math.max(value.getMin(), Math.min(value.getMax(), value.get()));
    }

    @Override
    public String getDefaultDisplayName()
    {
        String label = labels.apply("form");

        return label == null ? "graffiti" : label;
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

    @Override
    public String getTrackName(String property)
    {
        String name = super.getTrackName(property);
        int slash = property.lastIndexOf('/');
        String last = slash == -1 ? property : property.substring(slash + 1);
        String label = labels.apply(last);

        if (label == null || !name.endsWith(last))
        {
            return name;
        }

        return name.substring(0, name.length() - last.length()) + label;
    }
}
