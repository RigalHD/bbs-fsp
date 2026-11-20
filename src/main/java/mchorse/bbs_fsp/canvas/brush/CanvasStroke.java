package mchorse.bbs_fsp.canvas.brush;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasRasterTools;

import java.util.SplittableRandom;

/**
 * One continuous stroke of the brush over a layer.
 *
 * <p>Every pixel the stroke reaches remembers the strongest coverage it got and is recomposed
 * from the layer as it was before the stroke began. That is what keeps a translucent marker
 * even along its whole length instead of darkening wherever two dabs overlap, and what lets a
 * soft edge never eat into the solid middle of the same stroke.</p>
 */
public final class CanvasStroke
{
    private static final int[] BAYER = {
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    };
    private static final float SPRAY_RATE = 0.35F;
    private static final float DIAGONAL = (float) Math.sqrt(2D);

    private final CanvasLayerData layer;
    private final int width;
    private final int height;
    private final CanvasBrushPattern pattern;
    private final CanvasBrush brush;
    private final CanvasBrushStamp stamp;
    private final boolean eraser;
    private final int primary;
    private final int secondary;
    private final float opacity;
    private final int[] snapshot;
    private final float[] coverage;
    private final int[] colors;
    private final SplittableRandom random;
    private final int spacing;

    private boolean started;
    private int lastX;
    private int lastY;
    private float distance;
    private int travelled;

    public CanvasStroke(CanvasLayerData layer, int width, int height, CanvasBrushPattern pattern, CanvasBrush brush,
        boolean eraser, int primary, int secondary, float opacity, long seed)
    {
        this.layer = layer;
        this.width = width;
        this.height = height;
        this.pattern = pattern;
        this.brush = brush.copy();
        this.stamp = CanvasBrushStamp.create(this.brush, pattern.softness);
        this.eraser = eraser;
        this.primary = primary;
        this.secondary = secondary;
        this.opacity = Math.max(0F, Math.min(1F, opacity)) * (pattern.flow ? this.brush.flow : 1F);
        this.snapshot = layer.pixels.clone();
        this.coverage = new float[layer.pixels.length];
        this.colors = pattern.gradient ? new int[layer.pixels.length] : null;
        this.random = new SplittableRandom(seed);
        this.spacing = pattern == CanvasBrushPattern.SPRAY ? Math.max(1, this.brush.size / 4) : 1;
    }

    public CanvasBrushStamp getStamp()
    {
        return this.stamp;
    }

    /**
     * Continue the stroke to the given cell. The path from the previous cell is walked one cell
     * at a time, so a fast mouse leaves no gaps.
     */
    public void moveTo(int x, int y)
    {
        if (!this.started)
        {
            this.started = true;
            this.lastX = x;
            this.lastY = y;
            this.dab(x, y);

            return;
        }

        if (x == this.lastX && y == this.lastY)
        {
            return;
        }

        int currentX = this.lastX;
        int currentY = this.lastY;
        int dx = Math.abs(x - currentX);
        int sx = currentX < x ? 1 : -1;
        int dy = -Math.abs(y - currentY);
        int sy = currentY < y ? 1 : -1;
        int error = dx + dy;

        while (currentX != x || currentY != y)
        {
            int error2 = error * 2;
            boolean stepX = false;
            boolean stepY = false;

            if (error2 >= dy)
            {
                error += dy;
                currentX += sx;
                stepX = true;
            }

            if (error2 <= dx)
            {
                error += dx;
                currentY += sy;
                stepY = true;
            }

            this.distance += stepX && stepY ? DIAGONAL : 1F;
            this.travelled += 1;

            if (this.travelled % this.spacing == 0 || (currentX == x && currentY == y))
            {
                this.dab(currentX, currentY);
            }
        }

        this.lastX = x;
        this.lastY = y;
    }

    private void dab(int centerX, int centerY)
    {
        int color = this.pattern.gradient ? this.gradientColor() : this.primary;
        float density = this.brush.density;

        for (int i = 0; i < this.stamp.count; i++)
        {
            int x = centerX + this.stamp.offsetX[i];
            int y = centerY + this.stamp.offsetY[i];

            if (x < 0 || y < 0 || x >= this.width || y >= this.height)
            {
                continue;
            }

            float weight = this.stamp.weight[i];
            float amount;

            switch (this.pattern)
            {
                case PENCIL:
                    amount = 1F;
                    break;
                case DITHER:
                    amount = BAYER[(y & 3) * 4 + (x & 3)] < Math.round(density * 16F) ? 1F : 0F;
                    break;
                case SPRAY:
                    amount = this.random.nextFloat() < density * weight * SPRAY_RATE ? 1F : 0F;
                    break;
                default:
                    amount = weight;
            }

            if (amount <= 0F)
            {
                continue;
            }

            int index = y * this.width + x;

            if (this.colors != null && amount >= this.coverage[index])
            {
                this.colors[index] = color;
            }

            if (amount > this.coverage[index])
            {
                this.coverage[index] = amount;
            }

            this.resolve(index);
        }

        this.layer.touch();
    }

    private void resolve(int index)
    {
        int base = this.snapshot[index];
        float amount = this.coverage[index] * this.opacity;

        if (this.eraser)
        {
            int alpha = Math.round(((base >>> 24) & 255) * (1F - amount));

            this.layer.pixels[index] = alpha <= 0 ? 0 : (base & 0x00FFFFFF) | (alpha << 24);

            return;
        }

        int color = this.colors != null ? this.colors[index] : this.primary;
        int alpha = Math.round(((color >>> 24) & 255) * amount);

        this.layer.pixels[index] = CanvasRasterTools.sourceOver(base, (color & 0x00FFFFFF) | (alpha << 24));
    }

    private int gradientColor()
    {
        float phase = this.distance / Math.max(1, this.brush.length);
        float t = phase % 2F;

        if (t > 1F)
        {
            t = 2F - t;
        }

        return CanvasRasterTools.interpolatePremultiplied(this.primary, this.secondary, t);
    }
}
