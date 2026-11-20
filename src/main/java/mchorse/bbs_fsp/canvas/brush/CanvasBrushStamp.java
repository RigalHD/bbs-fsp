package mchorse.bbs_fsp.canvas.brush;

/**
 * The cells one dab of a brush covers, relative to the cell under the cursor, together with
 * how strongly each one is covered once softness is applied.
 */
public final class CanvasBrushStamp
{
    public final int width;
    public final int height;
    public final int count;
    public final int[] offsetX;
    public final int[] offsetY;
    public final float[] weight;

    private CanvasBrushStamp(int width, int height, int count, int[] offsetX, int[] offsetY, float[] weight)
    {
        this.width = width;
        this.height = height;
        this.count = count;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.weight = weight;
    }

    public static CanvasBrushStamp create(CanvasBrush brush, boolean soft)
    {
        CanvasBrushShape shape = brush.shape;
        int size = CanvasBrush.clampSize(brush.size);
        int width = shape.stampWidth(size);
        int height = shape.stampHeight(size);
        int[] offsetX = new int[width * height];
        int[] offsetY = new int[width * height];
        float[] weight = new float[width * height];
        float softness = soft ? brush.softness : 0F;
        int count = 0;

        for (int j = 0; j < height; j++)
        {
            for (int i = 0; i < width; i++)
            {
                float distance = shape.distance(i - (width - 1) / 2F, j - (height - 1) / 2F, size);

                if (distance > 1F)
                {
                    continue;
                }

                float coverage = falloff(distance, softness);

                if (coverage <= 0F)
                {
                    continue;
                }

                offsetX[count] = i - (width - 1) / 2;
                offsetY[count] = j - (height - 1) / 2;
                weight[count] = coverage;
                count += 1;
            }
        }

        if (count == 0)
        {
            offsetX[0] = 0;
            offsetY[0] = 0;
            weight[0] = 1F;
            count = 1;
        }

        return new CanvasBrushStamp(width, height, count, offsetX, offsetY, weight);
    }

    /**
     * Full coverage up to {@code 1 - softness} of the radius, then a smooth fade to nothing at
     * the edge of the shape.
     */
    static float falloff(float distance, float softness)
    {
        if (softness <= 0F)
        {
            return 1F;
        }

        float start = 1F - softness;

        if (distance <= start)
        {
            return 1F;
        }

        float t = Math.min(1F, (distance - start) / softness);

        return 1F - t * t * (3F - 2F * t);
    }

    public int minX()
    {
        return -(this.width - 1) / 2;
    }

    public int minY()
    {
        return -(this.height - 1) / 2;
    }
}
