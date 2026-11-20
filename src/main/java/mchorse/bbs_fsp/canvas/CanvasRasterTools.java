package mchorse.bbs_fsp.canvas;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class CanvasRasterTools
{
    private CanvasRasterTools()
    {}

    public static void pencil(CanvasLayerData layer, int width, int height, int x, int y, int color)
    {
        setPixel(layer, width, height, x, y, color);
    }

    public static void brush(CanvasLayerData layer, int width, int height, int centerX, int centerY,
        int size, boolean circle, int color)
    {
        int radius = Math.max(0, size - 1) / 2;
        int right = Math.max(0, size - 1) - radius;

        for (int y = centerY - radius; y <= centerY + right; y++)
        {
            for (int x = centerX - radius; x <= centerX + right; x++)
            {
                if (!circle || distanceSquared(x, y, centerX, centerY) <= (radius + 0.5F) * (radius + 0.5F))
                {
                    setPixel(layer, width, height, x, y, color);
                }
            }
        }
    }

    public static void erase(CanvasLayerData layer, int width, int height, int x, int y, int size,
        boolean circle, float opacity)
    {
        float amount = CanvasDataLimits.clampOpacity(opacity);
        int radius = Math.max(0, size - 1) / 2;
        int right = Math.max(0, size - 1) - radius;

        for (int py = y - radius; py <= y + right; py++)
        {
            for (int px = x - radius; px <= x + right; px++)
            {
                if (!circle || distanceSquared(px, py, x, y) <= (radius + 0.5F) * (radius + 0.5F))
                {
                    int current = getPixel(layer, width, height, px, py);
                    int alpha = Math.max(0, Math.round(((current >>> 24) & 255) * (1F - amount)));
                    setPixel(layer, width, height, px, py, (current & 0x00ffffff) | (alpha << 24));
                }
            }
        }
    }

    public static void line(CanvasLayerData layer, int width, int height, int x0, int y0, int x1, int y1,
        int color)
    {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;

        while (true)
        {
            setPixel(layer, width, height, x0, y0, color);

            if (x0 == x1 && y0 == y1)
            {
                break;
            }

            int error2 = error * 2;

            if (error2 >= dy)
            {
                error += dy;
                x0 += sx;
            }

            if (error2 <= dx)
            {
                error += dx;
                y0 += sy;
            }
        }
    }

    public static void rectangle(CanvasLayerData layer, int width, int height, int x0, int y0, int x1, int y1,
        int color, boolean filled)
    {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);

        for (int y = top; y <= bottom; y++)
        {
            for (int x = left; x <= right; x++)
            {
                if (filled || x == left || x == right || y == top || y == bottom)
                {
                    setPixel(layer, width, height, x, y, color);
                }
            }
        }
    }

    public static void ellipse(CanvasLayerData layer, int width, int height, int x0, int y0, int x1, int y1,
        int color, boolean filled)
    {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        double centerX = (left + right) / 2D;
        double centerY = (top + bottom) / 2D;
        double radiusX = Math.max(0.5D, (right - left) / 2D);
        double radiusY = Math.max(0.5D, (bottom - top) / 2D);
        double threshold = filled ? 1.0D : 0.18D;

        for (int y = top; y <= bottom; y++)
        {
            for (int x = left; x <= right; x++)
            {
                double value = Math.pow((x - centerX) / radiusX, 2D) + Math.pow((y - centerY) / radiusY, 2D);

                if (filled ? value <= 1D : value <= 1D && value >= 1D - threshold)
                {
                    setPixel(layer, width, height, x, y, color);
                }
            }
        }
    }

    public static void fill(CanvasLayerData layer, int width, int height, int startX, int startY, int color)
    {
        if (!inside(width, height, startX, startY))
        {
            return;
        }

        int target = getPixel(layer, width, height, startX, startY);

        if (target == color)
        {
            return;
        }

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        boolean[] visited = new boolean[width * height];
        queue.add(startY * width + startX);

        while (!queue.isEmpty())
        {
            int index = queue.removeFirst();

            if (visited[index] || layer.pixels[index] != target)
            {
                continue;
            }

            visited[index] = true;
            layer.pixels[index] = color;
            int x = index % width;
            int y = index / width;

            if (x > 0) queue.add(index - 1);
            if (x + 1 < width) queue.add(index + 1);
            if (y > 0) queue.add(index - width);
            if (y + 1 < height) queue.add(index + width);
        }
    }

    public static void gradient(CanvasLayerData layer, int width, int height, float startX, float startY,
        float endX, float endY, int startColor, int endColor, boolean radial)
    {
        float dx = endX - startX;
        float dy = endY - startY;
        float lengthSquared = dx * dx + dy * dy;

        if (lengthSquared < 0.000001F)
        {
            Arrays.fill(layer.pixels, endColor);
            return;
        }

        float length = (float) Math.sqrt(lengthSquared);

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                float t;

                if (radial)
                {
                    t = (float) Math.sqrt((x - startX) * (x - startX) + (y - startY) * (y - startY)) / length;
                }
                else
                {
                    t = ((x - startX) * dx + (y - startY) * dy) / lengthSquared;
                }

                layer.pixels[y * width + x] = interpolatePremultiplied(startColor, endColor,
                    Math.max(0F, Math.min(1F, t)));
            }
        }
    }

    public static void blur(CanvasLayerData layer, int width, int height, int radius, float sigma)
    {
        if (radius <= 0)
        {
            return;
        }

        float actualSigma = sigma > 0F && Float.isFinite(sigma) ? sigma : Math.max(0.5F, radius / 2F);
        float[] kernel = gaussianKernel(radius, actualSigma);
        int[] horizontal = new int[width * height];

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                horizontal[y * width + x] = sampleHorizontal(layer.pixels, width, height, x, y, kernel, radius);
            }
        }

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                layer.pixels[y * width + x] = sampleVertical(horizontal, width, height, x, y, kernel, radius);
            }
        }
    }

    public static int getPixel(CanvasLayerData layer, int width, int height, int x, int y)
    {
        return inside(width, height, x, y) ? layer.pixels[y * width + x] : 0;
    }

    public static void setPixel(CanvasLayerData layer, int width, int height, int x, int y, int color)
    {
        if (!inside(width, height, x, y))
        {
            return;
        }

        int index = y * width + x;
        layer.pixels[index] = sourceOver(layer.pixels[index], color);
    }

    private static boolean inside(int width, int height, int x, int y)
    {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private static int distanceSquared(int x0, int y0, int x1, int y1)
    {
        int dx = x0 - x1;
        int dy = y0 - y1;

        return dx * dx + dy * dy;
    }

    public static int sourceOver(int destination, int source)
    {
        float sourceAlpha = ((source >>> 24) & 255) / 255F;
        float destinationAlpha = ((destination >>> 24) & 255) / 255F;
        float outputAlpha = sourceAlpha + destinationAlpha * (1F - sourceAlpha);

        if (outputAlpha <= 0F)
        {
            return 0;
        }

        float red = (((source >> 16) & 255) * sourceAlpha + ((destination >> 16) & 255) * destinationAlpha * (1F - sourceAlpha)) / outputAlpha;
        float green = (((source >> 8) & 255) * sourceAlpha + ((destination >> 8) & 255) * destinationAlpha * (1F - sourceAlpha)) / outputAlpha;
        float blue = ((source & 255) * sourceAlpha + (destination & 255) * destinationAlpha * (1F - sourceAlpha)) / outputAlpha;

        return (Math.round(outputAlpha * 255F) << 24)
            | (Math.round(red) << 16)
            | (Math.round(green) << 8)
            | Math.round(blue);
    }

    public static int interpolatePremultiplied(int first, int second, float amount)
    {
        float a0 = ((first >>> 24) & 255) / 255F;
        float a1 = ((second >>> 24) & 255) / 255F;
        float alpha = a0 + (a1 - a0) * amount;

        if (alpha <= 0F)
        {
            return 0;
        }

        float r0 = ((first >> 16) & 255) / 255F * a0;
        float g0 = ((first >> 8) & 255) / 255F * a0;
        float b0 = (first & 255) / 255F * a0;
        float r1 = ((second >> 16) & 255) / 255F * a1;
        float g1 = ((second >> 8) & 255) / 255F * a1;
        float b1 = (second & 255) / 255F * a1;

        int red = Math.round(Math.max(0F, Math.min(1F, (r0 + (r1 - r0) * amount) / alpha)) * 255F);
        int green = Math.round(Math.max(0F, Math.min(1F, (g0 + (g1 - g0) * amount) / alpha)) * 255F);
        int blue = Math.round(Math.max(0F, Math.min(1F, (b0 + (b1 - b0) * amount) / alpha)) * 255F);

        return (Math.round(alpha * 255F) << 24) | (red << 16) | (green << 8) | blue;
    }

    private static float[] gaussianKernel(int radius, float sigma)
    {
        float[] kernel = new float[radius * 2 + 1];
        float sum = 0F;
        float denominator = 2F * sigma * sigma;

        for (int i = -radius; i <= radius; i++)
        {
            float value = (float) Math.exp(-(i * i) / denominator);
            kernel[i + radius] = value;
            sum += value;
        }

        for (int i = 0; i < kernel.length; i++)
        {
            kernel[i] /= sum;
        }

        return kernel;
    }

    private static int sampleHorizontal(int[] pixels, int width, int height, int x, int y, float[] kernel, int radius)
    {
        float alpha = 0F;
        float red = 0F;
        float green = 0F;
        float blue = 0F;

        for (int i = -radius; i <= radius; i++)
        {
            int sampleX = Math.max(0, Math.min(width - 1, x + i));
            int color = pixels[y * width + sampleX];
            float weight = kernel[i + radius];
            float sampleAlpha = ((color >>> 24) & 255) / 255F;

            alpha += sampleAlpha * weight;
            red += ((color >> 16) & 255) / 255F * sampleAlpha * weight;
            green += ((color >> 8) & 255) / 255F * sampleAlpha * weight;
            blue += (color & 255) / 255F * sampleAlpha * weight;
        }

        return unpremultiplied(alpha, red, green, blue);
    }

    private static int sampleVertical(int[] pixels, int width, int height, int x, int y, float[] kernel, int radius)
    {
        float alpha = 0F;
        float red = 0F;
        float green = 0F;
        float blue = 0F;

        for (int i = -radius; i <= radius; i++)
        {
            int sampleY = Math.max(0, Math.min(height - 1, y + i));
            int color = pixels[sampleY * width + x];
            float weight = kernel[i + radius];
            float sampleAlpha = ((color >>> 24) & 255) / 255F;

            alpha += sampleAlpha * weight;
            red += ((color >> 16) & 255) / 255F * sampleAlpha * weight;
            green += ((color >> 8) & 255) / 255F * sampleAlpha * weight;
            blue += (color & 255) / 255F * sampleAlpha * weight;
        }

        return unpremultiplied(alpha, red, green, blue);
    }

    private static int unpremultiplied(float alpha, float red, float green, float blue)
    {
        if (alpha <= 0F)
        {
            return 0;
        }

        return (Math.round(alpha * 255F) << 24)
            | (Math.round(Math.max(0F, Math.min(1F, red / alpha)) * 255F) << 16)
            | (Math.round(Math.max(0F, Math.min(1F, green / alpha)) * 255F) << 8)
            | Math.round(Math.max(0F, Math.min(1F, blue / alpha)) * 255F);
    }
}
