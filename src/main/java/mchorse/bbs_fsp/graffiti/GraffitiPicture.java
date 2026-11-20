package mchorse.bbs_fsp.graffiti;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasRenderBand;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One frame of a graffiti: a stack of pixel layers of the same size. It is the value a pixel
 * art keyframe holds.
 *
 * <p>BBS copies a keyframe's value every time it evaluates a track, so copies share the pixel
 * arrays and an edit unshares them first ({@link #unshare()}). The stamp names the content: a
 * copy keeps it, every edit and every read from data takes a new one, which is what the texture
 * cache and the keyframe signature checks key on.</p>
 */
public final class GraffitiPicture
{
    public static final int DEFAULT_SIZE = 64;
    public static final int MAX_SIZE = 1024;
    public static final int MAX_LAYERS = 64;

    private static final AtomicLong STAMPS = new AtomicLong();

    public int width;
    public int height;
    public final List<CanvasLayerData> layers = new ArrayList<>();

    private long stamp = STAMPS.incrementAndGet();

    public GraffitiPicture(int width, int height)
    {
        this.width = clampSize(width);
        this.height = clampSize(height);
        this.layers.add(this.createLayer("1"));
    }

    private GraffitiPicture()
    {}

    public static int clampSize(int size)
    {
        return Math.max(1, Math.min(MAX_SIZE, size));
    }

    public long getStamp()
    {
        return this.stamp;
    }

    /** Marks the content as changed. */
    public void touch()
    {
        this.stamp = STAMPS.incrementAndGet();
    }

    public int pixelCount()
    {
        return this.width * this.height;
    }

    public boolean isEmpty()
    {
        for (CanvasLayerData layer : this.layers)
        {
            for (int pixel : layer.pixels)
            {
                if ((pixel >>> 24) != 0)
                {
                    return false;
                }
            }
        }

        return true;
    }

    /** A copy over the same pixel arrays: cheap, and safe as long as edits go through {@link #unshare()}. */
    public GraffitiPicture copy()
    {
        GraffitiPicture copy = new GraffitiPicture();

        copy.width = this.width;
        copy.height = this.height;
        copy.stamp = this.stamp;

        for (CanvasLayerData layer : this.layers)
        {
            CanvasLayerData shared = layer.withPixels(layer.pixels);

            shared.revision = layer.revision;
            copy.layers.add(shared);
        }

        return copy;
    }

    /** Gives every layer pixel arrays of its own, so painting can't reach any copy. */
    public void unshare()
    {
        for (CanvasLayerData layer : this.layers)
        {
            layer.pixels = Arrays.copyOf(layer.pixels, layer.pixels.length);
        }
    }

    public CanvasLayerData createLayer(String name)
    {
        return new CanvasLayerData(UUID.randomUUID().toString(), name, CanvasRenderBand.FOREGROUND,
            this.layers.size(), true, 1F, new int[this.pixelCount()]);
    }

    public CanvasLayerData findLayer(String id)
    {
        for (CanvasLayerData layer : this.layers)
        {
            if (layer.id.equals(id))
            {
                return layer;
            }
        }

        return null;
    }

    public int indexOf(String id)
    {
        for (int i = 0; i < this.layers.size(); i++)
        {
            if (this.layers.get(i).id.equals(id))
            {
                return i;
            }
        }

        return -1;
    }

    /** Layers are listed bottom first: the first one is painted over by every other. */
    public CanvasLayerData addLayer(int index, boolean glow)
    {
        if (this.layers.size() >= MAX_LAYERS)
        {
            return null;
        }

        CanvasLayerData layer = this.createLayer(String.valueOf(this.layers.size() + 1));

        layer.glow = glow;
        this.layers.add(Math.max(0, Math.min(this.layers.size(), index)), layer);
        this.reorder();
        this.touch();

        return layer;
    }

    public CanvasLayerData duplicateLayer(String id)
    {
        int index = this.indexOf(id);

        if (index < 0 || this.layers.size() >= MAX_LAYERS)
        {
            return null;
        }

        CanvasLayerData duplicate = this.layers.get(index).duplicate(index + 1);

        this.layers.add(index + 1, duplicate);
        this.reorder();
        this.touch();

        return duplicate;
    }

    public boolean removeLayer(String id)
    {
        int index = this.indexOf(id);

        if (index < 0 || this.layers.size() <= 1)
        {
            return false;
        }

        this.layers.remove(index);
        this.reorder();
        this.touch();

        return true;
    }

    public boolean moveLayer(String id, int direction)
    {
        int index = this.indexOf(id);
        int target = index + direction;

        if (index < 0 || target < 0 || target >= this.layers.size())
        {
            return false;
        }

        CanvasLayerData layer = this.layers.remove(index);

        this.layers.add(target, layer);
        this.reorder();
        this.touch();

        return true;
    }

    /** Merges the layer into the one under it. */
    public boolean mergeDown(String id)
    {
        int index = this.indexOf(id);

        if (index <= 0)
        {
            return false;
        }

        CanvasLayerData top = this.layers.get(index);
        CanvasLayerData bottom = this.layers.get(index - 1);

        if (top.visible)
        {
            for (int i = 0; i < bottom.pixels.length; i++)
            {
                bottom.pixels[i] = GraffitiRaster.over(bottom.pixels[i], top.pixels[i], top.opacity);
            }
        }

        bottom.touch();
        this.layers.remove(index);
        this.reorder();
        this.touch();

        return true;
    }

    /**
     * Changes the size, keeping the pixels where they are relative to the anchor (0 is the left
     * or top edge, 0.5 the middle, 1 the right or bottom edge).
     */
    public void resize(int width, int height, float anchorX, float anchorY)
    {
        width = clampSize(width);
        height = clampSize(height);

        if (width == this.width && height == this.height)
        {
            return;
        }

        int shiftX = Math.round((width - this.width) * anchorX);
        int shiftY = Math.round((height - this.height) * anchorY);

        for (CanvasLayerData layer : this.layers)
        {
            int[] pixels = new int[width * height];

            for (int y = 0; y < this.height; y++)
            {
                int ty = y + shiftY;

                if (ty < 0 || ty >= height)
                {
                    continue;
                }

                for (int x = 0; x < this.width; x++)
                {
                    int tx = x + shiftX;

                    if (tx >= 0 && tx < width)
                    {
                        pixels[ty * width + tx] = layer.pixels[y * this.width + x];
                    }
                }
            }

            layer.pixels = pixels;
            layer.touch();
        }

        this.width = width;
        this.height = height;
        this.touch();
    }

    private void reorder()
    {
        for (int i = 0; i < this.layers.size(); i++)
        {
            this.layers.get(i).order = i;
        }
    }

    public MapType toData()
    {
        MapType data = new MapType(false);
        ListType layers = new ListType();

        data.putInt("width", this.width);
        data.putInt("height", this.height);

        for (CanvasLayerData layer : this.layers)
        {
            layers.add(layer.toData());
        }

        data.put("layers", layers);

        return data;
    }

    public static GraffitiPicture fromData(MapType data)
    {
        GraffitiPicture picture = new GraffitiPicture();
        ListType layers = data.getList("layers");

        picture.width = clampSize(data.getInt("width", DEFAULT_SIZE));
        picture.height = clampSize(data.getInt("height", DEFAULT_SIZE));

        for (int i = 0; i < Math.min(layers.size(), MAX_LAYERS); i++)
        {
            if (layers.get(i) instanceof MapType map)
            {
                picture.layers.add(CanvasLayerData.fromData(map, picture.width, picture.height, i));
            }
        }

        if (picture.layers.isEmpty())
        {
            picture.layers.add(picture.createLayer("1"));
        }

        picture.reorder();

        return picture;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof GraffitiPicture other) || other.width != this.width || other.height != this.height
            || other.layers.size() != this.layers.size())
        {
            return false;
        }

        if (other.stamp == this.stamp)
        {
            return true;
        }

        for (int i = 0; i < this.layers.size(); i++)
        {
            CanvasLayerData a = this.layers.get(i);
            CanvasLayerData b = other.layers.get(i);

            if (!a.id.equals(b.id) || !a.name.equals(b.name) || a.visible != b.visible || a.glow != b.glow
                || a.opacity != b.opacity || !Arrays.equals(a.pixels, b.pixels))
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return 31 * (31 * this.width + this.height) + this.layers.size();
    }
}
