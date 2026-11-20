package mchorse.bbs_fsp.canvas;

import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CanvasFrameData
{
    public int tick;
    public final List<CanvasLayerData> layers = new ArrayList<>();

    public CanvasFrameData(int tick, int width, int height)
    {
        this.tick = tick;
        this.layers.add(new CanvasLayerData("foreground", "1", CanvasRenderBand.FOREGROUND,
            0, true, 1F, new int[CanvasDataLimits.pixelCount(width, height)]));
        this.layers.add(new CanvasLayerData("behind_entities", "2", CanvasRenderBand.BEHIND_ENTITIES,
            1, true, 1F, new int[CanvasDataLimits.pixelCount(width, height)]));
        this.layers.add(new CanvasLayerData("behind_structures", "3", CanvasRenderBand.BEHIND_STRUCTURES,
            2, true, 1F, new int[CanvasDataLimits.pixelCount(width, height)]));
    }

    private CanvasFrameData(int tick)
    {
        this.tick = tick;
    }

    public CanvasFrameData copy()
    {
        CanvasFrameData copy = new CanvasFrameData(this.tick);

        for (CanvasLayerData layer : this.layers)
        {
            copy.layers.add(layer.copy());
        }

        return copy;
    }

    public CanvasLayerData duplicateLayer(String id)
    {
        if (this.layers.size() >= CanvasDataLimits.MAX_LAYERS)
        {
            return null;
        }

        for (int i = 0; i < this.layers.size(); i++)
        {
            CanvasLayerData source = this.layers.get(i);

            if (source.id.equals(id))
            {
                CanvasLayerData duplicate = source.duplicate(i + 1);

                this.layers.add(i + 1, duplicate);

                for (int j = 0; j < this.layers.size(); j++)
                {
                    this.layers.get(j).order = j;
                }

                return duplicate;
            }
        }

        return null;
    }

    public MapType toData()
    {
        MapType data = new MapType(false);
        ListType layers = new ListType();

        data.putInt("tick", this.tick);

        for (CanvasLayerData layer : this.layers)
        {
            layers.add(layer.toData());
        }

        data.put("layers", layers);

        return data;
    }

    public static CanvasFrameData fromData(MapType data, int width, int height)
    {
        CanvasFrameData frame = new CanvasFrameData(data.getInt("tick", 0));
        ListType layers = data.getList("layers");

        frame.layers.clear();

        for (int i = 0; i < Math.min(layers.size(), CanvasDataLimits.MAX_LAYERS); i++)
        {
            if (layers.get(i) instanceof MapType map)
            {
                frame.layers.add(CanvasLayerData.fromData(map, width, height, i));
            }
        }

        if (frame.layers.isEmpty())
        {
            frame.layers.add(new CanvasLayerData(width, height));
        }

        frame.layers.sort(Comparator.comparingInt(layer -> layer.order));

        return frame;
    }
}
