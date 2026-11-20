package mchorse.bbs_fsp.devtest;

import mchorse.bbs_fsp.BbsFspMod;
import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.canvas.CanvasComposition;
import mchorse.bbs_fsp.canvas.CanvasFrameData;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasViewportMath;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushPattern;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushSet;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushShape;
import mchorse.bbs_fsp.devtest.mixin.MouseInvoker;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.render.GraffitiFormRenderer;
import mchorse.bbs_fsp.render.GraffitiScene;
import mchorse.bbs_fsp.ui.graffiti.GraffitiSession;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiFrames;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiEditor;
import mchorse.bbs_fsp.render.CanvasPlayback;
import mchorse.bbs_fsp.ui.CanvasEyedropper;
import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_fsp.ui.UICanvasBrushEditor;
import mchorse.bbs_fsp.ui.UICanvasClip;
import mchorse.bbs_fsp.ui.UICanvasColorHistory;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.onboarding.TourChapter;
import mchorse.bbs_mod.ui.onboarding.Tours;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * In-game checks for Minecraft 1.20.4, which has no client game tests: creates a world, opens
 * the film editor on a canvas clip and walks through painting, the brush editor and the screen
 * eyedropper. Mouse events go straight to the game's own handlers, so the real cursor is never
 * moved. Every result is logged with an "[in-game check]" prefix and the client quits at the end.
 */
public final class CanvasDevTest implements ClientModInitializer
{
    private static final int WIDTH = 48;
    private static final int HEIGHT = 27;
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;

    private final Deque<Step> steps = new ArrayDeque<>();
    private int delay;
    private int waited;
    private CanvasClip clip;
    private Replay replay;
    private Vector3d flightStart;
    private Object received;
    private double[] cursor = {0, 0};
    private int expected;

    private record Step(String name, Predicate<MinecraftClient> action, int after)
    {}

    @Override
    public void onInitializeClient()
    {
        /* The checks run with the sound off: nobody listens to them. */
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register((client) ->
            client.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER).setValue(0D));

        String server = System.getProperty("bbs_fsp.devtest.server");

        if (server == null)
        {
            this.plan();
        }
        else
        {
            this.planServer(server);
        }

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    /**
     * Against a dedicated server started with runDevserver -PserverCheckHost: join, paint and save
     * a full-size canvas film through BBS's network repository, leave, join again and load it
     * back over the network. The server checks its own copy after the second leave.
     */
    private void planServer(String address)
    {
        String id = "bbs_fsp_client_film";
        int width = CanvasClip.DEFAULT_WIDTH;
        int height = CanvasClip.DEFAULT_HEIGHT;

        this.step("title screen", 20, (client) -> client.currentScreen instanceof TitleScreen);
        this.run("join " + address, 0, (client) -> join(client, address));
        this.step("joined", 100, (client) -> client.world != null && client.player != null && client.currentScreen == null);
        this.run("open the film editor", 60, (client) -> this.clip = openEditor(id, width, height));
        this.run("pencil brush", 0, (client) ->
        {
            CanvasBrushSet brushes = CanvasViewportController.brushes();

            brushes.setPattern(CanvasBrushPattern.PENCIL);
            brushes.getBrush().size = 2;
            brushes.setEraser(false);
            brushes.setPrimary(RED);
            brushes.touch();
            CanvasViewportController.selectLayer("foreground");
        });
        this.drag(new int[][] {{20, 20}, {90, 40}});
        this.run("save to the server", 60, (client) ->
        {
            require(near(layer(this.clip, "foreground"), width, height, 90, 40, RED), "painted while connected to a dedicated server");

            for (CanvasFrameData frame : this.clip.frames.getFrames())
            {
                layer(frame, "behind_structures").pixels[width * height - 1] = 0xFFFF00FF;
            }

            BBSModClient.getDashboard().getPanel(UIFilmPanel.class).forceSave();
        });
        this.screenshot("bbs_fsp_10_server_editor");
        this.run("leave", 0, (client) -> leave(client));
        this.step("left", 40, (client) -> client.world == null);
        this.run("join again", 0, (client) -> join(client, address));
        this.step("joined again", 60, (client) -> client.world != null && client.player != null);
        this.run("load the film over the network", 0, (client) -> ContentType.FILMS.getRepository().load(id, (data) -> this.received = data));
        this.step("film received", 0, (client) -> this.received != null);
        this.run("film from the server", 0, (client) ->
        {
            require(this.received instanceof Film, "the film came back from the server after reconnecting");

            Film film = (Film) this.received;

            require(film.camera.get().size() == 1 && film.camera.get().get(0) instanceof CanvasClip, "the film from the server still has its canvas clip");

            CanvasClip loaded = (CanvasClip) film.camera.get().get(0);
            List<CanvasFrameData> frames = loaded.frames.getFrames();

            require(loaded.width.get() == width && loaded.height.get() == height, "the canvas size came back");
            require(near(layer(loaded, "foreground"), width, height, 90, 40, RED), "the painting came back from the server");
            require(layer(frames.get(frames.size() - 1), "behind_structures").pixels[width * height - 1] == 0xFFFF00FF,
                "every frame came back from the server");
        });
        this.run("leave again", 40, (client) -> leave(client));
        this.run("done", 0, (client) ->
        {
            BbsFspMod.LOGGER.info("[in-game check] all checks passed");
            client.scheduleStop();
        });
    }

    private static void join(MinecraftClient client, String address)
    {
        ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(address),
            new ServerInfo("BBS FSP checks", address, ServerInfo.ServerType.OTHER), false);
    }

    private static void leave(MinecraftClient client)
    {
        client.setScreen(null);

        if (client.world != null)
        {
            client.world.disconnect();
        }

        client.disconnect(new TitleScreen());
    }

    private void tick(MinecraftClient client)
    {
        if (this.steps.isEmpty())
        {
            return;
        }

        if (this.delay > 0)
        {
            this.delay -= 1;

            return;
        }

        Step step = this.steps.peekFirst();

        try
        {
            if (step.action().test(client))
            {
                this.steps.removeFirst();
                this.delay = step.after();
                this.waited = 0;
            }
            else if (++this.waited > 2400)
            {
                throw new AssertionError("timed out waiting for: " + step.name());
            }
        }
        catch (Throwable e)
        {
            BbsFspMod.LOGGER.error("[in-game check] FAILED at \"" + step.name() + "\"", e);
            this.steps.clear();
            client.scheduleStop();
        }
    }

    private void step(String name, int after, Predicate<MinecraftClient> action)
    {
        this.steps.addLast(new Step(name, action, after));
    }

    private void run(String name, int after, java.util.function.Consumer<MinecraftClient> action)
    {
        this.step(name, after, (client) ->
        {
            action.accept(client);

            return true;
        });
    }

    private void plan()
    {
        this.step("title screen", 20, (client) -> client.currentScreen instanceof TitleScreen);
        this.run("open world creation", 40, (client) -> CreateWorldScreen.create(client, client.currentScreen));
        this.step("create the world", 0, (client) -> pressButton(client.currentScreen, "selectWorld.create"));
        this.step("world loaded", 200, (client) -> client.world != null && client.player != null && client.currentScreen == null);

        this.run("registration", 0, (client) -> require(
            BBSMod.getFactoryCameraClips().getTypeClass(BbsFspMod.CANVAS_LINK) == CanvasClip.class,
            "the canvas clip is registered in the BBS camera clip factory"));
        this.bbmodels();
        this.graffiti();
        this.run("open the film editor", 60, (client) -> this.clip = openEditor("bbs_fsp_devtest", WIDTH, HEIGHT));
        this.run("editor is shown", 2, (client) ->
        {
            require(client.currentScreen instanceof UIScreen, "the BBS dashboard is open");
            require(find(UICanvasClip.class) != null, "the canvas clip panel is shown");
            require(find(UICanvasBrushEditor.class) != null, "the brush editor is shown");
        });
        this.screenshot("bbs_fsp_01_editor");

        this.run("pencil brush", 0, (client) ->
        {
            CanvasBrushSet brushes = CanvasViewportController.brushes();

            brushes.setPattern(CanvasBrushPattern.PENCIL);
            brushes.getBrush().size = 1;
            brushes.getBrush().shape = CanvasBrushShape.SQUARE;
            brushes.setEraser(false);
            brushes.setPrimary(RED);
            brushes.touch();
            CanvasViewportController.selectLayer("foreground");
        });
        this.drag(new int[][] {{6, 6}, {20, 12}, {30, 12}});
        this.run("pencil result", 5, (client) ->
        {
            CanvasLayerData layer = layer(this.clip, "foreground");

            require(!CanvasViewportController.isDrawing(), "the stroke ends when the button is released");
            require(near(layer, 6, 6, RED), "pencil painted its first cell");
            require(near(layer, 20, 12, RED), "pencil reached the second point");
            require(near(layer, 30, 12, RED), "pencil reached the last point");
            require(count(layer, RED) >= 25, "pencil left no gaps between the points");
        });
        this.screenshot("bbs_fsp_02_pencil");

        this.run("marker brush", 0, (client) ->
        {
            CanvasBrushSet brushes = CanvasViewportController.brushes();

            brushes.setPattern(CanvasBrushPattern.MARKER);
            brushes.getBrush().size = 3;
            brushes.getBrush().shape = CanvasBrushShape.SQUARE;
            brushes.getBrush().softness = 0F;
            brushes.getBrush().flow = 0.5F;
            brushes.setPrimary(BLUE);
            brushes.touch();
        });
        this.drag(new int[][] {{8, 20}, {40, 20}, {8, 20}, {40, 20}});
        this.run("marker result", 5, (client) ->
        {
            int pixel = layer(this.clip, "foreground").pixels[20 * WIDTH + 24];
            int alpha = pixel >>> 24;

            require((pixel & 0x00FFFFFF) == 0x0000FF, "marker painted blue, got " + Integer.toHexString(pixel));
            require(alpha >= 120 && alpha <= 135, "marker stays at half opacity over repeated passes, got alpha " + alpha);
        });
        this.screenshot("bbs_fsp_03_marker");

        this.run("reveal the gradient button", 2, (client) -> this.cursor = reveal(client, editorIcon(2, Icons.FADING)));
        this.click();
        this.run("gradient selected", 0, (client) -> require(
            CanvasViewportController.brushes().getPattern() == CanvasBrushPattern.GRADIENT,
            "clicking the gradient button selects the gradient pattern"));
        this.run("reveal the circle button", 2, (client) -> this.cursor = reveal(client, editorIcon(6, Icons.CIRCLE)));
        this.click();
        this.run("circle selected", 5, (client) -> require(
            CanvasViewportController.brushes().getBrush().shape == CanvasBrushShape.CIRCLE,
            "clicking the circle button gives the gradient brush a round shape"));
        this.screenshot("bbs_fsp_04_brush_editor");
        this.run("back to pencil", 0, (client) ->
        {
            CanvasViewportController.brushes().setPattern(CanvasBrushPattern.PENCIL);
            CanvasViewportController.setColor(GREEN);
        });

        this.run("reveal the eyedropper", 2, (client) -> this.cursor = reveal(client, eyedropperButton()));
        this.click();
        this.run("eyedropper armed", 0, (client) -> require(CanvasEyedropper.isArmed(), "the eyedropper button arms the eyedropper"));
        this.run("hover the red line", 10, (client) -> this.move(client, cellToMenu(client, this.clip, 30, 12)));
        this.screenshot("bbs_fsp_05_eyedropper_armed");
        this.click();
        this.run("canvas pick result", 0, (client) ->
        {
            int color = CanvasViewportController.getColor();

            require(!CanvasEyedropper.isArmed(), "the eyedropper puts itself away after a pick");
            require((color & 0x00FFFFFF) == 0xFF0000, "the eyedropper took red from the film preview, got " + Integer.toHexString(color));
            require((color >>> 24) == 0xFF, "a screen pick keeps the paint alpha");
        });

        this.run("reveal the eyedropper again", 2, (client) -> this.cursor = reveal(client, eyedropperButton()));
        this.click();
        this.run("eyedropper armed again", 0, (client) -> require(CanvasEyedropper.isArmed(), "the eyedropper button arms the eyedropper again"));
        this.run("reveal the swatches", 10, (client) -> reveal(client, find(UICanvasColorHistory.class)));
        this.run("hover the yellow swatch", 10, (client) ->
        {
            UICanvasColorHistory history = find(UICanvasColorHistory.class);
            Area area = history.area;
            int size = Math.min(20, Math.max(3, area.w / 10));

            this.move(client, toMenu(area.x + 5 * size + size / 2D, area.y + 24 + size / 2D, history));
            BbsFspMod.LOGGER.info("[in-game check] swatch area {} at menu {},{} of {}x{}", area, this.cursor[0], this.cursor[1],
                client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        });
        this.screenshot("bbs_fsp_06_eyedropper_interface");
        this.click();
        this.run("interface pick result", 0, (client) ->
        {
            int color = CanvasViewportController.getColor();

            require((color & 0x00FFFFFF) == 0xFFFF00, "the eyedropper took the yellow swatch from the panel, got " + Integer.toHexString(color));
        });

        this.run("reset colour", 0, (client) -> CanvasViewportController.setColor(GREEN));
        this.run("reveal the eyedropper for cancel", 2, (client) -> this.cursor = reveal(client, eyedropperButton()));
        this.click();
        this.click();
        this.run("second press cancels", 0, (client) ->
        {
            require(!CanvasEyedropper.isArmed(), "a second press on the button puts the eyedropper away");
            require(CanvasViewportController.getColor() == GREEN, "putting the eyedropper away picks nothing");
        });
        this.click();
        this.run("press Escape", 3, (client) ->
        {
            long window = client.getWindow().getHandle();

            CanvasDevInput.run(() ->
            {
                client.keyboard.onKey(window, GLFW.GLFW_KEY_ESCAPE, 0, GLFW.GLFW_PRESS, 0);
                client.keyboard.onKey(window, GLFW.GLFW_KEY_ESCAPE, 0, GLFW.GLFW_RELEASE, 0);
            });
        });
        this.run("Escape result", 0, (client) ->
        {
            require(!CanvasEyedropper.isArmed(), "Escape puts the eyedropper away");
            require(client.currentScreen instanceof UIScreen, "Escape on the eyedropper keeps the editor open");
            require(CanvasViewportController.getColor() == GREEN, "Escape picks nothing");
        });

        this.run("hover the marker with Alt", 5, (client) ->
        {
            this.expected = layer(this.clip, "foreground").pixels[20 * WIDTH + 24];
            this.move(client, cellToMenu(client, this.clip, 24, 20));
            CanvasDevInput.setAlt(true);
        });
        this.screenshot("bbs_fsp_07_alt_pick");
        this.run("Alt+click", 3, (client) ->
        {
            client.currentScreen.mouseClicked(this.cursor[0], this.cursor[1], 0);
            client.currentScreen.mouseReleased(this.cursor[0], this.cursor[1], 0);
            CanvasDevInput.setAlt(false);
        });
        this.run("Alt+click result", 0, (client) ->
        {
            int color = CanvasViewportController.getColor();

            require(color == this.expected, "Alt+click took the canvas colour with its alpha: expected "
                + Integer.toHexString(this.expected) + ", got " + Integer.toHexString(color));
            require(!CanvasViewportController.isDrawing(), "Alt+click does not start a stroke");
        });

        this.run("render bands", 10, (client) ->
        {
            fill(layer(this.clip, "behind_entities"), 36, 40, 2, 25, 0xFF00FFFF);
            fill(layer(this.clip, "behind_structures"), 41, 45, 2, 25, 0xFFFF00FF);
            this.move(client, new double[] {2, 2});
        });
        this.screenshot("bbs_fsp_08_bands");

        this.run("play the film in the world", 10, (client) ->
        {
            Film film = BBSModClient.getDashboard().getPanel(UIFilmPanel.class).getData();
            List<CanvasFrameData> frames = this.clip.frames.getFrames();

            frames.subList(1, frames.size()).clear();
            client.setScreen(null);
            Films.playFilm(film, false);
        });
        this.run("playback picked up", 2, (client) ->
        {
            List<CanvasComposition.Sample> samples = CanvasPlayback.samples();

            require(BBSModClient.getFilms().getControllers().size() == 1, "the film plays in the world");
            require(samples.size() == 1 && samples.get(0).clip() == this.clip, "the playing film's canvas is picked up for rendering");
            require(near(layer(samples.get(0).frame(), "foreground"), 30, 12, RED), "the played frame carries the painting");
        });
        this.screenshot("bbs_fsp_09_playback");
        this.run("stop the film", 5, (client) -> Films.stopFilm("bbs_fsp_devtest"));
        this.run("playback stopped", 0, (client) -> require(CanvasPlayback.samples().isEmpty(), "nothing is drawn once the film stops"));
        this.run("done", 0, (client) ->
        {
            BbsFspMod.LOGGER.info("[in-game check] all checks passed");
            client.scheduleStop();
        });
    }

    /**
     * The graffiti form: registered, rendered in a film, painted through the pixel art editor
     * over the scene with plain and glowing paint, then in the flat view; settings reaching the
     * copy the film draws, frames added after the current one and walked with the keys and the
     * ruler, and the camera flying with F.
     */
    private void graffiti()
    {
        this.run("graffiti registration", 0, (client) ->
        {
            require(BBSMod.getForms().create(BbsFspMod.GRAFFITI_LINK) instanceof GraffitiForm, "the graffiti form is registered");
            require(FormUtilsClient.getRenderer(new GraffitiForm()) instanceof GraffitiFormRenderer, "the graffiti has its renderer");
        });
        this.run("open the graffiti film", 60, (client) -> this.replay = openGraffitiFilm(client));
        this.run("graffiti on screen", 0, (client) -> require(GraffitiScene.get(GraffitiForm.PIXEL_ART) != null,
            "the selected graffiti is found on screen"));
        this.screenshot("bbs_fsp_graffiti_01_selected");
        this.run("open the graffiti editor", 10, (client) ->
        {
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
            CanvasBrushSet brushes = CanvasViewportController.brushes();

            UIGraffitiEditor.open(BBSModClient.getDashboard().context, GraffitiSession.ofReplay(panel, this.replay));
            brushes.setPattern(CanvasBrushPattern.PENCIL);
            brushes.getBrush().size = 1;
            brushes.setEraser(false);
            brushes.setPrimary(RED);
            brushes.touch();
        });
        this.graffitiDrag(new float[][] {{0.2F, 0.5F}, {0.4F, 0.5F}, {0.6F, 0.5F}, {0.8F, 0.5F}});
        this.run("graffiti stroke", 2, (client) ->
        {
            GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();

            require(session.getFrames().size() == 1, "the first stroke made a pixel art keyframe");
            require(count(session.getPicture().layers.get(0), RED) > 10, "the stroke in the scene painted the graffiti");
        });
        this.run("glowing paint", 0, (client) ->
        {
            for (UIIcon icon : UIGraffitiEditor.getCurrent().getChildren(UIIcon.class))
            {
                if (icon.getIcon() == Icons.SUN)
                {
                    icon.clickItself();

                    break;
                }
            }

            CanvasViewportController.setColor(GREEN);
        });
        this.graffitiDrag(new float[][] {{0.5F, 0.2F}, {0.5F, 0.4F}, {0.5F, 0.6F}, {0.5F, 0.8F}});
        this.run("glowing stroke", 2, (client) ->
        {
            GraffitiPicture picture = UIGraffitiEditor.getCurrent().getSession().getPicture();
            CanvasLayerData glow = picture.layers.stream().filter((layer) -> layer.glow).findFirst().orElse(null);

            require(glow != null && count(glow, GREEN) > 10, "glowing paint went into a glowing layer");
        });
        this.screenshot("bbs_fsp_graffiti_02_glow");
        this.run("flat view", 5, (client) -> pressKey(client, GLFW.GLFW_KEY_V));
        this.run("flat view shown", 0, (client) -> require(UIGraffitiEditor.getCurrent().isFlat(), "V switches to the flat view"));
        this.screenshot("bbs_fsp_graffiti_03_flat");
        this.run("graffiti settings", 0, (client) ->
        {
            GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();
            GraffitiForm live = session.getLiveForm();

            require(live != null && live != session.getForm(), "the film draws its own copy of the graffiti");
            session.setSetting((form) -> form.blur, 2F);
            require(live.blur.get() == 2F && session.getForm().blur.get() == 2F, "a setting reaches the copy the film draws at once");
            session.setSetting((form) -> form.blur, 0F);
        });
        this.run("new frame", 3, (client) -> pressKey(client, GLFW.GLFW_KEY_N));
        this.run("new frame added", 0, (client) ->
        {
            GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();
            List<Keyframe<GraffitiPicture>> frames = session.getFrames();

            require(frames.size() == 2 && frames.get(1).getTick() == 2F, "N on a frame adds the next one a frame length later");
            require(session.getTick() == 2 && session.getCurrentFrame() == frames.get(1), "the cursor goes to the new frame");
            require(session.getPicture().isEmpty() && !session.getPreviousPicture().isEmpty(), "the new frame starts empty");
        });
        this.run("scene view", 5, (client) -> pressKey(client, GLFW.GLFW_KEY_V));
        this.graffitiDrag(new float[][] {{0.2F, 0.2F}, {0.4F, 0.4F}, {0.6F, 0.6F}, {0.8F, 0.8F}});
        this.run("second frame painted", 0, (client) -> require(!UIGraffitiEditor.getCurrent().getSession().getPicture().isEmpty(),
            "the second frame was painted"));
        this.screenshot("bbs_fsp_graffiti_04_second_frame");
        this.run("previous frame", 3, (client) -> pressKey(client, GLFW.GLFW_KEY_COMMA));
        this.run("first frame again", 0, (client) ->
        {
            GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();

            require(session.getTick() == 0 && session.getCurrentFrame() == session.getFrames().get(0), "comma goes to the previous frame");
        });
        this.run("frame in the middle", 3, (client) -> pressKey(client, GLFW.GLFW_KEY_N));
        this.run("frames moved along", 0, (client) ->
        {
            GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();
            List<Keyframe<GraffitiPicture>> frames = session.getFrames();

            require(frames.size() == 3 && frames.get(1).getTick() == 2F && frames.get(2).getTick() == 4F,
                "a frame added in the middle moves the next ones along");
            require(session.getPicture().isEmpty() && !frames.get(2).getValue().isEmpty(), "the painted frame moved, the new one is empty");
        });
        this.run("next frame", 3, (client) -> pressKey(client, GLFW.GLFW_KEY_PERIOD));
        this.run("copy of the frame", 0, (client) ->
        {
            GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();

            require(session.getTick() == 4, "period goes to the next frame");
            session.addFrame(true, 2);

            List<Keyframe<GraffitiPicture>> frames = session.getFrames();

            require(frames.size() == 4 && frames.get(3).getTick() == 6F && session.getTick() == 6, "a copy of the frame goes after it");
        });
        this.run("tick forward", 3, (client) -> pressKey(client, GLFW.GLFW_KEY_RIGHT));
        this.run("tick forward done", 0, (client) -> require(UIGraffitiEditor.getCurrent().getSession().getTick() == 7, "the right arrow steps a tick forward"));
        this.run("tick back", 3, (client) -> pressKey(client, GLFW.GLFW_KEY_LEFT));
        this.run("tick back done", 0, (client) -> require(UIGraffitiEditor.getCurrent().getSession().getTick() == 6, "the left arrow steps a tick back"));
        this.run("move to the ruler", 2, (client) ->
        {
            float[] point = UIGraffitiEditor.getCurrent().getChildren(UIGraffitiFrames.class).get(0).rulerPoint(1);

            this.move(client, new double[] {point[0], point[1]});
        });
        this.run("press on the ruler", 2, (client) -> client.currentScreen.mouseClicked(this.cursor[0], this.cursor[1], 0));
        this.run("release on the ruler", 2, (client) -> client.currentScreen.mouseReleased(this.cursor[0], this.cursor[1], 0));
        this.run("ruler moved the cursor", 0, (client) ->
        {
            GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();

            require(session.getTick() == 1 && session.getCurrentFrame() == session.getFrames().get(0), "a click on the ruler moves the cursor");
        });
        this.screenshot("bbs_fsp_graffiti_05_timeline");
        this.run("start the flight", 3, (client) ->
        {
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

            BBSSettings.editorFlightFreeLook.set(true);
            this.flightStart = new Vector3d(panel.getCamera().position);
            pressKey(client, GLFW.GLFW_KEY_F);
        });
        this.run("flying", 0, (client) ->
        {
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

            require(panel.isFlying(), "F starts the flight");
            require(!panel.dashboard.orbitUI.isFreeLook(), "the flight leaves the pointer to the editor");
            require(UIGraffitiEditor.isOpen() && !UIGraffitiEditor.getCurrent().isFlat(), "the editor stays in the scene while flying");
        });
        this.run("fly forward", 10, (client) -> keyEvent(client, GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS));
        this.run("stop flying forward", 5, (client) -> keyEvent(client, GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE));
        this.screenshot("bbs_fsp_graffiti_06_flight");
        this.run("flew forward", 0, (client) -> require(
            BBSModClient.getDashboard().getPanel(UIFilmPanel.class).getCamera().position.distance(this.flightStart) > 0.5D,
            "W flies the camera forward"));
        this.run("stop the flight", 3, (client) -> pressKey(client, GLFW.GLFW_KEY_F));
        this.run("landed", 0, (client) ->
        {
            require(!BBSModClient.getDashboard().getPanel(UIFilmPanel.class).isFlying(), "F again stops the flight");
            BBSSettings.editorFlightFreeLook.set(false);
        });
        this.run("close the graffiti editor", 5, (client) -> pressKey(client, GLFW.GLFW_KEY_ESCAPE));
        this.run("graffiti editor closed", 0, (client) ->
        {
            require(!UIGraffitiEditor.isOpen(), "Escape closes the editor");
            require(client.currentScreen instanceof UIScreen, "the dashboard stays open");
        });
        this.screenshot("bbs_fsp_graffiti_07_closed");
        this.run("graffiti glow pass", 0, (client) -> CanvasViewportController.setColor(RED));
    }

    private static void pressKey(MinecraftClient client, int key)
    {
        keyEvent(client, key, GLFW.GLFW_PRESS);
        keyEvent(client, key, GLFW.GLFW_RELEASE);
    }

    private static void keyEvent(MinecraftClient client, int key, int action)
    {
        long window = client.getWindow().getHandle();

        CanvasDevInput.run(() -> client.keyboard.onKey(window, key, 0, action, 0));
    }

    private void graffitiDrag(float[][] uv)
    {
        this.run("move to the graffiti stroke start", 2, (client) -> this.move(client, graffitiPoint(uv[0])));
        this.run("press on the graffiti", 2, (client) -> client.currentScreen.mouseClicked(this.cursor[0], this.cursor[1], 0));

        for (int i = 1; i < uv.length; i++)
        {
            float[] point = uv[i];

            this.run("drag on the graffiti", 2, (client) -> this.move(client, graffitiPoint(point)));
        }

        this.run("release on the graffiti", 2, (client) -> client.currentScreen.mouseReleased(this.cursor[0], this.cursor[1], 0));
    }

    private static double[] graffitiPoint(float[] uv)
    {
        UIGraffitiEditor editor = UIGraffitiEditor.getCurrent();

        if (editor.isFlat())
        {
            Area area = editor.getCenter();
            GraffitiPicture picture = editor.getSession().getPicture();
            float size = Math.min(area.w * 0.9F / picture.width, area.h * 0.9F / picture.height);

            return new double[] {area.mx() + (uv[0] - 0.5F) * picture.width * size, area.my() + (uv[1] - 0.5F) * picture.height * size};
        }

        GraffitiScene.Capture capture = GraffitiScene.get(editor.getSession().path);

        require(capture != null, "the graffiti is on screen in the editor");

        float[] point = capture.project(editor.getViewport(), uv[0], uv[1]);

        require(point != null, "the graffiti is in front of the camera");

        return new double[] {point[0], point[1]};
    }

    /** A film with one replay wearing a graffiti, standing in front of a still camera. */
    /**
     * The Blockbench projects named by {@code BBS_FSP_BBMODEL} (a path list), each copied into a model
     * folder of its own, loaded and filmed in a row; skipped when the variable isn't set.
     */
    private void bbmodels()
    {
        String extra = System.getenv("BBS_FSP_BBMODEL");

        if (extra == null || extra.isBlank())
        {
            return;
        }

        List<String> ids = new java.util.ArrayList<>();

        this.run("Blockbench projects", 0, (client) ->
        {
            for (String path : extra.split(java.io.File.pathSeparator))
            {
                java.io.File source = new java.io.File(path);

                if (!source.isFile())
                {
                    continue;
                }

                String name = source.getName();
                String id = "bbs_fsp_extra_" + name.substring(0, name.lastIndexOf('.')).toLowerCase().replaceAll("[^a-z0-9_]", "_");
                java.io.File folder = BBSMod.getAssetsPath("models/" + id);

                folder.mkdirs();

                try
                {
                    java.nio.file.Files.copy(source.toPath(), new java.io.File(folder, name).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                catch (Exception e)
                {
                    throw new AssertionError("could not copy " + path, e);
                }

                mchorse.bbs_mod.cubic.ModelInstance model = BBSModClient.getModels().loadModel(id);

                require(model != null, id + " loads");
                BbsFspMod.LOGGER.info("[in-game check] {}: materials {}, texture {}", id, model.materials, model.getTexture());
                ids.add(id);
            }
        });
        this.run("open the Blockbench film", 60, (client) ->
        {
            skipOnboarding();

            UIDashboard dashboard = BBSModClient.getDashboard();

            UIScreen.open(dashboard);

            UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);
            Film film = new Film();
            IdleClip camera = new IdleClip();
            double x = client.player.getX();
            double y = client.player.getY();
            double z = client.player.getZ();

            dashboard.setPanel(panel);
            film.setId("bbs_fsp_bbmodel_devtest");

            for (int i = 0; i < ids.size(); i++)
            {
                Replay replay = film.replays.addReplay();
                mchorse.bbs_mod.forms.forms.ModelForm form = new mchorse.bbs_mod.forms.forms.ModelForm();

                form.model.set(ids.get(i));
                replay.form.set(form);
                replay.keyframes.x.insert(0, x - 9D + i * 3D);
                replay.keyframes.y.insert(0, y + 1D);
                replay.keyframes.z.insert(0, z - 12D);
            }

            camera.duration.set(200);
            camera.position.set(new Position((float) x, (float) y + 4F, (float) z + 2F, 0F, 10F));
            film.camera.addClip(camera);

            panel.fill(film);
            panel.setCursor(0);
        });
        this.screenshot("bbs_fsp_bbmodel_1204");
        this.run("close the Blockbench film", 5, (client) -> client.setScreen(null));
    }

    private static Replay openGraffitiFilm(MinecraftClient client)
    {
        skipOnboarding();

        UIDashboard dashboard = BBSModClient.getDashboard();

        UIScreen.open(dashboard);

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);
        Film film = new Film();
        Replay replay = film.replays.addReplay();
        GraffitiForm form = new GraffitiForm();
        IdleClip camera = new IdleClip();
        double x = client.player.getX();
        double y = client.player.getY() + 2D;
        double z = client.player.getZ();

        dashboard.setPanel(panel);
        film.setId("bbs_fsp_graffiti_devtest");
        form.transform.get().scale.set(3F, 3F, 3F);
        replay.form.set(form);
        replay.keyframes.x.insert(0, x);
        replay.keyframes.y.insert(0, y);
        replay.keyframes.z.insert(0, z - 4D);
        camera.duration.set(40);
        camera.position.set(new Position((float) x, (float) y, (float) z, 0F, 0F));
        film.camera.addClip(camera);

        panel.fill(film);
        panel.showPanel(panel.replayEditor);
        panel.replayEditor.setReplay(replay);
        panel.setCursor(0);

        return replay;
    }

    private void drag(int[][] cells)
    {
        this.run("move to the stroke start", 2, (client) -> this.move(client, cellToMenu(client, this.clip, cells[0][0], cells[0][1])));
        this.run("press", 2, (client) -> client.currentScreen.mouseClicked(this.cursor[0], this.cursor[1], 0));

        for (int i = 1; i < cells.length; i++)
        {
            int[] cell = cells[i];

            this.run("drag to " + cell[0] + "," + cell[1], 2, (client) -> this.move(client, cellToMenu(client, this.clip, cell[0], cell[1])));
        }

        this.run("release", 2, (client) -> client.currentScreen.mouseReleased(this.cursor[0], this.cursor[1], 0));
    }

    private void click()
    {
        this.run("move to click", 3, (client) -> this.move(client, this.cursor));
        this.run("click", 3, (client) ->
        {
            client.currentScreen.mouseClicked(this.cursor[0], this.cursor[1], 0);
            client.currentScreen.mouseReleased(this.cursor[0], this.cursor[1], 0);
        });
    }

    private void screenshot(String name)
    {
        this.run("screenshot " + name, 2, (client) -> ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png",
            client.getFramebuffer(), (message) -> BbsFspMod.LOGGER.info("[in-game check] screenshot {}: {}", name, message.getString())));
    }

    /** Move the cursor through the game's own handler, given a position in menu coordinates. */
    private void move(MinecraftClient client, double[] menu)
    {
        Window window = client.getWindow();

        this.cursor = menu;
        CanvasDevInput.run(() -> ((MouseInvoker) client.mouse).bbsFspDevtest$onCursorPos(window.getHandle(),
            menu[0] * window.getWidth() / window.getScaledWidth(),
            menu[1] * window.getHeight() / window.getScaledHeight()));
    }

    private static void fill(CanvasLayerData layer, int x1, int x2, int y1, int y2, int color)
    {
        for (int y = y1; y < y2; y++)
        {
            for (int x = x1; x < x2; x++)
            {
                layer.pixels[y * WIDTH + x] = color;
            }
        }

        layer.touch();
    }

    private static boolean pressButton(Screen screen, String key)
    {
        if (!(screen instanceof CreateWorldScreen))
        {
            return false;
        }

        String label = Text.translatable(key).getString();

        for (var element : screen.children())
        {
            if (element instanceof ButtonWidget button && button.active && button.getMessage().getString().equals(label))
            {
                button.onPress();

                return true;
            }
        }

        return false;
    }

    /** The first-run welcome screen and the guided tours would sit over the editor and take the clicks. */
    private static void skipOnboarding()
    {
        Set<String> done = new HashSet<>();

        for (TourChapter chapter : List.of(Tours.DASHBOARD, Tours.FILM, Tours.MORPHING, Tours.MODEL_BLOCKS,
            Tours.PARTICLES, Tours.MODEL_EDITOR, Tours.TEXTURES, Tours.AUDIO))
        {
            done.add(chapter.id());
        }

        BBSSettings.onboardingWelcomeSeen.set(true);
        BBSSettings.onboardingToursDone.set(done);
    }

    private static CanvasClip openEditor(String id, int width, int height)
    {
        skipOnboarding();

        UIDashboard dashboard = BBSModClient.getDashboard();

        UIScreen.open(dashboard);

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);
        Film film = new Film();
        CanvasClip clip = new CanvasClip();

        dashboard.setPanel(panel);
        film.setId(id);
        clip.layer.set(0);
        clip.tick.set(0);
        clip.duration.set(30);
        clip.width.set(width);
        clip.height.set(height);
        clip.ensureValidData();
        film.camera.addClip(clip);

        panel.fill(film);
        panel.showPanel(panel.cameraEditor);
        panel.cameraEditor.pickClip(clip);
        panel.setCursor(0);

        return clip;
    }

    private static UIIcon editorIcon(int index, Object icon)
    {
        UIIcon button = find(UICanvasBrushEditor.class).getChildren(UIIcon.class).get(index);

        require(button.getIcon() == icon, "brush editor button " + index + " has the expected icon");

        return button;
    }

    private static UIIcon eyedropperButton()
    {
        for (UIIcon icon : find(UICanvasClip.class).getChildren(UIIcon.class))
        {
            if (icon.getIcon() == Icons.EYEDROPPER)
            {
                return icon;
            }
        }

        throw new AssertionError("the eyedropper button is in the canvas clip panel");
    }

    private static <T> T find(Class<T> type)
    {
        List<T> found = BBSModClient.getDashboard().getRoot().getChildren(type);

        return found.isEmpty() ? null : found.get(0);
    }

    private static CanvasLayerData layer(CanvasClip clip, String id)
    {
        return layer(clip.getFrameAtLocalTick(0), id);
    }

    private static CanvasLayerData layer(CanvasFrameData frame, String id)
    {
        for (CanvasLayerData layer : frame.layers)
        {
            if (layer.id.equals(id))
            {
                return layer;
            }
        }

        throw new AssertionError("the frame has a layer " + id);
    }

    private static boolean near(CanvasLayerData layer, int x, int y, int color)
    {
        return near(layer, WIDTH, HEIGHT, x, y, color);
    }

    private static boolean near(CanvasLayerData layer, int width, int height, int x, int y, int color)
    {
        for (int dy = -1; dy <= 1; dy++)
        {
            for (int dx = -1; dx <= 1; dx++)
            {
                int cx = x + dx;
                int cy = y + dy;

                if (cx >= 0 && cy >= 0 && cx < width && cy < height && layer.pixels[cy * width + cx] == color)
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static long count(CanvasLayerData layer, int color)
    {
        long count = 0;

        for (int pixel : layer.pixels)
        {
            if (pixel == color)
            {
                count += 1;
            }
        }

        return count;
    }

    private static double[] cellToMenu(MinecraftClient client, CanvasClip clip, int x, int y)
    {
        Area viewport = BBSModClient.getDashboard().getPanel(UIFilmPanel.class).preview.getViewport();

        return CanvasViewportMath.screenAt(x, y, viewport.x, viewport.y, viewport.w, viewport.h,
            clip.width.get(), clip.height.get(), clip.offsetX.get(), clip.offsetY.get(), clip.rotation.get());
    }

    private static double[] reveal(MinecraftClient client, UIElement element)
    {
        for (UIElement parent = element.getParent(); parent != null; parent = parent.getParent())
        {
            if (parent instanceof UIScrollView view && view.scroll.direction == ScrollDirection.VERTICAL)
            {
                view.scroll.setScroll(element.area.y - view.area.y - 20);
                view.scroll.clamp();

                break;
            }
        }

        return toMenu(element.area.mx(), element.area.my(), element);
    }

    private static double[] toMenu(double x, double y, UIElement element)
    {
        for (UIElement parent = element.getParent(); parent != null; parent = parent.getParent())
        {
            if (parent instanceof UIScrollView view)
            {
                if (view.scroll.direction == ScrollDirection.VERTICAL)
                {
                    y -= view.scroll.getScroll();
                }
                else
                {
                    x -= view.scroll.getScroll();
                }
            }
        }

        return new double[] {x, y};
    }

    private static void require(boolean condition, String what)
    {
        if (!condition)
        {
            throw new AssertionError(what);
        }

        BbsFspMod.LOGGER.info("[in-game check] ok: {}", what);
    }
}
