package mchorse.bbs_fsp.test;

import mchorse.bbs_fsp.BbsFspMod;
import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.canvas.CanvasComposition;
import mchorse.bbs_fsp.canvas.CanvasFrameData;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasViewportMath;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushPattern;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushSet;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushShape;
import mchorse.bbs_fsp.render.CanvasPlayback;
import mchorse.bbs_fsp.ui.CanvasEyedropper;
import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_fsp.ui.UICanvasBrushEditor;
import mchorse.bbs_fsp.ui.UICanvasClip;
import mchorse.bbs_fsp.ui.UICanvasColorHistory;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.onboarding.TourChapter;
import mchorse.bbs_mod.ui.onboarding.Tours;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens the film editor on a canvas clip in a real client and drives it through simulated
 * input: painting, the brush editor, the screen eyedropper and Alt-picking from the canvas.
 */
public final class CanvasClientGameTest implements FabricClientGameTest
{
    private static final int WIDTH = 48;
    private static final int HEIGHT = 27;
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        context.runOnClient((client) -> require(
            BBSMod.getFactoryCameraClips().getTypeClass(BbsFspMod.CANVAS_LINK) == CanvasClip.class,
            "the canvas clip is registered in the BBS camera clip factory"));

        context.getInput().resizeWindow(1600, 900);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create())
        {
            singleplayer.getClientWorld().waitForChunksRender();

            CanvasClip clip = context.computeOnClient((client) -> openEditor("bbs_fsp_gametest", WIDTH, HEIGHT));

            context.waitTicks(40);
            context.runOnClient((client) ->
            {
                require(client.currentScreen instanceof UIScreen, "the BBS dashboard is open");
                require(find(UICanvasClip.class) != null, "the canvas clip panel is shown");
                require(find(UICanvasBrushEditor.class) != null, "the brush editor is shown");
            });
            context.takeScreenshot("bbs_fsp_01_editor");

            this.pencil(context, clip);
            this.marker(context, clip);
            this.brushEditor(context);
            this.eyedropperOnCanvas(context, clip);
            this.eyedropperOnInterface(context);
            this.eyedropperCancel(context);
            this.altPick(context, clip);
            this.bands(context, clip);
            this.playback(context, clip);
        }

        this.serverRoundTrip(context);
    }

    /**
     * The film on a dedicated server: painted in the editor while connected, saved through BBS's
     * network repository, checked on the server's disk, then loaded back over the network after
     * reconnecting. A full-size canvas so the transfer is the size a real film sends.
     */
    private void serverRoundTrip(ClientGameTestContext context)
    {
        String id = "bbs_fsp_server_film";
        int width = CanvasClip.DEFAULT_WIDTH;
        int height = CanvasClip.DEFAULT_HEIGHT;

        try (TestDedicatedServerContext server = context.worldBuilder().createServer())
        {
            try (TestServerConnection connection = server.connect())
            {
                connection.getClientWorld().waitForChunksRender();

                CanvasClip clip = context.computeOnClient((client) -> openEditor(id, width, height));

                context.waitTicks(40);
                context.runOnClient((client) ->
                {
                    CanvasBrushSet brushes = CanvasViewportController.brushes();

                    brushes.setPattern(CanvasBrushPattern.PENCIL);
                    brushes.getBrush().size = 2;
                    brushes.setEraser(false);
                    brushes.setPrimary(RED);
                    brushes.touch();
                    CanvasViewportController.selectLayer("foreground");
                });
                this.drag(context, clip, new int[][] {{20, 20}, {90, 40}});
                context.runOnClient((client) ->
                {
                    require(near(layer(clip, "foreground"), width, height, 90, 40, RED), "painted while connected to a dedicated server");

                    for (CanvasFrameData frame : clip.frames.getFrames())
                    {
                        layer(frame, "behind_structures").pixels[width * height - 1] = 0xFFFF00FF;
                    }

                    BBSModClient.getDashboard().getPanel(UIFilmPanel.class).forceSave();
                });
                context.waitTicks(60);
                context.takeScreenshot("bbs_fsp_10_server_editor");

                boolean saved = server.computeOnServer((minecraft) ->
                {
                    Film film = BBSMod.getFilms().load(id);

                    if (film == null || film.camera.get().isEmpty() || !(film.camera.get().get(0) instanceof CanvasClip stored))
                    {
                        return false;
                    }

                    return near(layer(stored.getFrameAtLocalTick(0), "foreground"), width, height, 90, 40, RED)
                        && layer(stored.frames.getFrames().get(stored.frames.getFrames().size() - 1), "behind_structures").pixels[width * height - 1] == 0xFFFF00FF;
                });

                require(saved, "the server stored the painted film it received over the network");
                context.runOnClient((client) -> client.setScreen(null));
                context.waitTicks(5);
            }

            try (TestServerConnection connection = server.connect())
            {
                connection.getClientWorld().waitForChunksDownload();

                AtomicReference<Object> received = new AtomicReference<>();

                context.runOnClient((client) -> ContentType.FILMS.getRepository().load(id, received::set));
                context.waitFor((client) -> received.get() != null, 400);
                context.runOnClient((client) ->
                {
                    require(received.get() instanceof Film, "the film came back from the server after reconnecting");

                    Film film = (Film) received.get();

                    require(film.camera.get().size() == 1 && film.camera.get().get(0) instanceof CanvasClip, "the film from the server still has its canvas clip");

                    CanvasClip loaded = (CanvasClip) film.camera.get().get(0);

                    require(loaded.width.get() == width && loaded.height.get() == height, "the canvas size came back");
                    require(near(layer(loaded, "foreground"), width, height, 90, 40, RED), "the painting came back from the server");
                    require(layer(loaded.frames.getFrames().get(loaded.frames.getFrames().size() - 1), "behind_structures").pixels[width * height - 1] == 0xFFFF00FF,
                        "every frame came back from the server");
                });
            }
        }
    }

    private void pencil(ClientGameTestContext context, CanvasClip clip)
    {
        context.runOnClient((client) ->
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

        this.drag(context, clip, new int[][] {{6, 6}, {20, 12}, {30, 12}});

        context.runOnClient((client) ->
        {
            CanvasLayerData layer = layer(clip, "foreground");

            require(!CanvasViewportController.isDrawing(), "the stroke ends when the button is released");
            require(near(layer, 6, 6, RED), "pencil painted its first cell");
            require(near(layer, 20, 12, RED), "pencil reached the second point");
            require(near(layer, 30, 12, RED), "pencil reached the last point");
            require(count(layer, RED) >= 25, "pencil left no gaps between the points");
        });
        context.waitTicks(5);
        context.takeScreenshot("bbs_fsp_02_pencil");
    }

    private void marker(ClientGameTestContext context, CanvasClip clip)
    {
        context.runOnClient((client) ->
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

        this.drag(context, clip, new int[][] {{8, 20}, {40, 20}, {8, 20}, {40, 20}});

        context.runOnClient((client) ->
        {
            int pixel = layer(clip, "foreground").pixels[20 * WIDTH + 24];
            int alpha = pixel >>> 24;

            require((pixel & 0x00FFFFFF) == 0x0000FF, "marker painted blue, got " + Integer.toHexString(pixel));
            require(alpha >= 120 && alpha <= 135, "marker stays at half opacity over repeated passes, got alpha " + alpha);
        });
        context.waitTicks(5);
        context.takeScreenshot("bbs_fsp_03_marker");
    }

    private void brushEditor(ClientGameTestContext context)
    {
        double[] gradient = context.computeOnClient((client) ->
        {
            List<UIIcon> icons = find(UICanvasBrushEditor.class).getChildren(UIIcon.class);

            require(icons.get(2).getIcon() == Icons.FADING, "third pattern button is the gradient");

            return reveal(client, icons.get(2));
        });

        this.click(context, gradient);
        context.runOnClient((client) -> require(
            CanvasViewportController.brushes().getPattern() == CanvasBrushPattern.GRADIENT,
            "clicking the gradient button selects the gradient pattern"));

        double[] circle = context.computeOnClient((client) ->
            reveal(client, find(UICanvasBrushEditor.class).getChildren(UIIcon.class).get(6)));

        this.click(context, circle);
        context.runOnClient((client) -> require(
            CanvasViewportController.brushes().getBrush().shape == CanvasBrushShape.CIRCLE,
            "clicking the circle button gives the gradient brush a round shape"));
        context.waitTicks(5);
        context.takeScreenshot("bbs_fsp_04_brush_editor");

        context.runOnClient((client) ->
        {
            CanvasBrushSet brushes = CanvasViewportController.brushes();

            brushes.setPattern(CanvasBrushPattern.PENCIL);
            brushes.setPrimary(GREEN);
        });
    }

    private void eyedropperOnCanvas(ClientGameTestContext context, CanvasClip clip)
    {
        double[] button = context.computeOnClient((client) -> reveal(client, eyedropperButton()));

        this.click(context, button);
        context.runOnClient((client) -> require(CanvasEyedropper.isArmed(), "the eyedropper button arms the eyedropper"));

        double[] cell = context.computeOnClient((client) -> cellToWindow(client, clip, 30, 12));

        context.getInput().setCursorPos(cell[0], cell[1]);
        context.waitTicks(10);
        context.takeScreenshot("bbs_fsp_05_eyedropper_armed");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);

        context.runOnClient((client) ->
        {
            int color = CanvasViewportController.getColor();

            require(!CanvasEyedropper.isArmed(), "the eyedropper puts itself away after a pick");
            require((color & 0x00FFFFFF) == 0xFF0000, "the eyedropper took red from the film preview, got " + Integer.toHexString(color));
            require((color >>> 24) == 0xFF, "a screen pick keeps the paint alpha");
            require(near(layer(clip, "foreground"), 30, 12, RED), "the pick did not paint on the canvas");
        });
    }

    private void eyedropperOnInterface(ClientGameTestContext context)
    {
        double[] button = context.computeOnClient((client) -> reveal(client, eyedropperButton()));

        this.click(context, button);

        context.runOnClient((client) -> require(CanvasEyedropper.isArmed(), "the eyedropper button arms the eyedropper again"));
        context.runOnClient((client) -> reveal(client, find(UICanvasColorHistory.class)));
        context.waitTicks(10);

        double[] swatch = context.computeOnClient((client) ->
        {
            UICanvasColorHistory history = find(UICanvasColorHistory.class);
            Area area = history.area;
            int size = Math.min(20, Math.max(3, area.w / 10));
            Area cell = new Area(area.x + 5 * size, area.y + 24, size, size);

            return toWindow(client, cell.mx(), cell.my(), history);
        });

        context.getInput().setCursorPos(swatch[0], swatch[1]);
        context.waitTicks(10);
        context.takeScreenshot("bbs_fsp_06_eyedropper_interface");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);

        context.runOnClient((client) ->
        {
            int color = CanvasViewportController.getColor();

            require((color & 0x00FFFFFF) == 0xFFFF00, "the eyedropper took the yellow swatch from the panel, got " + Integer.toHexString(color));
        });
    }

    private void eyedropperCancel(ClientGameTestContext context)
    {
        context.runOnClient((client) -> CanvasViewportController.setColor(GREEN));

        double[] button = context.computeOnClient((client) -> reveal(client, eyedropperButton()));

        this.click(context, button);
        this.click(context, button);
        context.runOnClient((client) ->
        {
            require(!CanvasEyedropper.isArmed(), "a second press on the button puts the eyedropper away");
            require(CanvasViewportController.getColor() == GREEN, "putting the eyedropper away picks nothing");
        });

        this.click(context, button);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(3);
        context.runOnClient((client) ->
        {
            require(!CanvasEyedropper.isArmed(), "Escape puts the eyedropper away");
            require(client.currentScreen instanceof UIScreen, "Escape on the eyedropper keeps the editor open");
            require(CanvasViewportController.getColor() == GREEN, "Escape picks nothing");
        });
    }

    private void altPick(ClientGameTestContext context, CanvasClip clip)
    {
        double[] cell = context.computeOnClient((client) -> cellToWindow(client, clip, 24, 20));
        int expected = context.computeOnClient((client) -> layer(clip, "foreground").pixels[20 * WIDTH + 24]);

        context.getInput().setCursorPos(cell[0], cell[1]);
        context.waitTicks(3);
        context.getInput().holdAlt();
        context.waitTicks(3);
        context.takeScreenshot("bbs_fsp_07_alt_pick");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.getInput().releaseAlt();

        context.runOnClient((client) ->
        {
            int color = CanvasViewportController.getColor();

            require(color == expected, "Alt+click took the canvas colour with its alpha: expected "
                + Integer.toHexString(expected) + ", got " + Integer.toHexString(color));
            require(!CanvasViewportController.isDrawing(), "Alt+click does not start a stroke");
        });
    }

    /**
     * Columns on the two world bands that cross the horizon: the one behind entities has to cover
     * the ground as well as the sky, the one behind structures only the sky.
     */
    private void bands(ClientGameTestContext context, CanvasClip clip)
    {
        context.runOnClient((client) ->
        {
            fill(layer(clip, "behind_entities"), 36, 40, 2, 25, 0xFF00FFFF);
            fill(layer(clip, "behind_structures"), 41, 45, 2, 25, 0xFFFF00FF);
        });
        context.getInput().setCursorPos(5, 5);
        context.waitTicks(10);
        context.takeScreenshot("bbs_fsp_08_bands");
    }

    /**
     * The same film played in the world with the editor closed: the canvas is drawn by the
     * runtime pass for a film controller, not by the editor's preview.
     */
    private void playback(ClientGameTestContext context, CanvasClip clip)
    {
        context.runOnClient((client) ->
        {
            Film film = BBSModClient.getDashboard().getPanel(UIFilmPanel.class).getData();
            List<CanvasFrameData> frames = clip.frames.getFrames();

            frames.subList(1, frames.size()).clear();
            client.setScreen(null);
            Films.playFilm(film, false);
        });
        context.waitTicks(10);
        context.runOnClient((client) ->
        {
            List<CanvasComposition.Sample> samples = CanvasPlayback.samples();

            require(BBSModClient.getFilms().getControllers().size() == 1, "the film plays in the world");
            require(samples.size() == 1 && samples.get(0).clip() == clip, "the playing film's canvas is picked up for rendering");
            require(near(layer(samples.get(0).frame(), "foreground"), 30, 12, RED), "the played frame carries the painting");
        });
        context.takeScreenshot("bbs_fsp_09_playback");
        context.runOnClient((client) -> Films.stopFilm("bbs_fsp_gametest"));
        context.waitTicks(5);
        context.runOnClient((client) -> require(CanvasPlayback.samples().isEmpty(), "nothing is drawn once the film stops"));
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

    private void drag(ClientGameTestContext context, CanvasClip clip, int[][] cells)
    {
        double[][] points = context.computeOnClient((client) ->
        {
            double[][] result = new double[cells.length][];

            for (int i = 0; i < cells.length; i++)
            {
                result[i] = cellToWindow(client, clip, cells[i][0], cells[i][1]);
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

    private void click(ClientGameTestContext context, double[] point)
    {
        context.getInput().setCursorPos(point[0], point[1]);
        context.waitTicks(3);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
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

    /** Whether the cell or one of its neighbours has the colour: the cursor lands on whole interface units. */
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

    private static double[] cellToWindow(MinecraftClient client, CanvasClip clip, int x, int y)
    {
        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
        Area viewport = panel.preview.getViewport();
        double[] point = CanvasViewportMath.screenAt(x, y, viewport.x, viewport.y, viewport.w, viewport.h,
            clip.width.get(), clip.height.get(), clip.offsetX.get(), clip.offsetY.get(), clip.rotation.get());

        return scale(client, point[0], point[1]);
    }

    /** Scroll the element into view and return its centre in window coordinates. */
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

        return toWindow(client, element.area.mx(), element.area.my(), element);
    }

    private static double[] toWindow(MinecraftClient client, double x, double y, UIElement element)
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

        return scale(client, x, y);
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
