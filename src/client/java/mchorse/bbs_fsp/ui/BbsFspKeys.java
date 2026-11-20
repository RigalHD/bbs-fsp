package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.canvas.brush.CanvasBrushPattern;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushShape;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.Locale;

public final class BbsFspKeys
{
    public static final IKey CANVAS = L10n.lang("bbs_fsp.canvas.title");
    public static final IKey WIDTH = L10n.lang("bbs_fsp.canvas.width");
    public static final IKey HEIGHT = L10n.lang("bbs_fsp.canvas.height");
    public static final IKey OFFSET_X = L10n.lang("bbs_fsp.canvas.offset_x");
    public static final IKey OFFSET_Y = L10n.lang("bbs_fsp.canvas.offset_y");
    public static final IKey ROTATION = L10n.lang("bbs_fsp.canvas.rotation");
    public static final IKey GRID_OPACITY = L10n.lang("bbs_fsp.canvas.grid_opacity");
    public static final IKey FRAMES = L10n.lang("bbs_fsp.canvas.frames");
    public static final IKey PREVIOUS_FRAME = L10n.lang("bbs_fsp.canvas.previous_frame");
    public static final IKey COLOR = L10n.lang("bbs_fsp.canvas.color");
    public static final IKey SECONDARY_COLOR = L10n.lang("bbs_fsp.canvas.secondary_color");
    public static final IKey SWAP_COLORS = L10n.lang("bbs_fsp.canvas.swap_colors");
    public static final IKey EYEDROPPER = L10n.lang("bbs_fsp.canvas.eyedropper");
    public static final IKey OPACITY = L10n.lang("bbs_fsp.canvas.opacity");
    public static final IKey RECENT_COLORS = L10n.lang("bbs_fsp.canvas.recent_colors");
    public static final IKey LAYERS = L10n.lang("bbs_fsp.canvas.layers");
    public static final IKey DUPLICATE_LAYER = L10n.lang("bbs_fsp.canvas.duplicate_layer");

    public static final IKey BRUSH = L10n.lang("bbs_fsp.canvas.brush.title");
    public static final IKey BRUSH_PATTERN = L10n.lang("bbs_fsp.canvas.brush.pattern");
    public static final IKey BRUSH_SHAPE = L10n.lang("bbs_fsp.canvas.brush.shape");
    public static final IKey BRUSH_SIZE = L10n.lang("bbs_fsp.canvas.brush.size");
    public static final IKey BRUSH_SOFTNESS = L10n.lang("bbs_fsp.canvas.brush.softness");
    public static final IKey BRUSH_FLOW = L10n.lang("bbs_fsp.canvas.brush.flow");
    public static final IKey BRUSH_DENSITY = L10n.lang("bbs_fsp.canvas.brush.density");
    public static final IKey BRUSH_LENGTH = L10n.lang("bbs_fsp.canvas.brush.length");
    public static final IKey BRUSH_ERASER = L10n.lang("bbs_fsp.canvas.brush.eraser");
    public static final IKey BRUSH_RESET = L10n.lang("bbs_fsp.canvas.brush.reset");

    private static final IKey[] PATTERNS = keys(CanvasBrushPattern.values(), "bbs_fsp.canvas.brush.patterns.");
    private static final IKey[] SHAPES = keys(CanvasBrushShape.values(), "bbs_fsp.canvas.brush.shapes.");

    private BbsFspKeys()
    {}

    public static IKey pattern(CanvasBrushPattern pattern)
    {
        return PATTERNS[pattern.ordinal()];
    }

    public static IKey shape(CanvasBrushShape shape)
    {
        return SHAPES[shape.ordinal()];
    }

    private static IKey[] keys(Enum<?>[] values, String prefix)
    {
        IKey[] keys = new IKey[values.length];

        for (int i = 0; i < values.length; i++)
        {
            keys[i] = L10n.lang(prefix + values[i].name().toLowerCase(Locale.ROOT));
        }

        return keys;
    }
}
