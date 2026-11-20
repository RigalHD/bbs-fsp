package mchorse.bbs_fsp.canvas;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.UUID;

public final class CanvasClip extends Clip
{
    public static final int DEFAULT_WIDTH = 120;
    public static final int DEFAULT_HEIGHT = 68;
    public static final int DEFAULT_DURATION = 30;

    public final ValueInt width = new ValueInt("width", DEFAULT_WIDTH, 1, CanvasDataLimits.MAX_WIDTH);
    public final ValueInt height = new ValueInt("height", DEFAULT_HEIGHT, 1, CanvasDataLimits.MAX_HEIGHT);
    public final ValueFloat offsetX = new ValueFloat("offsetX", 0F);
    public final ValueFloat offsetY = new ValueFloat("offsetY", 0F);
    public final ValueFloat rotation = new ValueFloat("rotation", 0F);
    public final ValueFloat gridOpacity = new ValueFloat("gridOpacity", 0.5F, 0F, 1F);
    public final ValueFloat previousFrameOpacity = new ValueFloat("previousFrameOpacity", 0.35F, 0F, 1F);
    public final ValueFloat paintOpacity = new ValueFloat("paintOpacity", 1F, 0F, 1F);
    public final ValueCanvasColors recentColors = new ValueCanvasColors("recentColors");
    public final ValueCanvasFrames frames = new ValueCanvasFrames("frames", DEFAULT_WIDTH, DEFAULT_HEIGHT);

    public CanvasClip()
    {
        super();
        this.duration.set(DEFAULT_DURATION);
        this.add(this.width);
        this.add(this.height);
        this.add(this.offsetX);
        this.add(this.offsetY);
        this.add(this.rotation);
        this.add(this.gridOpacity);
        this.add(this.previousFrameOpacity);
        this.add(this.paintOpacity);
        this.add(this.recentColors);
        this.add(this.frames);
        this.frames.ensureDefaults(this.duration.get());
    }

    public CanvasFrameData getFrameAtLocalTick(int localTick)
    {
        return this.frames.getHeldFrame(Math.max(0, localTick));
    }

    public void ensureValidData()
    {
        CanvasDataLimits.pixelCount(this.width.get(), this.height.get());
        this.frames.configureSize(this.width.get(), this.height.get());
        this.frames.normalize(this.duration.get());
        this.frames.ensureDefaults(this.duration.get());
    }

    public String addLayer()
    {
        if (this.frames.getFrames().isEmpty()
            || this.frames.getFrames().stream().anyMatch(frame -> frame.layers.size() >= CanvasDataLimits.MAX_LAYERS))
        {
            return null;
        }

        String id = UUID.randomUUID().toString();
        String name = String.valueOf(this.frames.getFrames().get(0).layers.size() + 1);

        for (CanvasFrameData frame : this.frames.getFrames())
        {
            int order = frame.layers.size();

            frame.layers.add(new CanvasLayerData(id, name, CanvasRenderBand.FOREGROUND,
                order, true, 1F, new int[CanvasDataLimits.pixelCount(this.width.get(), this.height.get())]));
        }

        return id;
    }

    @Override
    public CanvasClip create()
    {
        return new CanvasClip();
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            int storedWidth = map.getInt("width", DEFAULT_WIDTH);
            int storedHeight = map.getInt("height", DEFAULT_HEIGHT);

            this.width.set(storedWidth);
            this.height.set(storedHeight);
            this.frames.configureSize(this.width.get(), this.height.get());
        }

        super.fromData(data);
        this.frames.configureSize(this.width.get(), this.height.get());
        this.ensureValidData();
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        if (!(original instanceof CanvasClip source))
        {
            return;
        }

        this.frames.getFrames().clear();

        for (CanvasFrameData frame : source.frames.getFrames())
        {
            if (frame.tick >= offset)
            {
                CanvasFrameData copy = frame.copy();
                copy.tick -= offset;
                this.frames.getFrames().add(copy);
            }
        }

        CanvasFrameData held = source.frames.getHeldFrame(offset);

        if (held != null && (this.frames.getFrames().isEmpty() || this.frames.getFrames().get(0).tick > 0))
        {
            CanvasFrameData first = held.copy();
            first.tick = 0;
            this.frames.getFrames().add(0, first);
        }

        this.frames.normalize(this.duration.get());
        this.frames.ensureDefaults(this.duration.get());
    }
}
