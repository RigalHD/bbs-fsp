package mchorse.bbs_fsp.canvas;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ValueCanvasFrames extends BaseValue
{
    private final List<CanvasFrameData> frames = new ArrayList<>();
    private int width;
    private int height;

    public ValueCanvasFrames(String id, int width, int height)
    {
        super(id);
        this.width = width;
        this.height = height;
    }

    public List<CanvasFrameData> getFrames()
    {
        return this.frames;
    }

    public void configureSize(int width, int height)
    {
        CanvasDataLimits.pixelCount(width, height);

        int oldWidth = this.width;
        int oldHeight = this.height;

        for (CanvasFrameData frame : this.frames)
        {
            for (CanvasLayerData layer : frame.layers)
            {
                if (layer.pixels.length != width * height || oldWidth != width || oldHeight != height)
                {
                    int[] resized = new int[width * height];

                    if (layer.pixels.length == oldWidth * oldHeight)
                    {
                        for (int y = 0; y < height; y++)
                        {
                            int sourceY = Math.min(oldHeight - 1, (int) ((long) y * oldHeight / height));

                            for (int x = 0; x < width; x++)
                            {
                                int sourceX = Math.min(oldWidth - 1, (int) ((long) x * oldWidth / width));
                                resized[y * width + x] = layer.pixels[sourceY * oldWidth + sourceX];
                            }
                        }
                    }

                    layer.pixels = resized;
                }
            }
        }

        this.width = width;
        this.height = height;
    }

    public CanvasFrameData findAtTick(int tick)
    {
        for (CanvasFrameData frame : this.frames)
        {
            if (frame.tick == tick)
            {
                return frame;
            }
        }

        return null;
    }

    public CanvasFrameData addFrame(int tick, boolean duplicateCurrent)
    {
        CanvasFrameData existing = this.findAtTick(tick);

        if (existing != null)
        {
            return existing;
        }

        CanvasFrameData frame = duplicateCurrent ? this.getHeldFrame(tick) : null;
        CanvasFrameData created = frame == null
            ? new CanvasFrameData(tick, this.width, this.height)
            : frame.copy();
        created.tick = tick;
        this.frames.add(created);
        this.frames.sort(Comparator.comparingInt(item -> item.tick));

        return created;
    }

    public boolean removeFrame(int tick)
    {
        return this.frames.removeIf(frame -> frame.tick == tick);
    }

    public void setFrameCount(int requested, int duration)
    {
        int count = Math.max(1, Math.min(requested, Math.min(duration, CanvasDataLimits.MAX_FRAMES)));

        while (this.frames.size() < count)
        {
            int bestTick = -1;
            int bestGap = 0;
            int previous = -1;

            for (int i = 0; i <= this.frames.size(); i++)
            {
                int next = i == this.frames.size() ? duration : this.frames.get(i).tick;
                int gap = next - previous - 1;

                if (gap > bestGap)
                {
                    bestGap = gap;
                    bestTick = previous + 1 + gap / 2;
                }

                previous = next;
            }

            if (bestTick < 0)
            {
                break;
            }

            this.addFrame(bestTick, true);
        }

    }

    public void ensureDefaults(int duration)
    {
        if (!this.frames.isEmpty())
        {
            return;
        }

        int frameCount = Math.max(1, (duration + 1) / 2);

        for (int i = 0; i < frameCount; i++)
        {
            int tick = i * 2;

            if (tick >= duration)
            {
                break;
            }

            this.frames.add(new CanvasFrameData(tick, this.width, this.height));
        }
    }

    public CanvasFrameData getHeldFrame(int tick)
    {
        CanvasFrameData result = null;

        for (CanvasFrameData frame : this.frames)
        {
            if (frame.tick > tick)
            {
                break;
            }

            result = frame;
        }

        return result == null && !this.frames.isEmpty() ? this.frames.get(0) : result;
    }

    public void normalize(int duration)
    {
        this.frames.removeIf(frame -> frame.tick < 0 || frame.tick >= duration);
        this.frames.sort(Comparator.comparingInt(frame -> frame.tick));

        for (int i = this.frames.size() - 1; i > 0; i--)
        {
            if (this.frames.get(i).tick == this.frames.get(i - 1).tick)
            {
                this.frames.remove(i);
            }
        }

        if (this.frames.size() > CanvasDataLimits.MAX_FRAMES)
        {
            this.frames.subList(CanvasDataLimits.MAX_FRAMES, this.frames.size()).clear();
        }
    }

    public ValueCanvasFrames copyValue()
    {
        ValueCanvasFrames copy = new ValueCanvasFrames(this.getId(), this.width, this.height);

        for (CanvasFrameData frame : this.frames)
        {
            copy.frames.add(frame.copy());
        }

        return copy;
    }

    @Override
    public BaseType toData()
    {
        ListType data = new ListType();

        for (CanvasFrameData frame : this.frames)
        {
            data.add(frame.toData());
        }

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.frames.clear();

        if (!data.isList())
        {
            return;
        }

        ListType list = data.asList();

        for (int i = 0; i < Math.min(list.size(), CanvasDataLimits.MAX_FRAMES); i++)
        {
            if (list.get(i) instanceof mchorse.bbs_mod.data.types.MapType map)
            {
                this.frames.add(CanvasFrameData.fromData(map, this.width, this.height));
            }
        }

        this.normalize(Integer.MAX_VALUE);
    }
}
