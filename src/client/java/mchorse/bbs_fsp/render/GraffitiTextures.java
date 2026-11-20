package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.graffiti.GraffitiRaster;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * GPU images of graffiti frames. Every owner (a renderer, the editor) has a slot per kind of
 * image and the slot is uploaded again only when the frame or the effect settings change, so a
 * long stroke keeps reusing one texture. Slots nobody asked for in a while are freed.
 */
public final class GraffitiTextures
{
    private static final long SWEEP_INTERVAL = 1000L;
    private static final long UNUSED_LIFETIME = 10000L;

    private static final Map<Slot, Entry> ENTRIES = new HashMap<>();
    private static long lastSweep;

    private GraffitiTextures()
    {}

    public enum Kind
    {
        /** The plain layers, blurred. */
        PLAIN,
        /** The glowing layers over their halo, blurred. */
        GLOW,
        /** Every layer as it is, for picking and the flat editor view. */
        ALL,
        /** Every layer faded, the previous frame under the one being painted. */
        ONION
    }

    public record Settings(float blur, float glow, float glowRadius, float onion)
    {
        public static final Settings NONE = new Settings(0F, 0F, 0F, 1F);

        /** The effects of a graffiti as it is drawn now. */
        public static Settings of(GraffitiForm form)
        {
            return new Settings(GraffitiForm.clamped(form.blur), GraffitiForm.clamped(form.glow), GraffitiForm.clamped(form.glowRadius), 1F);
        }
    }

    private record Slot(Object owner, Kind kind)
    {
        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof Slot slot && slot.owner == this.owner && slot.kind == this.kind;
        }

        @Override
        public int hashCode()
        {
            return System.identityHashCode(this.owner) * 31 + this.kind.ordinal();
        }
    }

    public static final class Entry
    {
        private Texture texture;
        private long stamp = -1L;
        private Settings settings;
        private boolean empty = true;
        private List<GraffitiRaster.Light> lights = List.of();
        private long used;

        public Texture texture()
        {
            return this.empty ? null : this.texture;
        }

        public List<GraffitiRaster.Light> lights()
        {
            return this.lights;
        }
    }

    public static Entry get(Object owner, Kind kind, GraffitiPicture picture, Settings settings)
    {
        long now = System.currentTimeMillis();
        Slot slot = new Slot(owner, kind);
        Entry entry = ENTRIES.computeIfAbsent(slot, (key) -> new Entry());

        entry.used = now;

        if (picture == null)
        {
            entry.empty = true;
            entry.lights = List.of();
        }
        else if (entry.stamp != picture.getStamp() || !settings.equals(entry.settings))
        {
            upload(entry, kind, picture, settings);
        }

        if (now - lastSweep > SWEEP_INTERVAL)
        {
            lastSweep = now;
            sweep(now);
        }

        return entry;
    }

    private static void upload(Entry entry, Kind kind, GraffitiPicture picture, Settings settings)
    {
        int width = picture.width;
        int height = picture.height;
        int[] pixels = switch (kind)
        {
            case PLAIN -> GraffitiRaster.composite(picture, false);
            case GLOW -> GraffitiRaster.composite(picture, true);
            case ALL, ONION -> GraffitiRaster.compositeAll(picture);
        };

        entry.stamp = picture.getStamp();
        entry.settings = settings;
        entry.lights = kind == Kind.GLOW ? GraffitiRaster.lights(pixels, width, height) : List.of();
        entry.empty = pixels == null;

        if (pixels == null)
        {
            return;
        }

        if (kind == Kind.GLOW)
        {
            pixels = GraffitiRaster.halo(pixels, width, height, settings.glowRadius(), settings.glow());
        }

        if (kind == Kind.PLAIN || kind == Kind.GLOW)
        {
            pixels = GraffitiRaster.blur(pixels, width, height, settings.blur());
        }
        else if (kind == Kind.ONION)
        {
            pixels = GraffitiRaster.fade(pixels, settings.onion());
        }

        Pixels image = Pixels.fromIntArray(width, height, pixels);

        image.rewindBuffer();

        if (entry.texture == null)
        {
            entry.texture = Texture.textureFromPixels(image, GL11.GL_NEAREST);
        }
        else
        {
            entry.texture.bind();
            entry.texture.uploadTexture(image);
            entry.texture.unbind();
        }
    }

    private static void sweep(long now)
    {
        Iterator<Entry> iterator = ENTRIES.values().iterator();

        while (iterator.hasNext())
        {
            Entry entry = iterator.next();

            if (now - entry.used > UNUSED_LIFETIME)
            {
                if (entry.texture != null)
                {
                    entry.texture.delete();
                }

                iterator.remove();
            }
        }
    }

    public static void clear()
    {
        for (Entry entry : ENTRIES.values())
        {
            if (entry.texture != null)
            {
                entry.texture.delete();
            }
        }

        ENTRIES.clear();
    }
}
