package mchorse.bbs_fsp.devserver;

import mchorse.bbs_fsp.BbsFspMod;
import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasRenderBand;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.List;

/**
 * Runs on a real dedicated server, where none of the client classes exist: the canvas clip has
 * to be registered there, and a film carrying one has to survive BBS's own save and load.
 * Logs every result with an "[server check]" prefix and stops the server when done.
 *
 * <p>With {@code -Dbbs_fsp.serverCheck.host=true} the server stays up for a client: the 1.20.4
 * in-game driver joins, paints and saves a film, leaves, joins again to load it back and leaves.
 * After the second leave the film the client sent is checked on disk and the server stops.</p>
 */
public final class CanvasServerCheck implements DedicatedServerModInitializer
{
    private static final String FILM = "bbs_fsp_server_check";
    private static final String CLIENT_FILM = "bbs_fsp_client_film";
    private static final String GRAFFITI_FILM = "bbs_fsp_server_graffiti";
    private static final boolean HOST = Boolean.getBoolean("bbs_fsp.serverCheck.host");

    private static int departures;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 68;

    @Override
    public void onInitializeServer()
    {
        ServerLifecycleEvents.SERVER_STARTED.register((server) ->
        {
            try
            {
                check();
                BbsFspMod.LOGGER.info("[server check] all checks passed");
            }
            catch (Throwable e)
            {
                BbsFspMod.LOGGER.error("[server check] FAILED", e);
                server.stop(false);

                return;
            }

            if (HOST)
            {
                BbsFspMod.LOGGER.info("[server check] waiting for the client");
            }
            else
            {
                server.stop(false);
            }
        });

        if (HOST)
        {
            ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() ->
            {
                departures += 1;

                if (departures < 2)
                {
                    return;
                }

                try
                {
                    checkClientFilm();
                    BbsFspMod.LOGGER.info("[server check] the client's film checks passed");
                }
                catch (Throwable e)
                {
                    BbsFspMod.LOGGER.error("[server check] FAILED", e);
                }
                finally
                {
                    server.stop(false);
                }
            }));
        }
    }

    /** The film the 1.20.4 driver painted and saved while connected, as the server stored it. */
    private static void checkClientFilm()
    {
        Film film = BBSMod.getFilms().load(CLIENT_FILM);

        require(film != null, "the server stored the film the client saved over the network");
        require(film.camera.get().size() == 1 && film.camera.get().get(0) instanceof CanvasClip, "the stored film has its canvas clip");

        CanvasClip clip = (CanvasClip) film.camera.get().get(0);
        CanvasLayerData last = layer(clip, clip.frames.getFrames().size() - 1, "behind_structures");
        long painted = 0;

        for (int pixel : layer(clip, 0, "foreground").pixels)
        {
            if (pixel == 0xFFFF0000)
            {
                painted += 1;
            }
        }

        require(painted >= 60, "the stroke the client painted is on the server's disk (" + painted + " pixels)");
        require(last.pixels[last.pixels.length - 1] == 0xFFFF00FF, "every frame the client sent is on the server's disk");
    }

    private static void check()
    {
        require(!classExists("net.minecraft.client.MinecraftClient"), "this is a dedicated server without Minecraft's client classes");

        require(BBSMod.getFactoryCameraClips().getTypeClass(BbsFspMod.CANVAS_LINK) == CanvasClip.class,
            "the canvas clip is registered on the dedicated server");

        Film film = new Film();
        CanvasClip clip = new CanvasClip();

        clip.duration.set(30);
        clip.ensureValidData();
        clip.offsetX.set(3F);
        clip.rotation.set(15F);
        layer(clip, 0, "foreground").pixels[10 * WIDTH + 20] = 0xFFFF0000;
        layer(clip, 0, "behind_entities").pixels[30 * WIDTH + 40] = 0x8000FFFF;
        layer(clip, 14, "behind_structures").pixels[HEIGHT * WIDTH - 1] = 0xFFFF00FF;
        film.camera.addClip(clip);

        FilmManager films = BBSMod.getFilms();

        require(films.save(FILM, (MapType) film.toData()), "BBS saved the film with the canvas clip");

        Film loaded = films.load(FILM);

        require(loaded != null, "BBS loaded the film back");

        List<Clip> clips = loaded.camera.get();

        require(clips.size() == 1 && clips.get(0) instanceof CanvasClip, "the loaded film still has its canvas clip");

        CanvasClip loadedClip = (CanvasClip) clips.get(0);

        require(loadedClip.frames.getFrames().size() == clip.frames.getFrames().size(), "every frame survived");
        require(loadedClip.offsetX.get() == 3F && loadedClip.rotation.get() == 15F, "the canvas transform survived");
        require(layer(loadedClip, 0, "foreground").pixels[10 * WIDTH + 20] == 0xFFFF0000, "the foreground painting survived");
        require(layer(loadedClip, 0, "behind_entities").pixels[30 * WIDTH + 40] == 0x8000FFFF, "a translucent pixel kept its alpha");
        require(layer(loadedClip, 14, "behind_structures").pixels[HEIGHT * WIDTH - 1] == 0xFFFF00FF, "the last frame's painting survived");
        require(layer(loadedClip, 14, "behind_structures").renderBand == CanvasRenderBand.BEHIND_STRUCTURES, "render bands survived");

        checkGraffiti(films);
    }

    /** A replay wearing a graffiti, its own picture and two pixel art keyframes, through BBS's save and load. */
    @SuppressWarnings("unchecked")
    private static void checkGraffiti(FilmManager films)
    {
        require(BBSMod.getForms().create(BbsFspMod.GRAFFITI_LINK) instanceof GraffitiForm, "the graffiti form is registered on the dedicated server");

        Film film = new Film();
        Replay replay = film.replays.addReplay();
        GraffitiForm form = new GraffitiForm();
        GraffitiPicture first = new GraffitiPicture(32, 16);
        GraffitiPicture second = new GraffitiPicture(32, 16);

        form.pixelArt.get().layers.get(0).pixels[5] = 0xFF00FF00;
        form.blur.set(2.5F);
        first.layers.get(0).pixels[0] = 0xFFFF0000;
        second.addLayer(1, true).pixels[32 * 16 - 1] = 0x80FFFF00;
        replay.form.set(form);

        KeyframeChannel<GraffitiPicture> channel = (KeyframeChannel<GraffitiPicture>) replay.properties.getOrCreate(form, GraffitiForm.PIXEL_ART);

        require(channel != null, "the pixel art property has a keyframe track");
        channel.insert(0, first);
        channel.insert(10, second);

        require(films.save(GRAFFITI_FILM, (MapType) film.toData()), "BBS saved the film with the graffiti");

        Film loaded = films.load(GRAFFITI_FILM);

        require(loaded != null && loaded.replays.getList().size() == 1, "BBS loaded the graffiti film back");

        Replay loadedReplay = loaded.replays.getList().get(0);

        require(loadedReplay.form.get() instanceof GraffitiForm, "the replay still wears a graffiti");

        GraffitiForm loadedForm = (GraffitiForm) loadedReplay.form.get();
        KeyframeChannel<GraffitiPicture> loadedChannel = (KeyframeChannel<GraffitiPicture>) loadedReplay.properties.getOrCreate(loadedForm, GraffitiForm.PIXEL_ART);

        require(loadedForm.pixelArt.get().layers.get(0).pixels[5] == 0xFF00FF00 && loadedForm.blur.get() == 2.5F, "the graffiti's own picture and blur survived");
        require(loadedChannel != null && loadedChannel.getKeyframes().size() == 2, "both pixel art keyframes survived");
        require(loadedChannel.get(0).getValue().layers.get(0).pixels[0] == 0xFFFF0000, "the first frame's painting survived");

        GraffitiPicture loadedSecond = loadedChannel.get(1).getValue();

        require(loadedSecond.layers.size() == 2 && loadedSecond.layers.get(1).glow, "the glowing layer survived");
        require(loadedSecond.layers.get(1).pixels[32 * 16 - 1] == 0x80FFFF00, "glowing paint kept its alpha");
    }

    private static boolean classExists(String name)
    {
        try
        {
            Class.forName(name, false, CanvasServerCheck.class.getClassLoader());

            return true;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }

    private static CanvasLayerData layer(CanvasClip clip, int frame, String id)
    {
        for (CanvasLayerData layer : clip.frames.getFrames().get(frame).layers)
        {
            if (layer.id.equals(id))
            {
                return layer;
            }
        }

        throw new AssertionError("frame " + frame + " has a layer " + id);
    }

    private static void require(boolean condition, String what)
    {
        if (!condition)
        {
            throw new AssertionError(what);
        }

        BbsFspMod.LOGGER.info("[server check] ok: {}", what);
    }
}
