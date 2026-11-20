package mchorse.bbs_fsp.canvas;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CanvasComposition
{
    private CanvasComposition()
    {}

    public record Sample(CanvasClip clip, CanvasFrameData frame, int localTick, int listOrder)
    {}

    public static List<Sample> resolve(Film film, int tick)
    {
        List<Sample> samples = new ArrayList<>();
        List<Clip> clips = film.camera.get();

        for (int i = 0; i < clips.size(); i++)
        {
            Clip clip = clips.get(i);

            if (clip instanceof CanvasClip canvas && canvas.enabled.get() && canvas.isInside(tick))
            {
                int localTick = tick - canvas.tick.get();
                CanvasFrameData frame = canvas.getFrameAtLocalTick(localTick);

                if (frame != null)
                {
                    samples.add(new Sample(canvas, frame, localTick, i));
                }
            }
        }

        samples.sort(Comparator
            .comparingInt((Sample sample) -> sample.clip.layer.get())
            .thenComparingInt(sample -> sample.clip.tick.get())
            .thenComparingInt(Sample::listOrder));

        return samples;
    }

    public static List<CanvasLayerData> layersForBand(CanvasFrameData frame, CanvasRenderBand band)
    {
        List<CanvasLayerData> layers = new ArrayList<>();

        for (CanvasLayerData layer : frame.layers)
        {
            if (layer.visible && layer.renderBand == band)
            {
                layers.add(layer);
            }
        }

        layers.sort(Comparator.comparingInt(layer -> layer.order));

        return layers;
    }

    /**
     * The colour a cell of the frame ends up with once every visible layer is composited in
     * render order, from the band behind structures up to the foreground, alpha included.
     */
    public static int colorAt(CanvasFrameData frame, int width, int height, int x, int y)
    {
        if (frame == null || x < 0 || y < 0 || x >= width || y >= height)
        {
            return 0;
        }

        int index = y * width + x;
        int result = 0;

        for (CanvasRenderBand band : CanvasRenderBand.values())
        {
            for (CanvasLayerData layer : layersForBand(frame, band))
            {
                if (layer.pixels.length != width * height)
                {
                    continue;
                }

                int pixel = layer.pixels[index];
                int alpha = Math.round(((pixel >>> 24) & 255) * layer.opacity);

                if (alpha > 0)
                {
                    result = CanvasRasterTools.sourceOver(result, (pixel & 0x00FFFFFF) | (alpha << 24));
                }
            }
        }

        return result;
    }
}
