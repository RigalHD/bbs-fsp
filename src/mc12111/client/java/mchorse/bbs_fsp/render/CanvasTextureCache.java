package mchorse.bbs_fsp.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import mchorse.bbs_fsp.BbsFspMod;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * GPU copies of canvas layers as vanilla dynamic textures, so both the deferred interface and
 * the world render layers can name them by identifier. A layer is uploaded again only when its
 * revision moves; layers that have not been drawn for a while give their textures back.
 */
public final class CanvasTextureCache
{
    public static final CanvasTextureCache SHARED = new CanvasTextureCache();

    private static final long SWEEP_INTERVAL = 1000L;
    private static final long UNUSED_LIFETIME = 10000L;

    private final Map<CanvasLayerData, Entry> entries = new IdentityHashMap<>();
    private Consumer<Identifier> onRemove = (id) -> {};
    private long lastSweep;
    private int counter;

    public void onRemove(Consumer<Identifier> onRemove)
    {
        this.onRemove = onRemove;
    }

    public Identifier get(CanvasLayerData layer, int width, int height)
    {
        long now = System.currentTimeMillis();
        Entry entry = this.entries.get(layer);

        if (entry == null || entry.width != width || entry.height != height)
        {
            if (entry != null)
            {
                this.destroy(entry);
            }

            entry = this.create(width, height);
            this.entries.put(layer, entry);
        }

        if (entry.revision != layer.revision || !entry.uploaded)
        {
            this.upload(entry, layer);
        }

        entry.used = now;

        if (now - this.lastSweep > SWEEP_INTERVAL)
        {
            this.lastSweep = now;
            this.sweep(now);
        }

        return entry.id;
    }

    private Entry create(int width, int height)
    {
        Identifier id = Identifier.of(BbsFspMod.MOD_ID, "canvas/layer_" + (this.counter++));
        CanvasTexture texture = new CanvasTexture(id.toString(), width, height);

        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);

        return new Entry(id, texture, width, height);
    }

    private void upload(Entry entry, CanvasLayerData layer)
    {
        NativeImage image = entry.texture.getImage();

        if (image == null || layer.pixels.length != entry.width * entry.height)
        {
            return;
        }

        for (int y = 0; y < entry.height; y++)
        {
            int row = y * entry.width;

            for (int x = 0; x < entry.width; x++)
            {
                image.setColorArgb(x, y, layer.pixels[row + x]);
            }
        }

        entry.texture.upload();
        entry.revision = layer.revision;
        entry.uploaded = true;
    }

    private void sweep(long now)
    {
        Iterator<Entry> iterator = this.entries.values().iterator();

        while (iterator.hasNext())
        {
            Entry entry = iterator.next();

            if (now - entry.used > UNUSED_LIFETIME)
            {
                this.destroy(entry);
                iterator.remove();
            }
        }
    }

    private void destroy(Entry entry)
    {
        this.onRemove.accept(entry.id);
        MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
    }

    public void remove(CanvasLayerData layer)
    {
        Entry entry = this.entries.remove(layer);

        if (entry != null)
        {
            this.destroy(entry);
        }
    }

    public void clear()
    {
        for (Entry entry : this.entries.values())
        {
            this.destroy(entry);
        }

        this.entries.clear();
    }

    private static final class Entry
    {
        private final Identifier id;
        private final CanvasTexture texture;
        private final int width;
        private final int height;
        private long revision;
        private boolean uploaded;
        private long used;

        private Entry(Identifier id, CanvasTexture texture, int width, int height)
        {
            this.id = id;
            this.texture = texture;
            this.width = width;
            this.height = height;
        }
    }

    private static final class CanvasTexture extends NativeImageBackedTexture
    {
        private CanvasTexture(String name, int width, int height)
        {
            super(() -> name, width, height, true);
            this.sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
        }
    }
}
