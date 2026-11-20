package mchorse.bbs_fsp.test;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;

/**
 * A Blockbench project dropped into a model folder: the folder is in the model list, the model
 * loads with one material per texture, the embedded textures land in the material folders, its
 * animation plays, and the model draws in a film. A project named by {@code BBS_FSP_BBMODEL} (a real one, say) is filmed too.
 */
public final class BBModelClientGameTest implements FabricClientGameTest
{
    private static final String MODEL = "bbs_fsp_bbmodel";
    /** Projects named by {@code BBS_FSP_BBMODEL} (a path list), each in a model folder of its own. */
    private static final List<String> EXTRA = new ArrayList<>();

    private static IdleClip camera;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        context.runOnClient((client) ->
        {
            File folder = BBSMod.getAssetsPath("models/" + MODEL);

            deleteRecursively(folder);
            folder.mkdirs();

            try
            {
                Files.writeString(new File(folder, MODEL + ".bbmodel").toPath(), project(), StandardCharsets.UTF_8);
            }
            catch (Exception e)
            {
                throw new AssertionError("could not write the test project", e);
            }

            EXTRA.clear();

            String extra = System.getenv("BBS_FSP_BBMODEL");

            for (String path : extra == null ? new String[0] : extra.split(File.pathSeparator))
            {
                File source = new File(path);

                if (!source.isFile())
                {
                    continue;
                }

                String name = source.getName();
                String id = "bbs_fsp_extra_" + name.substring(0, name.lastIndexOf('.')).toLowerCase().replaceAll("[^a-z0-9_]", "_");
                File extraFolder = BBSMod.getAssetsPath("models/" + id);

                deleteRecursively(extraFolder);
                extraFolder.mkdirs();

                try
                {
                    Files.copy(source.toPath(), new File(extraFolder, name).toPath());
                }
                catch (Exception e)
                {
                    throw new AssertionError("could not copy " + path, e);
                }

                EXTRA.add(id);
            }

            ModelManager models = BBSModClient.getModels();

            require(models.getAvailableKeys().contains(MODEL), "a folder with a .bbmodel is in the model list");

            ModelInstance model = models.loadModel(MODEL);

            require(model != null, "the Blockbench project loads as a model");
            require(model.materials.equals(List.of("red", "blue")), "every texture of the project is a material");
            require(new File(folder, "textures/red/red.png").isFile() && new File(folder, "textures/blue/blue.png").isFile(), "embedded textures are written into the material folders");
            require(model.getMaterialTexture("blue", null).path.endsWith("textures/blue/blue.png"), "the blue material shows the blue picture");
            require(model.animations.get("idle") != null && model.animations.get("idle").parts.containsKey("block"), "the project's animation is the model's");

            for (String id : EXTRA)
            {
                ModelInstance loaded = models.loadModel(id);

                require(loaded != null, id + " loads");
                System.out.println("[in-game check] " + id + ": materials " + loaded.materials + ", texture " + loaded.getTexture() + ", animations " + loaded.animations.getAll().stream().map((a) -> a.id).toList());
            }
        });

        context.getInput().resizeWindow(1600, 900);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create())
        {
            singleplayer.getClientWorld().waitForChunksRender();

            context.runOnClient(BBModelClientGameTest::openFilm);

            context.waitTicks(60);
            context.takeScreenshot("bbs_fsp_bbmodel_01_film");
            /* Playing, the replays tick, and "idle" turns the block a quarter in 10 ticks. */
            context.runOnClient((client) -> panel().togglePlayback());
            context.waitTicks(10);
            context.runOnClient((client) -> panel().togglePlayback());
            context.waitTicks(3);
            context.takeScreenshot("bbs_fsp_bbmodel_01b_spun");

            /* Closer, from the front corner: each face has its own picture. */
            context.runOnClient((client) -> camera.position.set(new Position((float) client.player.getX() + 7F, (float) client.player.getY() + 4F, (float) client.player.getZ() - 8F, 30F, 20F)));
            context.waitTicks(20);
            context.takeScreenshot("bbs_fsp_bbmodel_02_close");

            context.runOnClient((client) -> UIScreen.open(null));
            context.waitTicks(5);
        }
    }

    private static void openFilm(MinecraftClient client)
    {
        GraffitiClientGameTest.skipOnboarding();

        UIDashboard dashboard = BBSModClient.getDashboard();

        UIScreen.open(dashboard);

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);
        Film film = new Film();
        camera = new IdleClip();
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();

        dashboard.setPanel(panel);
        film.setId("bbs_fsp_bbmodel_gametest");
        addModel(film, MODEL, x + 2.5D, y + 1D, z - 5D, 1.5F);

        for (int i = 0; i < EXTRA.size(); i++)
        {
            /* A row behind the test block, left to right. */
            addModel(film, EXTRA.get(i), x - 9D + i * 3D, y + 1D, z - 12D, 1F);
        }

        camera.duration.set(200);
        camera.position.set(new Position((float) x, (float) y + 4F, (float) z + 2F, 0F, 10F));
        film.camera.addClip(camera);

        panel.fill(film);
        panel.setCursor(0);

    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
    }

    private static void addModel(Film film, String model, double x, double y, double z, float scale)
    {
        Replay replay = film.replays.addReplay();
        ModelForm form = new ModelForm();

        form.model.set(model);
        form.transform.get().scale.set(scale, scale, scale);
        replay.form.set(form);
        replay.keyframes.x.insert(0, x);
        replay.keyframes.y.insert(0, y);
        replay.keyframes.z.insert(0, z);
    }

    /* A block: red on north, south, up and down, blue (a 32 px picture) on east and west; "idle" spins it. */
    private static String project()
    {
        String red = png(0xFFE02020, 16);
        String blue = png(0xFF2040E0, 32);
        String faces = """
            "north": {"uv": [0, 0, 16, 16], "texture": 0},
            "south": {"uv": [0, 0, 16, 16], "texture": 0},
            "up": {"uv": [0, 0, 16, 16], "texture": 0},
            "down": {"uv": [0, 0, 16, 16], "texture": 0},
            "east": {"uv": [0, 0, 32, 32], "texture": 1},
            "west": {"uv": [0, 0, 32, 32], "texture": 1}
            """;

        return """
            {
              "meta": {"format_version": "4.10", "model_format": "free", "box_uv": false},
              "resolution": {"width": 16, "height": 16},
              "textures": [
                {"name": "red.png", "id": "0", "uuid": "t0", "uv_width": 16, "uv_height": 16, "source": "%s"},
                {"name": "blue.png", "id": "1", "uuid": "t1", "uv_width": 32, "uv_height": 32, "source": "%s"}
              ],
              "elements": [
                {"type": "cube", "uuid": "c", "from": [-8, 0, -8], "to": [8, 16, 8], "origin": [0, 0, 0], "faces": {%s}}
              ],
              "outliner": [{"name": "block", "uuid": "g", "origin": [0, 0, 0], "children": ["c"]}],
              "animations": [{"name": "idle", "loop": "loop", "length": 2, "animators": {"g": {"name": "block", "type": "bone", "keyframes": [
                {"channel": "rotation", "time": 0, "interpolation": "linear", "data_points": [{"x": 0, "y": 0, "z": 0}]},
                {"channel": "rotation", "time": 2, "interpolation": "linear", "data_points": [{"x": 0, "y": 360, "z": 0}]}
              ]}}}]
            }
            """.formatted(red, blue, faces);
    }

    private static String png(int argb, int size)
    {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                /* A darker frame shows where each face's picture ends. */
                boolean edge = i == 0 || j == 0 || i == size - 1 || j == size - 1;

                image.setRGB(i, j, edge ? argb & 0xFF7F7F7F : argb);
            }
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            ImageIO.write(image, "png", out);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private static void deleteRecursively(File file)
    {
        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children)
            {
                deleteRecursively(child);
            }
        }

        file.delete();
    }

    private static void require(boolean condition, String what)
    {
        if (!condition)
        {
            throw new AssertionError("In-game check failed: " + what);
        }

        System.out.println("[in-game check] ok: " + what);
    }
}
