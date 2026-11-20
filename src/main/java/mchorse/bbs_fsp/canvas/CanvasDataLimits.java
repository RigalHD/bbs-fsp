package mchorse.bbs_fsp.canvas;

final class CanvasDataLimits
{
    static final int MAX_WIDTH = 4096;
    static final int MAX_HEIGHT = 4096;
    static final int MAX_FRAMES = 10000;
    static final int MAX_LAYERS = 512;

    private CanvasDataLimits()
    {}

    static int pixelCount(int width, int height)
    {
        if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT)
        {
            throw new IllegalArgumentException("Canvas dimensions are outside the supported range");
        }

        long count = (long) width * height;

        if (count > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException("Canvas pixel count is too large");
        }

        return (int) count;
    }

    static float clampOpacity(float opacity)
    {
        if (!Float.isFinite(opacity))
        {
            return 1F;
        }

        return Math.max(0F, Math.min(1F, opacity));
    }
}
