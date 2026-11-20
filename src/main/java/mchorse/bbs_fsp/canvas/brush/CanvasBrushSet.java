package mchorse.bbs_fsp.canvas.brush;

import mchorse.bbs_mod.data.types.MapType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the painter has picked: the active pattern with the settings of every pattern,
 * eraser mode and the two colours. The revision moves on every change so cached stamps and
 * previews know when to rebuild.
 */
public final class CanvasBrushSet
{
    public static final int DEFAULT_PRIMARY = 0xFFFFFFFF;
    public static final int DEFAULT_SECONDARY = 0xFF000000;

    private final Map<CanvasBrushPattern, CanvasBrush> brushes = new EnumMap<>(CanvasBrushPattern.class);
    private CanvasBrushPattern pattern = CanvasBrushPattern.PENCIL;
    private boolean eraser;
    private int primary = DEFAULT_PRIMARY;
    private int secondary = DEFAULT_SECONDARY;
    private long revision;

    public CanvasBrushSet()
    {
        for (CanvasBrushPattern pattern : CanvasBrushPattern.values())
        {
            this.brushes.put(pattern, CanvasBrush.defaults(pattern));
        }
    }

    public long getRevision()
    {
        return this.revision;
    }

    public void touch()
    {
        this.revision += 1;
    }

    public CanvasBrushPattern getPattern()
    {
        return this.pattern;
    }

    public void setPattern(CanvasBrushPattern pattern)
    {
        if (pattern != null && pattern != this.pattern)
        {
            this.pattern = pattern;
            this.touch();
        }
    }

    public CanvasBrush getBrush()
    {
        return this.brushes.get(this.pattern);
    }

    public CanvasBrush getBrush(CanvasBrushPattern pattern)
    {
        return this.brushes.get(pattern);
    }

    public boolean isEraser()
    {
        return this.eraser;
    }

    public void setEraser(boolean eraser)
    {
        if (this.eraser != eraser)
        {
            this.eraser = eraser;
            this.touch();
        }
    }

    public int getPrimary()
    {
        return this.primary;
    }

    public void setPrimary(int color)
    {
        if (this.primary != color)
        {
            this.primary = color;
            this.touch();
        }
    }

    public int getSecondary()
    {
        return this.secondary;
    }

    public void setSecondary(int color)
    {
        if (this.secondary != color)
        {
            this.secondary = color;
            this.touch();
        }
    }

    public void swapColors()
    {
        int primary = this.primary;

        this.primary = this.secondary;
        this.secondary = primary;
        this.touch();
    }

    public void resetBrush()
    {
        this.getBrush().copyFrom(CanvasBrush.defaults(this.pattern));
        this.touch();
    }

    public MapType toData()
    {
        MapType data = new MapType(false);
        MapType brushes = new MapType(false);

        for (Map.Entry<CanvasBrushPattern, CanvasBrush> entry : this.brushes.entrySet())
        {
            brushes.put(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue().toData());
        }

        data.putString("pattern", this.pattern.name());
        data.putBool("eraser", this.eraser);
        data.putInt("primary", this.primary);
        data.putInt("secondary", this.secondary);
        data.put("brushes", brushes);

        return data;
    }

    public void fromData(MapType data)
    {
        this.pattern = CanvasBrushPattern.fromId(data.getString("pattern", this.pattern.name()));
        this.eraser = data.getBool("eraser", false);
        this.primary = data.getInt("primary", this.primary);
        this.secondary = data.getInt("secondary", this.secondary);

        MapType brushes = data.getMap("brushes");

        for (CanvasBrushPattern pattern : CanvasBrushPattern.values())
        {
            String key = pattern.name().toLowerCase(Locale.ROOT);

            if (brushes.has(key) && brushes.get(key) instanceof MapType brush)
            {
                this.brushes.get(pattern).fromData(brush);
            }
        }

        this.touch();
    }
}
