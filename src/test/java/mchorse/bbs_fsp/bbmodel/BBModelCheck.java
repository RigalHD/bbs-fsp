package mchorse.bbs_fsp.bbmodel;

import mchorse.bbs_mod.cubic.CubicModelAnimator;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.StringReader;

public final class BBModelCheck
{
    private static final String RED = "data:image/png;base64,AAEC";
    private static final String BLUE = "data:image/png;base64,AwQF";

    /*
     * Three textures, the third a copy of the first. A group "body" (pivot 0,8,0, turned 45° on Y)
     * holds a block-sized cube with red on north and south and a turned blue face on east, a small
     * blue cube turned about its own pivot, and an empty group named "body" too. A loose cube sits
     * at the top level.
     */
    private static final String PROJECT = """
        {
          "meta": {"format_version": "4.10", "model_format": "free", "box_uv": false},
          "resolution": {"width": 16, "height": 16},
          "textures": [
            {"name": "Red.png", "id": "0", "uuid": "t0", "uv_width": 16, "uv_height": 16, "source": "%s"},
            {"name": "blue.png", "id": "1", "uuid": "t1", "uv_width": 32, "uv_height": 32, "source": "%s"},
            {"name": "Red.png", "id": "2", "uuid": "t2", "uv_width": 16, "uv_height": 16, "source": "%s"}
          ],
          "elements": [
            {"type": "cube", "uuid": "a", "from": [0, 0, 0], "to": [16, 16, 16], "origin": [0, 0, 0], "faces": {
              "north": {"uv": [0, 0, 16, 16], "texture": 0},
              "east": {"uv": [0, 0, 32, 32], "texture": 1, "rotation": 90},
              "south": {"uv": [0, 0, 16, 16], "texture": 2},
              "west": {"uv": [0, 0, 0, 0], "texture": null},
              "up": {"uv": [0, 0, 16, 16], "texture": null}
            }},
            {"type": "cube", "uuid": "b", "from": [4, 4, 4], "to": [12, 12, 12], "origin": [8, 8, 8], "rotation": [0, 0, 22.5], "faces": {
              "north": {"uv": [0, 0, 8, 8], "texture": "t1"}
            }},
            {"type": "cube", "uuid": "c", "from": [0, 0, 0], "to": [1, 1, 1], "faces": {"down": {"uv": [0, 0, 1, 1], "texture": 0}}},
            {"type": "cube", "uuid": "hidden", "export": false, "from": [0, 0, 0], "to": [1, 1, 1], "faces": {"down": {"uv": [0, 0, 1, 1], "texture": 0}}}
          ],
          "outliner": [
            {"name": "body", "uuid": "g1", "origin": [0, 8, 0], "rotation": [0, 45, 0], "children": [
              "a", "b", "hidden",
              {"name": "body", "uuid": "g2", "origin": [0, 0, 0], "children": []}
            ]},
            "c"
          ],
          "animations": [
            {"name": "wave", "loop": "loop", "length": 1, "animators": {
              "g1": {"name": "body", "type": "bone", "keyframes": [
                {"channel": "rotation", "time": 0, "interpolation": "linear", "data_points": [{"x": 0, "y": "0", "z": 0}]},
                {"channel": "rotation", "time": 0.5, "interpolation": "catmullrom", "data_points": [{"x": "10", "y": "2 + 3", "z": ""}]},
                {"channel": "rotation", "time": 1, "interpolation": "linear", "data_points": [{"x": 0, "y": 0, "z": 0}]},
                {"channel": "position", "time": 0, "interpolation": "step", "data_points": [{"x": 1, "y": 2, "z": 3}]},
                {"channel": "position", "time": 0.5, "interpolation": "linear", "data_points": [{"x": 4, "y": 4, "z": 4}, {"x": 5, "y": 5, "z": 5}]},
                {"channel": "scale", "time": 0.25, "interpolation": "bezier", "bezier_right_time": [0.05, 0.05, 0.05], "bezier_right_value": [0, 0, 0], "data_points": [{"x": 2, "y": 2, "z": 2}]},
                {"channel": "scale", "time": 0.75, "interpolation": "linear", "bezier_left_time": [-0.2, -0.2, -0.2], "bezier_left_value": [0.5, 0.5, 0.5], "data_points": [{"x": "", "y": 1, "z": 1}]},
                {"channel": "timeline", "time": 0.1, "data_points": [{"script": "say hi"}]}
              ]},
              "nobody": {"name": "gone", "type": "bone", "keyframes": [{"channel": "rotation", "time": 0, "data_points": [{"x": 1, "y": 1, "z": 1}]}]},
              "effects": {"name": "Effects", "type": "effect", "keyframes": []}
            }},
            {"name": "wave", "animators": {}}
          ]
        }
        """.formatted(RED, BLUE, RED);

    public static void main(String[] args) throws Exception
    {
        checkNames();
        checkProject();
        checkAnimations();

        /* Given project files, also say what they turn into. */
        for (String arg : args)
        {
            try
            {
                describe(java.nio.file.Path.of(arg));
            }
            catch (Exception e)
            {
                System.out.println(arg + ": FAILED " + e);
            }
        }
    }

    private static void describe(java.nio.file.Path path) throws Exception
    {
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path))
        {
            BBModelParser.Result result = BBModelParser.parse(reader, new MolangParser());
            int meshes = 0;
            int triangles = 0;

            for (ModelGroup group : result.model.getOrderedGroups())
            {
                meshes += group.meshes.size();

                for (ModelMesh mesh : group.meshes)
                {
                    triangles += mesh.baseData.vertices.size() / 3;
                }
            }

            System.out.println(path.getFileName() + ": " + result.model.getOrderedGroups().size() + " groups, "
                + meshes + " meshes, " + triangles + " triangles, " + result.textures.size() + " materials "
                + result.textures.stream().map((t) -> t.material + (t.png == null ? "(file " + t.file + ")" : "")).toList()
                + ", animations " + result.animations.stream().map((a) -> a.id + a.parts.keySet()).toList());
        }
    }

    private static void checkNames()
    {
        require(BBModelParser.materialName("Red.png").equals("red"));
        require(BBModelParser.materialName("My Tex!.PNG").equals("my_tex_"));
        require(BBModelParser.materialName("..png").equals("texture"));
    }

    private static void checkProject()
    {
        BBModelParser.Result result = BBModelParser.parse(new StringReader(PROJECT), new MolangParser());
        Model model = result.model;

        /* The copy of red is red; unused textures aren't materials. */
        require(result.textures.size() == 2);
        require(result.textures.get(0).material.equals("red") && result.textures.get(0).png.length == 3);
        require(result.textures.get(1).material.equals("blue"));
        require(model.textureWidth == 1 && model.textureHeight == 1);

        /* The empty "body" goes; the loose cube gets a group of its own. */
        require(model.topGroups.size() == 2);

        ModelGroup body = model.topGroups.get(0);
        ModelGroup root = model.topGroups.get(1);

        require(body.id.equals("body") && root.id.equals("root") && body.children.isEmpty());
        require(body.initial.translate.equals(new Vector3f(0, 8, 0)) && body.initial.rotate.equals(new Vector3f(0, 45, 0)));
        require(model.getGroup("body") == body);

        /* Body: one red mesh (north + south), one blue mesh (east), the turned cube's blue mesh. */
        require(body.meshes.size() == 3);

        ModelMesh red = body.meshes.get(0);
        ModelMesh blue = body.meshes.get(1);
        ModelMesh turned = body.meshes.get(2);

        require(red.material.equals("red") && red.baseData.vertices.size() == 12);
        require(blue.material.equals("blue") && blue.baseData.vertices.size() == 6);
        require(turned.material.equals("blue") && turned.rotate.equals(new Vector3f(0, 0, 22.5F)) && turned.origin.equals(new Vector3f(8, 8, 8)));
        require(red.baseData.normals.size() == 12 && red.baseData.uvs.size() == 12);

        /* North starts at its bottom-left corner (seen from the north: +X side), with the texture's bottom-left. */
        require(red.baseData.vertices.get(0).equals(new Vector3f(16, 0, 0)));
        require(red.baseData.uvs.get(0).equals(new Vector2f(0, 1)));
        require(red.baseData.vertices.get(2).equals(new Vector3f(0, 16, 0)) && red.baseData.uvs.get(2).equals(new Vector2f(1, 0)));
        require(red.baseData.normals.get(0).equals(new Vector3f(0, 0, -1)));

        /* East is turned 90°: its bottom-left corner shows the texture's bottom-right (UVs measured in 32). */
        require(blue.baseData.vertices.get(0).equals(new Vector3f(16, 0, 16)));
        require(blue.baseData.uvs.get(0).equals(new Vector2f(1, 1)));
        require(blue.baseData.normals.get(0).equals(new Vector3f(1, 0, 0)));

        /* The loose cube's bottom face, and nothing of the cube that isn't exported. */
        require(root.meshes.size() == 1 && root.meshes.get(0).baseData.vertices.size() == 6);
        require(root.meshes.get(0).baseData.normals.get(0).equals(new Vector3f(0, -1, 0)));
    }

    private static void checkAnimations()
    {
        BBModelParser.Result result = BBModelParser.parse(new StringReader(PROJECT), new MolangParser());

        /* Two animations (the second name made unique); only the bone that exists is animated. */
        require(result.animations.size() == 2);

        Animation wave = result.animations.get(0);

        require(wave.id.equals("wave") && result.animations.get(1).id.equals("wave_2"));
        require(wave.getLength() == 1D && wave.parts.keySet().equals(java.util.Set.of("body")));

        AnimationPart body = wave.parts.get("body");

        /* Rotation: seconds are ticks, Molang works, blank is the resting value, Catmull-Rom on both sides of its keyframe. */
        require(ticks(body.rx).equals(java.util.List.of(0F, 10F, 20F)));
        require(body.rx.get(1).getValue().get() == 10D && body.ry.get(1).getValue().get() == 5D && body.rz.get(1).getValue().get() == 0D);
        require(body.rx.get(1).getInterpolation().getInterp() == Interpolations.HERMITE && body.rx.get(2).getInterpolation().getInterp() == Interpolations.HERMITE);

        /* Position: held after a step keyframe; a keyframe with a value before and after it is split in two. */
        require(ticks(body.x).equals(java.util.List.of(0F, 10F, 10F + 0.001F)));
        require(body.x.get(1).getInterpolation().getInterp() == Interpolations.CONST);
        require(at(body, 5F).x == 1D && at(body, 10F).x == 4D && at(body, 15F).x == 5D);

        /* Scale: Bézier with Blockbench's handles in ticks, blank scale is 1. */
        Keyframe<MolangExpression> from = body.sx.get(0);
        Keyframe<MolangExpression> to = body.sx.get(1);

        require(to.getInterpolation().getInterp() == Interpolations.BEZIER && to.getValue().get() == 1D);
        require(Math.abs(from.rx - 1F) < 1e-4F && from.ry == 0F && Math.abs(to.lx - 4F) < 1e-4F && to.ly == 0.5F);

        /* No animations: none. */
        String still = "{\"elements\": [], \"outliner\": []}";

        require(BBModelParser.parse(new StringReader(still), new MolangParser()).animations.isEmpty());
    }

    private static java.util.List<Float> ticks(KeyframeChannel<MolangExpression> channel)
    {
        return channel.getKeyframes().stream().map(Keyframe::getTick).toList();
    }

    private static Vector3d at(AnimationPart part, float tick)
    {
        return CubicModelAnimator.interpolateList(new Vector3d(), part.x, part.y, part.z, tick, 0D);
    }

    private static void require(boolean condition)
    {
        if (!condition)
        {
            throw new AssertionError("Blockbench model check failed");
        }
    }
}
