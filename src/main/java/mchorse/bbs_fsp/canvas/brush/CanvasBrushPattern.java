package mchorse.bbs_fsp.canvas.brush;

public enum CanvasBrushPattern
{
    PENCIL(false, false, false, false),
    MARKER(true, true, false, false),
    GRADIENT(true, true, false, true),
    DITHER(false, true, true, false),
    SPRAY(true, true, true, false);

    public final boolean softness;
    public final boolean flow;
    public final boolean density;
    public final boolean gradient;

    CanvasBrushPattern(boolean softness, boolean flow, boolean density, boolean gradient)
    {
        this.softness = softness;
        this.flow = flow;
        this.density = density;
        this.gradient = gradient;
    }

    public static CanvasBrushPattern fromId(String id)
    {
        for (CanvasBrushPattern pattern : values())
        {
            if (pattern.name().equalsIgnoreCase(id))
            {
                return pattern;
            }
        }

        return PENCIL;
    }
}
