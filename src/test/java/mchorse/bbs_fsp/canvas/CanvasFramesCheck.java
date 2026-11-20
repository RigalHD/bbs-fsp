package mchorse.bbs_fsp.canvas;

import java.util.HashSet;
import java.util.Set;

public final class CanvasFramesCheck
{
    public static void main(String[] args)
    {
        ValueCanvasFrames frames = new ValueCanvasFrames("frames", 120, 68);

        frames.ensureDefaults(30);
        require(frames.getFrames().size() == 15);
        require(frames.getFrames().get(0).layers.size() == 3);
        require(frames.getFrames().get(0).layers.get(0).renderBand == CanvasRenderBand.FOREGROUND);
        require(frames.getFrames().get(0).layers.get(1).renderBand == CanvasRenderBand.BEHIND_ENTITIES);
        require(frames.getFrames().get(0).layers.get(2).renderBand == CanvasRenderBand.BEHIND_STRUCTURES);

        frames.setFrameCount(30, 30);
        Set<Integer> ticks = new HashSet<>();

        for (CanvasFrameData frame : frames.getFrames())
        {
            require(frame.tick >= 0 && frame.tick < 30);
            require(ticks.add(frame.tick));
        }

        require(frames.getFrames().size() == 30);
        frames.setFrameCount(40, 30);
        require(frames.getFrames().size() == 30);
        frames.setFrameCount(1, 30);
        require(frames.getFrames().size() == 30);
        require(frames.getFrames().get(0).tick == 0);

        CanvasClip clip = new CanvasClip();
        String layerId = clip.addLayer();

        require(layerId != null);

        for (CanvasFrameData frame : clip.frames.getFrames())
        {
            require(frame.layers.size() == 4);
            require(frame.layers.get(3).id.equals(layerId));
        }

        CanvasClip original = new CanvasClip();

        original.width.set(16);
        original.height.set(9);
        original.ensureValidData();
        original.offsetX.set(2.5F);
        original.rotation.set(30F);
        original.frames.getFrames().get(0).layers.get(0).pixels[5] = 0x80FF0000;
        original.frames.getFrames().get(3).layers.get(2).pixels[16 * 9 - 1] = 0xFF00FF00;

        CanvasClip loaded = new CanvasClip();

        loaded.fromData(original.toData());
        require(loaded.width.get() == 16 && loaded.height.get() == 9);
        require(loaded.offsetX.get() == 2.5F && loaded.rotation.get() == 30F);
        require(loaded.frames.getFrames().size() == original.frames.getFrames().size());
        require(loaded.frames.getFrames().get(0).layers.get(0).pixels[5] == 0x80FF0000);
        require(loaded.frames.getFrames().get(3).layers.get(2).pixels[16 * 9 - 1] == 0xFF00FF00);
        require(loaded.frames.getFrames().get(3).layers.get(2).renderBand == CanvasRenderBand.BEHIND_STRUCTURES);

        CanvasLayerData stroke = new CanvasLayerData(16, 16);

        CanvasRasterTools.line(stroke, 16, 16, 2, 3, 11, 8, 0xFF12AB34);
        require(stroke.pixels[3 * 16 + 2] == 0xFF12AB34);
        require(stroke.pixels[8 * 16 + 11] == 0xFF12AB34);
        require(java.util.Arrays.stream(stroke.pixels).filter(pixel -> pixel != 0).count() == 10);
    }

    private static void require(boolean condition)
    {
        if (!condition)
        {
            throw new AssertionError();
        }
    }
}
