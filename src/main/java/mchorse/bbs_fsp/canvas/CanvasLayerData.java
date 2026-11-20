package mchorse.bbs_fsp.canvas;

import mchorse.bbs_mod.data.types.IntArrayType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.Arrays;
import java.util.UUID;

public final class CanvasLayerData
{
    private static final int DEFAULT_PIXEL = 0;

    public String id;
    public String name;
    public CanvasRenderBand renderBand;
    public int order;
    public boolean visible;
    public float opacity;
    /** Painted with glowing paint: drawn at full brightness and, with IRLights, lights the scene. */
    public boolean glow;
    public int[] pixels;
    public long revision;

    public CanvasLayerData(int width, int height)
    {
        this(UUID.randomUUID().toString(), "Layer 0", CanvasRenderBand.FOREGROUND, 0, true, 1F,
            new int[CanvasDataLimits.pixelCount(width, height)]);
    }

    public CanvasLayerData(String id, String name, CanvasRenderBand renderBand, int order,
        boolean visible, float opacity, int[] pixels)
    {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.isBlank() ? "Layer " + order : name;
        this.renderBand = renderBand == null ? CanvasRenderBand.FOREGROUND : renderBand;
        this.order = order;
        this.visible = visible;
        this.opacity = CanvasDataLimits.clampOpacity(opacity);
        this.pixels = pixels == null ? new int[0] : pixels;
        this.revision = 0L;
    }

    public CanvasLayerData copy()
    {
        return this.withPixels(Arrays.copyOf(this.pixels, this.pixels.length));
    }

    /** The same layer over the given pixels, which may well be shared with this one. */
    public CanvasLayerData withPixels(int[] pixels)
    {
        CanvasLayerData copy = new CanvasLayerData(this.id, this.name, this.renderBand, this.order, this.visible,
            this.opacity, pixels);

        copy.glow = this.glow;

        return copy;
    }

    public CanvasLayerData duplicate(int newOrder)
    {
        CanvasLayerData duplicate = new CanvasLayerData(UUID.randomUUID().toString(), this.name + " copy", this.renderBand,
            newOrder, this.visible, this.opacity, Arrays.copyOf(this.pixels, this.pixels.length));

        duplicate.glow = this.glow;

        return duplicate;
    }

    public void touch()
    {
        this.revision += 1L;
    }

    public MapType toData()
    {
        MapType data = new MapType(false);

        data.putString("id", this.id);
        data.putString("name", this.name);
        data.putString("band", this.renderBand.name());
        data.putInt("order", this.order);
        data.putBool("visible", this.visible);
        data.putFloat("opacity", this.opacity);

        if (this.glow)
        {
            data.putBool("glow", true);
        }

        data.put("pixels", new IntArrayType(Arrays.copyOf(this.pixels, this.pixels.length)));

        return data;
    }

    public static CanvasLayerData fromData(MapType data, int width, int height, int fallbackOrder)
    {
        int expected = CanvasDataLimits.pixelCount(width, height);
        int[] stored = data.has("pixels") && data.get("pixels") instanceof IntArrayType
            ? ((IntArrayType) data.get("pixels")).value
            : null;
        int[] pixels = stored != null && stored.length == expected
            ? Arrays.copyOf(stored, stored.length)
            : new int[expected];

        CanvasLayerData layer = new CanvasLayerData(
            data.getString("id", UUID.randomUUID().toString()),
            data.getString("name", "Layer " + fallbackOrder),
            CanvasRenderBand.fromId(data.getString("band", CanvasRenderBand.FOREGROUND.name())),
            data.getInt("order", fallbackOrder),
            data.getBool("visible", true),
            data.getFloat("opacity", 1F),
            pixels
        );

        layer.glow = data.getBool("glow", false);

        return layer;
    }
}
