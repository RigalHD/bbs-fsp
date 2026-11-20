package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * GPU copies of canvas layers, uploaded again only when a layer's revision moves. Layers that
 * have not been drawn for a while (an undo replaces every layer object, a scrubbed-away frame
 * is no longer shown) give their textures back.
 */
public final class CanvasTextureCache
{
    public static final CanvasTextureCache SHARED = new CanvasTextureCache();

    private static final long SWEEP_INTERVAL = 1000L;
    private static final long UNUSED_LIFETIME = 10000L;

    private final Map<CanvasLayerData, Entry> entries = new IdentityHashMap<>();
    private long lastSweep;

    public Texture get(CanvasLayerData layer, int width, int height)
    {
        long now = System.currentTimeMillis();
        Entry entry = this.entries.get(layer);

        if (entry == null || entry.revision != layer.revision || entry.texture.width != width || entry.texture.height != height)
        {
            entry = this.upload(layer, width, height, entry);
            this.entries.put(layer, entry);
        }

        entry.used = now;

        if (now - this.lastSweep > SWEEP_INTERVAL)
        {
            this.lastSweep = now;
            this.sweep(now);
        }

        return entry.texture;
    }

    private Entry upload(CanvasLayerData layer, int width, int height, Entry entry)
    {
        Pixels pixels = Pixels.fromIntArray(width, height, layer.pixels);
        Texture texture;

        try
        {
            pixels.rewindBuffer();

            if (entry != null && entry.texture.width == width && entry.texture.height == height)
            {
                texture = entry.texture;
                texture.bind();

                try
                {
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                    GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, width);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels.getBuffer());
                    GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                }
                finally
                {
                    texture.unbind();
                }
            }
            else
            {
                texture = Texture.textureFromPixels(pixels, GL11.GL_NEAREST);
            }
        }
        finally
        {
            if (pixels.getBuffer() != null)
            {
                pixels.delete();
            }
        }

        if (entry != null && texture != entry.texture)
        {
            entry.texture.delete();
        }

        return new Entry(texture, layer.revision);
    }

    private void sweep(long now)
    {
        Iterator<Entry> iterator = this.entries.values().iterator();

        while (iterator.hasNext())
        {
            Entry entry = iterator.next();

            if (now - entry.used > UNUSED_LIFETIME)
            {
                entry.texture.delete();
                iterator.remove();
            }
        }
    }

    public void remove(CanvasLayerData layer)
    {
        Entry entry = this.entries.remove(layer);

        if (entry != null)
        {
            entry.texture.delete();
        }
    }

    public void clear()
    {
        for (Entry entry : this.entries.values())
        {
            entry.texture.delete();
        }

        this.entries.clear();
    }

    private static final class Entry
    {
        private final Texture texture;
        private final long revision;
        private long used;

        private Entry(Texture texture, long revision)
        {
            this.texture = texture;
            this.revision = revision;
        }
    }
}
