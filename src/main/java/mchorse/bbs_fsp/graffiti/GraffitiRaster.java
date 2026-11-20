package mchorse.bbs_fsp.graffiti;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasRasterTools;
import mchorse.bbs_fsp.canvas.CanvasRenderBand;

import java.util.ArrayList;
import java.util.List;

/**
 * Pixel work of the graffiti that the canvas tools don't do already: compositing a frame into
 * the plain and the glowing image, blurring them, the halo around glowing paint, the transforms
 * of the editor and the light glowing paint casts.
 */
public final class GraffitiRaster
{
    /** Cells per side of the grid glowing paint is gathered in, one light per lit cell. */
    public static final int LIGHT_GRID = 2;

    private GraffitiRaster()
    {}

    public record Light(float u, float v, float red, float green, float blue, float weight)
    {}

    public static int over(int destination, int source, float opacity)
    {
        int alpha = Math.round(((source >>> 24) & 255) * Math.max(0F, Math.min(1F, opacity)));

        if (alpha <= 0)
        {
            return destination;
        }

        return CanvasRasterTools.sourceOver(destination, (source & 0x00FFFFFF) | (alpha << 24));
    }

    /**
     * The visible layers composited bottom to top: only the glowing ones, or only the plain
     * ones. Returns null when none of them has a single pixel to show.
     */
    public static int[] composite(GraffitiPicture picture, boolean glow)
    {
        return composite(picture, glow ? Boolean.TRUE : Boolean.FALSE);
    }

    /** Every visible layer, plain and glowing, the way the editor shows the frame; null when nothing shows. */
    public static int[] compositeAll(GraffitiPicture picture)
    {
        return composite(picture, null);
    }

    private static int[] composite(GraffitiPicture picture, Boolean glow)
    {
        int[] result = null;

        for (CanvasLayerData layer : picture.layers)
        {
            if (!layer.visible || glow != null && layer.glow != glow || layer.opacity <= 0F || layer.pixels.length != picture.pixelCount())
            {
                continue;
            }

            for (int i = 0; i < layer.pixels.length; i++)
            {
                int pixel = layer.pixels[i];

                if ((pixel >>> 24) == 0)
                {
                    continue;
                }

                if (result == null)
                {
                    result = new int[picture.pixelCount()];
                }

                result[i] = over(result[i], pixel, layer.opacity);
            }
        }

        return result;
    }

    /** The colour of one cell, every visible layer over each other. */
    public static int colorAt(GraffitiPicture picture, int x, int y)
    {
        if (x < 0 || y < 0 || x >= picture.width || y >= picture.height)
        {
            return 0;
        }

        int index = y * picture.width + x;
        int result = 0;

        for (CanvasLayerData layer : picture.layers)
        {
            if (layer.visible && layer.pixels.length == picture.pixelCount())
            {
                result = over(result, layer.pixels[index], layer.opacity);
            }
        }

        return result;
    }

    /** A blurred copy; radius in pixels, fractions allowed. */
    public static int[] blur(int[] pixels, int width, int height, float radius)
    {
        int[] copy = pixels.clone();

        if (radius <= 0.05F)
        {
            return copy;
        }

        CanvasRasterTools.blur(wrap(copy), width, height, (int) Math.ceil(radius * 1.5F), Math.max(0.3F, radius / 1.5F));

        return copy;
    }

    /** Glowing paint over a soft halo of its own colours. */
    public static int[] halo(int[] glow, int width, int height, float radius, float strength)
    {
        if (radius <= 0.05F || strength <= 0F)
        {
            return glow.clone();
        }

        int[] halo = blur(glow, width, height, radius);

        for (int i = 0; i < halo.length; i++)
        {
            int pixel = halo[i];
            int alpha = Math.min(255, Math.round(((pixel >>> 24) & 255) * strength));

            halo[i] = over((pixel & 0x00FFFFFF) | (alpha << 24), glow[i], 1F);
        }

        return halo;
    }

    /** Multiplies the alpha of every pixel, for onion skins. */
    public static int[] fade(int[] pixels, float opacity)
    {
        int[] copy = new int[pixels.length];

        for (int i = 0; i < pixels.length; i++)
        {
            int alpha = Math.round(((pixels[i] >>> 24) & 255) * opacity);

            copy[i] = alpha <= 0 ? 0 : (pixels[i] & 0x00FFFFFF) | (alpha << 24);
        }

        return copy;
    }

    /**
     * Glowing paint gathered into up to {@link #LIGHT_GRID}² lights, each at the brightness
     * weighted centre of its cell (in 0..1 of the picture, v down) and of its average colour.
     */
    public static List<Light> lights(int[] glow, int width, int height)
    {
        List<Light> lights = new ArrayList<>();

        if (glow == null)
        {
            return lights;
        }

        int cells = LIGHT_GRID * LIGHT_GRID;
        float[] sum = new float[cells];
        float[] u = new float[cells];
        float[] v = new float[cells];
        float[] red = new float[cells];
        float[] green = new float[cells];
        float[] blue = new float[cells];
        float total = 0F;

        for (int y = 0; y < height; y++)
        {
            int cellY = Math.min(LIGHT_GRID - 1, y * LIGHT_GRID / height);

            for (int x = 0; x < width; x++)
            {
                int pixel = glow[y * width + x];
                float alpha = ((pixel >>> 24) & 255) / 255F;

                if (alpha <= 0F)
                {
                    continue;
                }

                int cell = cellY * LIGHT_GRID + Math.min(LIGHT_GRID - 1, x * LIGHT_GRID / width);

                sum[cell] += alpha;
                u[cell] += (x + 0.5F) / width * alpha;
                v[cell] += (y + 0.5F) / height * alpha;
                red[cell] += ((pixel >> 16) & 255) / 255F * alpha;
                green[cell] += ((pixel >> 8) & 255) / 255F * alpha;
                blue[cell] += (pixel & 255) / 255F * alpha;
                total += alpha;
            }
        }

        for (int i = 0; i < cells; i++)
        {
            if (sum[i] > 0F)
            {
                lights.add(new Light(u[i] / sum[i], v[i] / sum[i], red[i] / sum[i], green[i] / sum[i],
                    blue[i] / sum[i], sum[i] / total));
            }
        }

        return lights;
    }

    public static void flipHorizontal(CanvasLayerData layer, int width, int height)
    {
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width / 2; x++)
            {
                swap(layer.pixels, y * width + x, y * width + width - 1 - x);
            }
        }
    }

    public static void flipVertical(CanvasLayerData layer, int width, int height)
    {
        for (int y = 0; y < height / 2; y++)
        {
            for (int x = 0; x < width; x++)
            {
                swap(layer.pixels, y * width + x, (height - 1 - y) * width + x);
            }
        }
    }

    /** Moves the pixels of a layer, what leaves the picture is gone. */
    public static void shift(CanvasLayerData layer, int[] source, int width, int height, int dx, int dy)
    {
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                int sx = x - dx;
                int sy = y - dy;

                layer.pixels[y * width + x] = sx >= 0 && sy >= 0 && sx < width && sy < height ? source[sy * width + sx] : 0;
            }
        }
    }

    /**
     * One dab of the blur brush: every pixel within the radius takes the average of its
     * neighbourhood in the snapshot, mixed in by the strength.
     */
    public static void blurDab(CanvasLayerData layer, int[] snapshot, int width, int height, int centerX, int centerY,
        int radius, float strength)
    {
        int reach = Math.max(1, radius / 2);

        for (int y = centerY - radius; y <= centerY + radius; y++)
        {
            for (int x = centerX - radius; x <= centerX + radius; x++)
            {
                int dx = x - centerX;
                int dy = y - centerY;

                if (x < 0 || y < 0 || x >= width || y >= height || dx * dx + dy * dy > radius * radius)
                {
                    continue;
                }

                float alpha = 0F;
                float red = 0F;
                float green = 0F;
                float blue = 0F;
                int count = 0;

                for (int sy = Math.max(0, y - reach); sy <= Math.min(height - 1, y + reach); sy++)
                {
                    for (int sx = Math.max(0, x - reach); sx <= Math.min(width - 1, x + reach); sx++)
                    {
                        int color = snapshot[sy * width + sx];
                        float a = ((color >>> 24) & 255) / 255F;

                        alpha += a;
                        red += ((color >> 16) & 255) * a;
                        green += ((color >> 8) & 255) * a;
                        blue += (color & 255) * a;
                        count++;
                    }
                }

                int blurred = alpha <= 0F ? 0 : (Math.round(alpha / count * 255F) << 24)
                    | (Math.min(255, Math.round(red / alpha)) << 16)
                    | (Math.min(255, Math.round(green / alpha)) << 8)
                    | Math.min(255, Math.round(blue / alpha));

                layer.pixels[y * width + x] = CanvasRasterTools.interpolatePremultiplied(snapshot[y * width + x], blurred, strength);
            }
        }
    }

    private static void swap(int[] pixels, int a, int b)
    {
        int temp = pixels[a];

        pixels[a] = pixels[b];
        pixels[b] = temp;
    }

    private static CanvasLayerData wrap(int[] pixels)
    {
        return new CanvasLayerData("blur", "blur", CanvasRenderBand.FOREGROUND, 0, true, 1F, pixels);
    }
}
