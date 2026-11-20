package mchorse.bbs_fsp.canvas.brush;

import mchorse.bbs_mod.data.types.MapType;

/**
 * Settings of one brush pattern. Every pattern keeps its own copy, so switching from a thin
 * pencil to a wide soft marker and back restores the size and shape each one was left with.
 */
public final class CanvasBrush
{
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 64;
    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 512;

    public int size;
    public CanvasBrushShape shape;
    public float softness;
    public float flow;
    public float density;
    public int length;

    public CanvasBrush(int size, CanvasBrushShape shape, float softness, float flow, float density, int length)
    {
        this.size = clampSize(size);
        this.shape = shape == null ? CanvasBrushShape.SQUARE : shape;
        this.softness = clampUnit(softness, 0F);
        this.flow = clampUnit(flow, 1F);
        this.density = clampUnit(density, 0.5F);
        this.length = clampLength(length);
    }

    public static CanvasBrush defaults(CanvasBrushPattern pattern)
    {
        switch (pattern)
        {
            case MARKER:
                return new CanvasBrush(6, CanvasBrushShape.CIRCLE, 0.45F, 0.6F, 0.5F, 24);
            case GRADIENT:
                return new CanvasBrush(4, CanvasBrushShape.CIRCLE, 0.2F, 1F, 0.5F, 24);
            case DITHER:
                return new CanvasBrush(6, CanvasBrushShape.SQUARE, 0F, 1F, 0.5F, 24);
            case SPRAY:
                return new CanvasBrush(12, CanvasBrushShape.CIRCLE, 0.5F, 1F, 0.3F, 24);
            default:
                return new CanvasBrush(1, CanvasBrushShape.SQUARE, 0F, 1F, 0.5F, 24);
        }
    }

    public CanvasBrush copy()
    {
        return new CanvasBrush(this.size, this.shape, this.softness, this.flow, this.density, this.length);
    }

    public void copyFrom(CanvasBrush brush)
    {
        this.size = brush.size;
        this.shape = brush.shape;
        this.softness = brush.softness;
        this.flow = brush.flow;
        this.density = brush.density;
        this.length = brush.length;
    }

    public MapType toData()
    {
        MapType data = new MapType(false);

        data.putInt("size", this.size);
        data.putString("shape", this.shape.name());
        data.putFloat("softness", this.softness);
        data.putFloat("flow", this.flow);
        data.putFloat("density", this.density);
        data.putInt("length", this.length);

        return data;
    }

    public void fromData(MapType data)
    {
        this.size = clampSize(data.getInt("size", this.size));
        this.shape = data.has("shape") ? CanvasBrushShape.fromId(data.getString("shape")) : this.shape;
        this.softness = clampUnit(data.getFloat("softness", this.softness), this.softness);
        this.flow = clampUnit(data.getFloat("flow", this.flow), this.flow);
        this.density = clampUnit(data.getFloat("density", this.density), this.density);
        this.length = clampLength(data.getInt("length", this.length));
    }

    @Override
    public boolean equals(Object object)
    {
        return object instanceof CanvasBrush brush
            && this.size == brush.size
            && this.shape == brush.shape
            && Float.compare(this.softness, brush.softness) == 0
            && Float.compare(this.flow, brush.flow) == 0
            && Float.compare(this.density, brush.density) == 0
            && this.length == brush.length;
    }

    @Override
    public int hashCode()
    {
        int hash = this.size;

        hash = hash * 31 + this.shape.ordinal();
        hash = hash * 31 + Float.floatToIntBits(this.softness);
        hash = hash * 31 + Float.floatToIntBits(this.flow);
        hash = hash * 31 + Float.floatToIntBits(this.density);

        return hash * 31 + this.length;
    }

    public static int clampSize(int size)
    {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    public static int clampLength(int length)
    {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
    }

    private static float clampUnit(float value, float fallback)
    {
        return Float.isFinite(value) ? Math.max(0F, Math.min(1F, value)) : fallback;
    }
}
