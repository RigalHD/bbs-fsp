package mchorse.bbs_fsp.test;

import mchorse.bbs_fsp.BbsFspMod;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.graffiti.GraffitiCycle;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiKeyframeFactory;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.render.GraffitiFormRenderer;
import mchorse.bbs_fsp.render.GraffitiScene;
import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_fsp.ui.graffiti.GraffitiSession;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiFrames;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiEditor;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.onboarding.TourChapter;
import mchorse.bbs_mod.ui.onboarding.Tours;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The graffiti form in a running client: it is in the form palette, renders in a film, opens
 * the pixel art editor over the scene and paints into pixel art keyframes through the cursor
 * cast onto the quad, with plain and glowing paint, in the scene and in the flat view. Settings
 * reach the copy the film draws at once, frames are added after the current one and walked
 * with the keys and the ruler, and the camera flies with F.
 */
public final class GraffitiClientGameTest implements FabricClientGameTest
{
    private static final int RED = 0xFFFF2020;
    private static final int YELLOW = 0xFFFFE020;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        context.runOnClient((client) ->
        {
            Form form = BBSMod.getForms().create(BbsFspMod.GRAFFITI_LINK);

            require(form instanceof GraffitiForm, "the graffiti form is registered in the BBS form factory");
            require(FormUtilsClient.getRenderer(form) instanceof GraffitiFormRenderer, "the graffiti has its renderer");
            require(inPalette(), "the graffiti is in the form palette");
        });

        context.getInput().resizeWindow(1600, 900);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create())
        {
            singleplayer.getClientWorld().waitForChunksRender();

            Replay replay = context.computeOnClient(GraffitiClientGameTest::openFilm);

            context.waitTicks(40);
            context.takeScreenshot("bbs_fsp_graffiti_01_selected");
            context.runOnClient((client) -> require(
                GraffitiScene.get(FormUtils.getPropertyPath(((GraffitiForm) replay.form.get()).pixelArt)) != null,
                "the selected graffiti is found on screen"));

            context.runOnClient((client) ->
            {
                UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

                UIGraffitiEditor.open(BBSModClient.getDashboard().context, GraffitiSession.ofReplay(panel, replay));
                CanvasViewportController.setColor(RED);
            });
            context.waitTicks(10);
            context.runOnClient((client) ->
            {
                UIGraffitiEditor editor = UIGraffitiEditor.getCurrent();
                GraffitiScene.Capture capture = GraffitiScene.get(editor.getSession().path);
                Area viewport = editor.getViewport();
                float[] a = capture.project(viewport, 0F, 0F);
                float[] b = capture.project(viewport, 1F, 1F);

                BbsFspMod.LOGGER.info("[in-game check] graffiti corners {},{} - {},{} in viewport {} {} {} {}",
                    a[0], a[1], b[0], b[1], viewport.x, viewport.y, viewport.w, viewport.h);
                require(viewport.isInside((int) ((a[0] + b[0]) / 2), (int) ((a[1] + b[1]) / 2)), "the graffiti's middle is inside the scene view");
            });
            context.takeScreenshot("bbs_fsp_graffiti_02_editor");

            this.drag(context, new float[][] {{0.2F, 0.5F}, {0.4F, 0.5F}, {0.6F, 0.5F}, {0.8F, 0.5F}});
            context.runOnClient((client) ->
            {
                GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();
                GraffitiPicture picture = session.getPicture();

                require(session.getFrames().size() == 1, "the first stroke made a pixel art keyframe");
                require(count(picture.layers.get(0), RED) > 10, "the stroke in the scene painted the graffiti");
            });
            context.takeScreenshot("bbs_fsp_graffiti_03_stroke");

            context.runOnClient((client) ->
            {
                for (UIIcon icon : UIGraffitiEditor.getCurrent().getChildren(UIIcon.class))
                {
                    if (icon.getIcon() == Icons.SUN)
                    {
                        icon.clickItself();

                        break;
                    }
                }

                CanvasViewportController.setColor(YELLOW);
            });
            this.drag(context, new float[][] {{0.5F, 0.2F}, {0.5F, 0.4F}, {0.5F, 0.6F}, {0.5F, 0.8F}});
            context.runOnClient((client) ->
            {
                GraffitiPicture picture = UIGraffitiEditor.getCurrent().getSession().getPicture();
                CanvasLayerData glow = picture.layers.stream().filter((layer) -> layer.glow).findFirst().orElse(null);

                require(glow != null && count(glow, YELLOW) > 10, "glowing paint went into a glowing layer");
            });
            context.takeScreenshot("bbs_fsp_graffiti_04_glow");

            this.press(context, GLFW.GLFW_KEY_V);
            context.runOnClient((client) -> require(UIGraffitiEditor.getCurrent().isFlat(), "V switches to the flat view"));
            context.takeScreenshot("bbs_fsp_graffiti_05_flat");

            context.runOnClient((client) ->
            {
                GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();
                GraffitiForm live = session.getLiveForm();

                require(live != null && live != session.getForm(), "the film draws its own copy of the graffiti");
                session.setSetting((form) -> form.blur, 2F);
                require(live.blur.get() == 2F && session.getForm().blur.get() == 2F, "a setting reaches the copy the film draws at once");
                session.setSetting((form) -> form.blur, 0F);
            });

            this.press(context, GLFW.GLFW_KEY_N);
            context.runOnClient((client) ->
            {
                GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();
                List<Keyframe<GraffitiPicture>> frames = session.getFrames();

                require(frames.size() == 2 && frames.get(1).getTick() == 2F, "N on a frame adds the next one a frame length later");
                require(session.getTick() == 2 && session.getCurrentFrame() == frames.get(1), "the cursor goes to the new frame");
                require(session.getPicture().isEmpty() && !session.getPreviousPicture().isEmpty(), "the new frame starts empty");
            });
            this.press(context, GLFW.GLFW_KEY_V);
            this.drag(context, new float[][] {{0.2F, 0.2F}, {0.4F, 0.4F}, {0.6F, 0.6F}, {0.8F, 0.8F}});
            context.takeScreenshot("bbs_fsp_graffiti_06_second_frame");
            context.runOnClient((client) -> require(!UIGraffitiEditor.getCurrent().getSession().getPicture().isEmpty(),
                "the second frame was painted"));

            this.press(context, GLFW.GLFW_KEY_COMMA);
            context.runOnClient((client) ->
            {
                GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();

                require(session.getTick() == 0 && session.getCurrentFrame() == session.getFrames().get(0), "comma goes to the previous frame");
            });
            context.waitTicks(5);
            context.takeScreenshot("bbs_fsp_graffiti_07_first_frame_again");

            this.press(context, GLFW.GLFW_KEY_N);
            context.runOnClient((client) ->
            {
                GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();
                List<Keyframe<GraffitiPicture>> frames = session.getFrames();

                require(frames.size() == 3 && frames.get(1).getTick() == 2F && frames.get(2).getTick() == 4F,
                    "a frame added in the middle moves the next ones along");
                require(session.getPicture().isEmpty() && !frames.get(2).getValue().isEmpty(), "the painted frame moved, the new one is empty");
            });

            this.press(context, GLFW.GLFW_KEY_PERIOD);
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            this.press(context, GLFW.GLFW_KEY_N);
            context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            context.runOnClient((client) ->
            {
                GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();
                List<Keyframe<GraffitiPicture>> frames = session.getFrames();

                require(frames.size() == 4 && frames.get(3).getTick() == 6F && session.getTick() == 6, "Shift+N copies the frame after it");
                require(Arrays.equals(frames.get(3).getValue().layers.get(0).pixels, frames.get(2).getValue().layers.get(0).pixels),
                    "the copy has the same paint");
            });

            this.press(context, GLFW.GLFW_KEY_RIGHT);
            context.runOnClient((client) -> require(UIGraffitiEditor.getCurrent().getSession().getTick() == 7, "the right arrow steps a tick forward"));
            this.press(context, GLFW.GLFW_KEY_LEFT);
            context.runOnClient((client) -> require(UIGraffitiEditor.getCurrent().getSession().getTick() == 6, "the left arrow steps a tick back"));

            double[] ruler = context.computeOnClient((client) ->
            {
                UIGraffitiFrames timeline = UIGraffitiEditor.getCurrent().getChildren(UIGraffitiFrames.class).get(0);
                float[] point = timeline.rulerPoint(1);

                return scale(client, point[0], point[1]);
            });

            context.getInput().setCursorPos(ruler[0], ruler[1]);
            context.waitTicks(2);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.runOnClient((client) ->
            {
                GraffitiSession session = UIGraffitiEditor.getCurrent().getSession();

                require(session.getTick() == 1 && session.getCurrentFrame() == session.getFrames().get(0), "a click on the ruler moves the cursor");
            });
            context.takeScreenshot("bbs_fsp_graffiti_08_timeline");

            Vector3d before = context.computeOnClient((client) ->
            {
                UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

                BBSSettings.editorFlightFreeLook.set(true);

                return new Vector3d(panel.getCamera().position);
            });

            this.press(context, GLFW.GLFW_KEY_F);
            context.runOnClient((client) ->
            {
                UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

                require(panel.isFlying(), "F starts the flight");
                require(!panel.dashboard.orbitUI.isFreeLook(), "the flight leaves the pointer to the editor");
                require(UIGraffitiEditor.isOpen() && !UIGraffitiEditor.getCurrent().isFlat(), "the editor stays in the scene while flying");
            });
            context.getInput().holdKey(GLFW.GLFW_KEY_W);
            context.waitTicks(10);
            context.getInput().releaseKey(GLFW.GLFW_KEY_W);
            context.waitTicks(5);
            context.takeScreenshot("bbs_fsp_graffiti_09_flight");
            context.runOnClient((client) ->
            {
                UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

                require(panel.getCamera().position.distance(before) > 0.5D, "W flies the camera forward");
            });
            this.press(context, GLFW.GLFW_KEY_F);
            context.runOnClient((client) ->
            {
                require(!BBSModClient.getDashboard().getPanel(UIFilmPanel.class).isFlying(), "F again stops the flight");
                BBSSettings.editorFlightFreeLook.set(false);
            });

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
            context.runOnClient((client) ->
            {
                require(!UIGraffitiEditor.isOpen(), "Escape closes the editor");
                require(client.currentScreen instanceof UIScreen, "the dashboard stays open");
            });
            context.takeScreenshot("bbs_fsp_graffiti_10_closed");

            /* Copying pixel art keyframes goes through the clipboard as text, the way the keyframe
             * editor's copy and paste do it; the pasted frame keeps its paint. */
            context.runOnClient((client) ->
            {
                UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
                GraffitiSession session = GraffitiSession.ofReplay(panel, panel.replayEditor.getReplay());
                Keyframe<GraffitiPicture> frame = session.getFrames().get(0);
                MapType sheet = new MapType();
                ListType keyframes = new ListType();
                MapType copied = new MapType();

                keyframes.add(frame.toData());
                sheet.putString("type", GraffitiKeyframeFactory.KEY);
                sheet.put("keyframes", keyframes);
                copied.put("pixel_art", sheet);
                mchorse.bbs_mod.graphics.window.Window.setClipboard(copied, "_CopyKeyframes");

                MapType pasted = mchorse.bbs_mod.graphics.window.Window.getClipboardMap("_CopyKeyframes");

                require(pasted != null, "copied pixel art keyframes can be pasted");

                Keyframe<?> back = UIKeyframes.parseKeyframes(pasted).get("pixel_art").keyframes.get(0);

                require(back.getValue() instanceof GraffitiPicture picture
                    && Arrays.equals(picture.layers.get(0).pixels, frame.getValue().layers.get(0).pixels)
                    && !picture.isEmpty(), "the pasted pixel art keeps its paint");

                /* Frames at 0, 2, 4 and 6: the cycle lasts 8 ticks on its own. */
                Form copy = FormUtils.copy(panel.replayEditor.getReplay().form.get());
                GraffitiForm cycled = GraffitiSession.find(copy);
                List<Keyframe<GraffitiPicture>> frames = session.getFrames();

                require(frames.size() == 4 && frames.get(3).getTick() == 6F, "the film has four frames for the cycle checks");
                cycled.cycleMode.set(GraffitiCycle.LOOP);
                panel.replayEditor.getReplay().properties.applyProperties(copy, 9F);
                require(cycled.pixelArt.get().equals(frames.get(0).getValue()), "a looped graffiti starts over after its last frame");
                cycled.cycleLength.set(4);
                panel.replayEditor.getReplay().properties.applyProperties(copy, 5F);
                require(cycled.pixelArt.get().equals(frames.get(0).getValue()), "a looped graffiti starts over after its length");
                cycled.cycleLength.set(0);
                cycled.cycleMode.set(GraffitiCycle.HOLD);
                panel.replayEditor.getReplay().properties.applyProperties(copy, 30F);
                require(cycled.pixelArt.get().equals(frames.get(3).getValue()), "a held graffiti keeps its last frame");
                cycled.cycleMode.set(GraffitiCycle.ONCE);
                panel.replayEditor.getReplay().properties.applyProperties(copy, 7F);
                require(cycled.pixelArt.get().equals(frames.get(3).getValue()), "a graffiti played once shows its last frame to the end");
                panel.replayEditor.getReplay().properties.applyProperties(copy, 8F);
                require(cycled.pixelArt.get() == GraffitiCycle.ENDED, "a graffiti played once is gone after it");
            });
        }
    }

    private static boolean inPalette()
    {
        for (FormCategory category : BBSModClient.getFormCategories().getAllCategories())
        {
            for (Form form : category.getForms())
            {
                if (form instanceof GraffitiForm)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /** A film with one replay wearing a graffiti, standing in front of a still camera. */
    private static Replay openFilm(MinecraftClient client)
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
        film.setId("bbs_fsp_graffiti_gametest");
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

    private void press(ClientGameTestContext context, int key)
    {
        context.getInput().pressKey(key);
        context.waitTicks(3);
    }

    /** Drags the mouse across the graffiti, the points in parts of the picture (0..1, v down). */
    private void drag(ClientGameTestContext context, float[][] uv)
    {
        double[][] points = context.computeOnClient((client) ->
        {
            UIGraffitiEditor editor = UIGraffitiEditor.getCurrent();
            double[][] result = new double[uv.length][];

            for (int i = 0; i < uv.length; i++)
            {
                result[i] = editor.isFlat() ? flatPoint(client, editor, uv[i]) : scenePoint(client, editor, uv[i]);
            }

            return result;
        });

        context.getInput().setCursorPos(points[0][0], points[0][1]);
        context.waitTicks(2);
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);

        for (int i = 1; i < points.length; i++)
        {
            context.getInput().setCursorPos(points[i][0], points[i][1]);
            context.waitTicks(2);
        }

        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static double[] scenePoint(MinecraftClient client, UIGraffitiEditor editor, float[] uv)
    {
        GraffitiScene.Capture capture = GraffitiScene.get(editor.getSession().path);

        require(capture != null, "the graffiti is on screen in the editor");

        float[] point = capture.project(editor.getViewport(), uv[0], uv[1]);

        require(point != null, "the graffiti is in front of the camera");

        return scale(client, point[0], point[1]);
    }

    private static double[] flatPoint(MinecraftClient client, UIGraffitiEditor editor, float[] uv)
    {
        Area area = editor.getCenter();
        GraffitiPicture picture = editor.getSession().getPicture();
        float size = Math.min(area.w * 0.9F / picture.width, area.h * 0.9F / picture.height);

        return scale(client, area.mx() + (uv[0] - 0.5F) * picture.width * size, area.my() + (uv[1] - 0.5F) * picture.height * size);
    }

    private static long count(CanvasLayerData layer, int color)
    {
        long count = 0;

        for (int pixel : layer.pixels)
        {
            if (pixel == color)
            {
                count++;
            }
        }

        return count;
    }

    static void skipOnboarding()
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

    private static double[] scale(MinecraftClient client, double x, double y)
    {
        Window window = client.getWindow();

        return new double[] {
            x * window.getWidth() / window.getScaledWidth(),
            y * window.getHeight() / window.getScaledHeight()
        };
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
