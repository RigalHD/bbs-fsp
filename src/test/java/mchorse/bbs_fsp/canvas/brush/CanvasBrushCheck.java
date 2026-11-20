package mchorse.bbs_fsp.canvas.brush;

import mchorse.bbs_fsp.canvas.CanvasComposition;
import mchorse.bbs_fsp.canvas.CanvasFrameData;
import mchorse.bbs_fsp.canvas.CanvasLayerData;

import java.util.Arrays;

public final class CanvasBrushCheck
{
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    public static void main(String[] args)
    {
        checkStamps();
        checkPencil();
        checkMarkerDoesNotBuildUp();
        checkGradient();
        checkDither();
        checkSpray();
        checkEraser();
        checkGapless();
        checkComposite();
        checkPersistence();
    }

    private static void checkStamps()
    {
        require(stamp(1, CanvasBrushShape.SQUARE).count == 1);
        require(stamp(4, CanvasBrushShape.SQUARE).count == 16);
        require(stamp(3, CanvasBrushShape.CIRCLE).count == 5);
        require(stamp(3, CanvasBrushShape.DIAMOND).count == 5);
        require(stamp(5, CanvasBrushShape.CIRCLE).count == 21);
        require(stamp(5, CanvasBrushShape.DIAMOND).count == 13);
        require(stamp(7, CanvasBrushShape.HORIZONTAL).count == 7);
        require(stamp(7, CanvasBrushShape.HORIZONTAL).height == 1);
        require(stamp(6, CanvasBrushShape.VERTICAL).count == 6);
        require(stamp(6, CanvasBrushShape.VERTICAL).width == 1);

        CanvasBrushStamp even = stamp(4, CanvasBrushShape.SQUARE);

        require(Arrays.stream(even.offsetX).min().getAsInt() == -1);
        require(Arrays.stream(even.offsetX).max().getAsInt() == 2);

        for (CanvasBrushShape shape : CanvasBrushShape.values())
        {
            for (int size = CanvasBrush.MIN_SIZE; size <= CanvasBrush.MAX_SIZE; size++)
            {
                require(stamp(size, shape).count >= 1);
            }
        }

        CanvasBrush soft = new CanvasBrush(9, CanvasBrushShape.CIRCLE, 1F, 1F, 0.5F, 24);
        CanvasBrushStamp softStamp = CanvasBrushStamp.create(soft, true);
        float center = 0F;
        float edge = 1F;

        for (int i = 0; i < softStamp.count; i++)
        {
            if (softStamp.offsetX[i] == 0 && softStamp.offsetY[i] == 0)
            {
                center = softStamp.weight[i];
            }

            if (softStamp.offsetX[i] == 4 && softStamp.offsetY[i] == 0)
            {
                edge = softStamp.weight[i];
            }
        }

        require(center == 1F);
        require(edge < 0.5F);
    }

    private static void checkPencil()
    {
        CanvasLayerData layer = new CanvasLayerData(16, 16);
        CanvasStroke stroke = stroke(layer, 16, 16, CanvasBrushPattern.PENCIL, new CanvasBrush(1, CanvasBrushShape.SQUARE, 0F, 1F, 0.5F, 24), false, RED, 1F);

        stroke.moveTo(2, 3);
        stroke.moveTo(11, 8);

        require(layer.pixels[3 * 16 + 2] == RED);
        require(layer.pixels[8 * 16 + 11] == RED);
        require(Arrays.stream(layer.pixels).filter(pixel -> pixel != 0).count() == 10);
    }

    private static void checkMarkerDoesNotBuildUp()
    {
        CanvasLayerData layer = new CanvasLayerData(16, 4);
        CanvasStroke stroke = stroke(layer, 16, 4, CanvasBrushPattern.MARKER, new CanvasBrush(3, CanvasBrushShape.SQUARE, 0F, 0.5F, 0.5F, 24), false, RED, 1F);

        stroke.moveTo(1, 1);
        stroke.moveTo(14, 1);
        stroke.moveTo(1, 1);

        int alpha = (layer.pixels[16 + 7] >>> 24) & 255;

        require(alpha >= 126 && alpha <= 129);
        require(layer.pixels[16 + 7] == layer.pixels[16 + 8]);
    }

    private static void checkGradient()
    {
        CanvasLayerData layer = new CanvasLayerData(32, 1);
        CanvasStroke stroke = stroke(layer, 32, 1, CanvasBrushPattern.GRADIENT, new CanvasBrush(1, CanvasBrushShape.SQUARE, 0F, 1F, 0.5F, 20), false, RED, 1F);

        stroke.moveTo(0, 0);
        stroke.moveTo(31, 0);

        require(layer.pixels[0] == RED);
        require(layer.pixels[20] == BLUE);
        require(((layer.pixels[10] >> 16) & 255) > 100 && (layer.pixels[10] & 255) > 100);
        require(((layer.pixels[30] >> 16) & 255) > 100);
    }

    private static void checkDither()
    {
        CanvasLayerData layer = new CanvasLayerData(8, 8);
        CanvasStroke stroke = stroke(layer, 8, 8, CanvasBrushPattern.DITHER, new CanvasBrush(8, CanvasBrushShape.SQUARE, 0F, 1F, 0.5F, 24), false, RED, 1F);

        stroke.moveTo(3, 3);

        require(Arrays.stream(layer.pixels).filter(pixel -> pixel == RED).count() == 32);
        require(layer.pixels[0] == RED && layer.pixels[1] == 0 && layer.pixels[8] == 0 && layer.pixels[9] == RED);
    }

    private static void checkSpray()
    {
        CanvasLayerData first = new CanvasLayerData(32, 32);
        CanvasLayerData second = new CanvasLayerData(32, 32);
        CanvasBrush brush = new CanvasBrush(12, CanvasBrushShape.CIRCLE, 0F, 1F, 0.5F, 24);

        stroke(first, 32, 32, CanvasBrushPattern.SPRAY, brush, false, RED, 1F).moveTo(16, 16);
        stroke(second, 32, 32, CanvasBrushPattern.SPRAY, brush, false, RED, 1F).moveTo(16, 16);

        long painted = Arrays.stream(first.pixels).filter(pixel -> pixel != 0).count();

        require(painted > 0 && painted < 113);
        require(Arrays.equals(first.pixels, second.pixels));
    }

    private static void checkEraser()
    {
        CanvasLayerData layer = new CanvasLayerData(8, 8);

        Arrays.fill(layer.pixels, BLUE);
        stroke(layer, 8, 8, CanvasBrushPattern.PENCIL, new CanvasBrush(2, CanvasBrushShape.SQUARE, 0F, 1F, 0.5F, 24), true, RED, 1F).moveTo(4, 4);

        require(layer.pixels[4 * 8 + 4] == 0);
        require(layer.pixels[5 * 8 + 5] == 0);
        require(layer.pixels[0] == BLUE);

        CanvasLayerData half = new CanvasLayerData(4, 4);

        Arrays.fill(half.pixels, BLUE);
        stroke(half, 4, 4, CanvasBrushPattern.PENCIL, new CanvasBrush(1, CanvasBrushShape.SQUARE, 0F, 1F, 0.5F, 24), true, RED, 0.5F).moveTo(1, 1);

        int alpha = (half.pixels[5] >>> 24) & 255;

        require(alpha >= 126 && alpha <= 129);
        require((half.pixels[5] & 0x00FFFFFF) == (BLUE & 0x00FFFFFF));
    }

    private static void checkGapless()
    {
        CanvasLayerData layer = new CanvasLayerData(64, 64);
        CanvasStroke stroke = stroke(layer, 64, 64, CanvasBrushPattern.PENCIL, new CanvasBrush(1, CanvasBrushShape.SQUARE, 0F, 1F, 0.5F, 24), false, RED, 1F);

        stroke.moveTo(0, 0);
        stroke.moveTo(63, 40);
        stroke.moveTo(-20, 63);

        for (int x = 0; x < 64; x++)
        {
            int y = Math.round(x * 40F / 63F);
            boolean found = false;

            for (int dy = -1; dy <= 1; dy++)
            {
                int row = y + dy;

                if (row >= 0 && row < 64 && layer.pixels[row * 64 + x] == RED)
                {
                    found = true;
                }
            }

            require(found);
        }

        require(layer.pixels[63 * 64] == 0 || layer.pixels[63 * 64] == RED);
    }

    private static void checkComposite()
    {
        CanvasFrameData frame = new CanvasFrameData(0, 2, 1);

        frame.layers.get(2).pixels[0] = BLUE;
        frame.layers.get(0).pixels[0] = 0x80FF0000;
        frame.layers.get(1).pixels[1] = 0x40FFFFFF;

        int color = CanvasComposition.colorAt(frame, 2, 1, 0, 0);

        require((color >>> 24) == 255);
        require(((color >> 16) & 255) >= 126 && ((color >> 16) & 255) <= 130);
        require((color & 255) >= 125 && (color & 255) <= 129);
        require(CanvasComposition.colorAt(frame, 2, 1, 1, 0) == 0x40FFFFFF);
        require(CanvasComposition.colorAt(frame, 2, 1, 5, 0) == 0);

        frame.layers.get(0).visible = false;
        require(CanvasComposition.colorAt(frame, 2, 1, 0, 0) == BLUE);
    }

    private static void checkPersistence()
    {
        CanvasBrushSet brushes = new CanvasBrushSet();

        brushes.setPattern(CanvasBrushPattern.GRADIENT);
        brushes.getBrush().size = 17;
        brushes.getBrush().shape = CanvasBrushShape.DIAMOND;
        brushes.getBrush().length = 99;
        brushes.setEraser(true);
        brushes.setPrimary(0x7F123456);
        brushes.setSecondary(0xFF654321);

        CanvasBrushSet loaded = new CanvasBrushSet();

        loaded.fromData(brushes.toData());

        require(loaded.getPattern() == CanvasBrushPattern.GRADIENT);
        require(loaded.getBrush().equals(brushes.getBrush()));
        require(loaded.getBrush(CanvasBrushPattern.PENCIL).equals(CanvasBrush.defaults(CanvasBrushPattern.PENCIL)));
        require(loaded.isEraser());
        require(loaded.getPrimary() == 0x7F123456);
        require(loaded.getSecondary() == 0xFF654321);

        loaded.swapColors();
        require(loaded.getPrimary() == 0xFF654321 && loaded.getSecondary() == 0x7F123456);

        loaded.resetBrush();
        require(loaded.getBrush().equals(CanvasBrush.defaults(CanvasBrushPattern.GRADIENT)));
    }

    private static CanvasBrushStamp stamp(int size, CanvasBrushShape shape)
    {
        return CanvasBrushStamp.create(new CanvasBrush(size, shape, 0F, 1F, 0.5F, 24), false);
    }

    private static CanvasStroke stroke(CanvasLayerData layer, int width, int height, CanvasBrushPattern pattern,
        CanvasBrush brush, boolean eraser, int color, float opacity)
    {
        return new CanvasStroke(layer, width, height, pattern, brush, eraser, color, BLUE, opacity, 42L);
    }

    private static void require(boolean condition)
    {
        if (!condition)
        {
            throw new AssertionError();
        }
    }
}
