package mchorse.bbs_fsp.canvas.brush;

public enum CanvasBrushShape
{
    SQUARE,
    CIRCLE,
    DIAMOND,
    HORIZONTAL,
    VERTICAL;

    public int stampWidth(int size)
    {
        return this == VERTICAL ? 1 : size;
    }

    public int stampHeight(int size)
    {
        return this == HORIZONTAL ? 1 : size;
    }

    /**
     * Distance of a stamp cell from the stamp centre, normalised so that 1 is the edge of the
     * shape. {@code x} and {@code y} are measured from the centre of the stamp in cells, so even
     * sizes sit between cells. Returns a value above 1 for cells outside the shape.
     */
    public float distance(float x, float y, int size)
    {
        float half = size / 2F;
        float radius = Math.max(0.25F, half - 0.25F);

        switch (this)
        {
            case CIRCLE:
                float length = (float) Math.sqrt(x * x + y * y);

                return length <= radius ? length / half : 2F;
            case DIAMOND:
                float manhattan = Math.abs(x) + Math.abs(y);

                return manhattan <= radius ? manhattan / half : 2F;
            case HORIZONTAL:
                return Math.abs(x) / half;
            case VERTICAL:
                return Math.abs(y) / half;
            default:
                return Math.max(Math.abs(x), Math.abs(y)) / half;
        }
    }

    public static CanvasBrushShape fromId(String id)
    {
        for (CanvasBrushShape shape : values())
        {
            if (shape.name().equalsIgnoreCase(id))
            {
                return shape;
            }
        }

        return SQUARE;
    }
}
