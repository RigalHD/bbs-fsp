package mchorse.bbs_fsp.bbmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.math.molang.MolangParser;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a Blockbench project ({@code .bbmodel}, or an Animated Java {@code .ajblueprint}, which is the
 * same JSON) into a BBS model where every texture of the project is its own material, so each face
 * shows the texture Blockbench gave it.
 *
 * <p>Faces become mesh triangles, one mesh per material in a group (and per material of a rotated
 * cube or a mesh element, which turn about their own pivot). Mesh UVs are normalized into the face's
 * own texture, so textures of any size sit side by side; the model's texture sheet is 1x1. BBS lays
 * models out in Blockbench's own coordinates (its bedrock import flips X there and back), so positions,
 * pivots and rotations carry over as they are.</p>
 */
public final class BBModelParser
{
    /** A texture of the project that some face uses: PNG bytes when embedded, else the file name. */
    public static final class Texture
    {
        public final String material;
        public final byte[] png;
        public final String file;

        private Texture(String material, byte[] png, String file)
        {
            this.material = material;
            this.png = png;
            this.file = file;
        }
    }

    public static final class Result
    {
        public final Model model;
        public final List<Texture> textures;
        public final List<Animation> animations;

        private Result(Model model, List<Texture> textures, List<Animation> animations)
        {
            this.model = model;
            this.textures = textures;
            this.animations = animations;
        }
    }

    /** A project texture as faces see it: its material and the size its UVs are measured in. */
    private record Slot(Texture texture, float uvWidth, float uvHeight)
    {}

    /** Blockbench face → the BBS cube face corners, top-left, top-right, bottom-right, bottom-left (as {@code ModelCube} lays them). */
    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};
    private static final int[][][] CORNERS = {
        /* north */ {{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}},
        /* east  */ {{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}},
        /* south */ {{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}},
        /* west  */ {{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}},
        /* up    */ {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
        /* down  */ {{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}},
    };
    private static final float[][] NORMALS = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private final List<Slot> slots = new ArrayList<>();
    private final Map<String, Slot> slotsById = new HashMap<>();
    private final List<Texture> used = new ArrayList<>();
    private final Map<String, JsonObject> elements = new HashMap<>();
    private final Map<String, JsonObject> groups = new HashMap<>();
    private final Set<String> groupIds = new HashSet<>();
    /** Blockbench group uuid → the id its BBS group got, which animations name their bones by. */
    private final Map<String, String> groupsByUuid = new HashMap<>();
    private final Model model;

    private BBModelParser(MolangParser parser)
    {
        this.model = new Model(parser);
        this.model.textureWidth = 1;
        this.model.textureHeight = 1;
    }

    public static Result parse(Reader reader, MolangParser parser)
    {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        BBModelParser bbmodel = new BBModelParser(parser);

        bbmodel.read(root);

        List<Animation> animations = BBModelAnimations.read(array(root, "animations"), bbmodel.groupsByUuid, parser);

        return new Result(bbmodel.model, bbmodel.used, animations);
    }

    private void read(JsonObject root)
    {
        JsonObject resolution = object(root, "resolution");
        float width = resolution == null ? 16F : size(resolution, "width", 16F);
        float height = resolution == null ? 16F : size(resolution, "height", 16F);

        this.readTextures(array(root, "textures"), width, height);

        for (JsonElement element : array(root, "elements"))
        {
            JsonObject object = element.getAsJsonObject();

            this.elements.put(string(object, "uuid", ""), object);
        }

        /* Blockbench 5 keeps group data apart from the outliner, which then only nests uuids. */
        for (JsonElement group : array(root, "groups"))
        {
            JsonObject object = group.getAsJsonObject();

            this.groups.put(string(object, "uuid", ""), object);
        }

        ModelGroup loose = null;

        for (JsonElement node : array(root, "outliner"))
        {
            if (node.isJsonObject())
            {
                this.model.topGroups.add(this.readGroup(node.getAsJsonObject(), null));
            }
            else
            {
                if (loose == null)
                {
                    loose = this.newGroup("root");
                    this.model.topGroups.add(loose);
                }

                this.readElement(node.getAsString(), loose);
            }
        }

        this.model.topGroups.removeIf((group) -> isEmpty(group));
        this.model.initialize();
    }

    private void readTextures(JsonArray textures, float width, float height)
    {
        Map<String, Texture> byContent = new HashMap<>();
        Set<String> names = new HashSet<>();

        for (int i = 0; i < textures.size(); i++)
        {
            JsonObject texture = textures.get(i).getAsJsonObject();
            String name = string(texture, "name", "texture_" + i);
            String source = string(texture, "source", "");
            byte[] png = null;

            if (source.startsWith("data:") && source.contains(","))
            {
                try
                {
                    png = Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
                }
                catch (IllegalArgumentException e)
                {
                    png = null;
                }
            }

            /* The same picture added twice (Blockbench keeps both) is one material. */
            String key = png != null ? source : "file:" + name;
            Texture shared = byContent.get(key);

            if (shared == null)
            {
                shared = new Texture(unique(materialName(name), names), png, png == null ? name : null);
                byContent.put(key, shared);
            }

            Slot slot = new Slot(shared, size(texture, "uv_width", width), size(texture, "uv_height", height));

            this.slots.add(slot);
            this.slotsById.put(string(texture, "uuid", "\0"), slot);
            this.slotsById.put(string(texture, "id", "\0"), slot);
        }
    }

    private ModelGroup readGroup(JsonObject node, ModelGroup parent)
    {
        JsonObject data = this.groups.getOrDefault(string(node, "uuid", ""), node);
        ModelGroup group = this.newGroup(string(data, "name", "group"));

        this.groupsByUuid.put(string(node, "uuid", ""), group.id);

        vector(data, "origin", group.initial.translate);
        vector(data, "rotation", group.initial.rotate);

        for (JsonElement child : array(node, "children"))
        {
            if (child.isJsonObject())
            {
                ModelGroup childGroup = this.readGroup(child.getAsJsonObject(), group);

                if (!isEmpty(childGroup))
                {
                    group.children.add(childGroup);
                }
            }
            else
            {
                this.readElement(child.getAsString(), group);
            }
        }

        return group;
    }

    private ModelGroup newGroup(String name)
    {
        String id = name.isEmpty() ? "group" : name;
        String unique = id;

        for (int i = 2; !this.groupIds.add(unique); i++)
        {
            unique = id + "_" + i;
        }

        return new ModelGroup(unique);
    }

    private void readElement(String uuid, ModelGroup group)
    {
        JsonObject element = this.elements.get(uuid);

        if (element == null || !bool(element, "export", true))
        {
            return;
        }

        String type = string(element, "type", "cube");

        if (type.equals("cube"))
        {
            this.readCube(element, group);
        }
        else if (type.equals("mesh"))
        {
            this.readMesh(element, group);
        }
    }

    private void readCube(JsonObject cube, ModelGroup group)
    {
        Vector3f from = vector(cube, "from", new Vector3f());
        Vector3f to = vector(cube, "to", new Vector3f());
        Vector3f origin = vector(cube, "origin", new Vector3f());
        Vector3f rotation = vector(cube, "rotation", new Vector3f());
        float inflate = number(cube, "inflate", 0F);
        boolean rotated = rotation.lengthSquared() > 0F;
        Map<String, ModelMesh> meshes = rotated ? new HashMap<>() : null;
        JsonObject faces = object(cube, "faces");

        if (faces == null)
        {
            return;
        }

        Vector3f min = new Vector3f(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)).sub(inflate, inflate, inflate);
        Vector3f max = new Vector3f(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).add(inflate, inflate, inflate);

        for (int f = 0; f < FACES.length; f++)
        {
            JsonObject face = object(faces, FACES[f]);
            Slot slot = face == null ? null : this.slot(face.get("texture"));
            JsonArray uv = face == null ? null : array(face, "uv");

            if (slot == null || uv.size() < 4)
            {
                continue;
            }

            float u1 = uv.get(0).getAsFloat() / slot.uvWidth;
            float v1 = uv.get(1).getAsFloat() / slot.uvHeight;
            float u2 = uv.get(2).getAsFloat() / slot.uvWidth;
            float v2 = uv.get(3).getAsFloat() / slot.uvHeight;
            Vector2f[] uvs = {new Vector2f(u1, v1), new Vector2f(u2, v1), new Vector2f(u2, v2), new Vector2f(u1, v2)};
            int turns = Math.floorMod(Math.round(number(face, "rotation", 0F) / 90F), 4);
            Vector3f[] corners = new Vector3f[4];
            Vector2f[] cornerUVs = new Vector2f[4];

            for (int c = 0; c < 4; c++)
            {
                int[] corner = CORNERS[f][c];

                corners[c] = new Vector3f(corner[0] == 1 ? max.x : min.x, corner[1] == 1 ? max.y : min.y, corner[2] == 1 ? max.z : min.z);
                /* A face turned by 90° shows its bottom-left texel at its top-left corner (BBS's ModelUV agrees). */
                cornerUVs[c] = uvs[Math.floorMod(c - turns, 4)];
            }

            ModelMesh mesh = rotated
                ? meshes.computeIfAbsent(slot.texture.material, (m) -> this.newMesh(group, m, origin, rotation))
                : this.groupMesh(group, slot.texture.material);
            Vector3f normal = new Vector3f(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]);

            /* Same winding as ModelCube's quads: bottom-left, bottom-right, top-right, top-left. */
            triangle(mesh, corners[3], corners[2], corners[1], cornerUVs[3], cornerUVs[2], cornerUVs[1], normal);
            triangle(mesh, corners[3], corners[1], corners[0], cornerUVs[3], cornerUVs[1], cornerUVs[0], normal);
            this.use(slot.texture);
        }
    }

    private void readMesh(JsonObject element, ModelGroup group)
    {
        Vector3f origin = vector(element, "origin", new Vector3f());
        Vector3f rotation = vector(element, "rotation", new Vector3f());
        JsonObject vertices = object(element, "vertices");
        JsonObject faces = object(element, "faces");
        Map<String, ModelMesh> meshes = new HashMap<>();

        if (vertices == null || faces == null)
        {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : faces.entrySet())
        {
            JsonObject face = entry.getValue().getAsJsonObject();
            Slot slot = this.slot(face.get("texture"));
            JsonObject uv = object(face, "uv");
            List<String> keys = new ArrayList<>();

            for (JsonElement key : array(face, "vertices"))
            {
                if (vertices.has(key.getAsString()) && uv != null && uv.has(key.getAsString()))
                {
                    keys.add(key.getAsString());
                }
            }

            if (slot == null || keys.size() < 3)
            {
                continue;
            }

            List<Vector3f> points = new ArrayList<>();
            List<Vector2f> uvs = new ArrayList<>();

            for (String key : keys)
            {
                points.add(vector(vertices, key, new Vector3f()).add(origin));

                JsonArray coords = uv.getAsJsonArray(key);

                uvs.add(new Vector2f(coords.get(0).getAsFloat() / slot.uvWidth, coords.get(1).getAsFloat() / slot.uvHeight));
            }

            if (points.size() == 4 && !convex(points))
            {
                /* Blockbench stores a quad's corners in any order; make them go round the edge. */
                points.add(2, points.remove(3));
                uvs.add(2, uvs.remove(3));
            }

            ModelMesh mesh = meshes.computeIfAbsent(slot.texture.material, (m) -> this.newMesh(group, m, origin, rotation));

            for (int i = 1; i + 1 < points.size(); i++)
            {
                Vector3f a = points.get(0);
                Vector3f b = points.get(i);
                Vector3f c = points.get(i + 1);
                Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();

                if (!normal.isFinite())
                {
                    continue;
                }

                /* Both sides: a mesh face's winding in Blockbench doesn't say which side faces out. */
                triangle(mesh, a, b, c, uvs.get(0), uvs.get(i), uvs.get(i + 1), normal);
                triangle(mesh, a, c, b, uvs.get(0), uvs.get(i + 1), uvs.get(i), new Vector3f(normal).negate());
            }

            this.use(slot.texture);
        }
    }

    private static boolean convex(List<Vector3f> points)
    {
        Vector3f reference = null;

        for (int i = 0; i < 4; i++)
        {
            Vector3f a = points.get(i);
            Vector3f b = points.get((i + 1) % 4);
            Vector3f c = points.get((i + 2) % 4);
            Vector3f cross = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(b));

            if (reference == null)
            {
                reference = cross;
            }
            else if (cross.dot(reference) < 0F)
            {
                return false;
            }
        }

        return true;
    }

    /** The mesh of a group's unrotated faces of a material: one per material, so a group draws in a few calls. */
    private ModelMesh groupMesh(ModelGroup group, String material)
    {
        for (ModelMesh mesh : group.meshes)
        {
            if (mesh.material.equals(material) && mesh.rotate.lengthSquared() == 0F && mesh.origin.lengthSquared() == 0F)
            {
                return mesh;
            }
        }

        return this.newMesh(group, material, new Vector3f(), new Vector3f());
    }

    private ModelMesh newMesh(ModelGroup group, String material, Vector3f origin, Vector3f rotation)
    {
        ModelMesh mesh = new ModelMesh();

        mesh.material = material;
        mesh.origin.set(origin);
        mesh.rotate.set(rotation);
        group.meshes.add(mesh);

        return mesh;
    }

    private static void triangle(ModelMesh mesh, Vector3f a, Vector3f b, Vector3f c, Vector2f ua, Vector2f ub, Vector2f uc, Vector3f normal)
    {
        mesh.baseData.vertices.add(new Vector3f(a));
        mesh.baseData.vertices.add(new Vector3f(b));
        mesh.baseData.vertices.add(new Vector3f(c));
        mesh.baseData.uvs.add(new Vector2f(ua));
        mesh.baseData.uvs.add(new Vector2f(ub));
        mesh.baseData.uvs.add(new Vector2f(uc));

        for (int i = 0; i < 3; i++)
        {
            mesh.baseData.normals.add(new Vector3f(normal));
        }
    }

    private Slot slot(JsonElement texture)
    {
        if (texture == null || texture.isJsonNull() || !texture.isJsonPrimitive())
        {
            return null;
        }

        if (texture.getAsJsonPrimitive().isNumber())
        {
            int index = texture.getAsInt();

            return index >= 0 && index < this.slots.size() ? this.slots.get(index) : null;
        }

        if (texture.getAsJsonPrimitive().isString())
        {
            return this.slotsById.get(texture.getAsString());
        }

        return null;
    }

    private void use(Texture texture)
    {
        if (!this.used.contains(texture))
        {
            this.used.add(texture);
        }
    }

    private static boolean isEmpty(ModelGroup group)
    {
        return group.meshes.isEmpty() && group.cubes.isEmpty() && group.children.isEmpty();
    }

    /** A texture's name as a material, which is also a folder name: lowercase, no extension, safe characters. */
    static String materialName(String name)
    {
        String base = name.toLowerCase(Locale.ROOT);

        if (base.endsWith(".png"))
        {
            base = base.substring(0, base.length() - 4);
        }

        base = base.replaceAll("[^a-z0-9_.-]", "_").replaceAll("^[._]+", "");

        return base.isEmpty() ? "texture" : base;
    }

    private static String unique(String name, Set<String> names)
    {
        String unique = name;

        for (int i = 2; !names.add(unique); i++)
        {
            unique = name + "_" + i;
        }

        return unique;
    }

    static JsonArray array(JsonObject object, String key)
    {
        JsonElement element = object.get(key);

        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    static JsonObject object(JsonObject object, String key)
    {
        JsonElement element = object.get(key);

        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    static String string(JsonObject object, String key, String fallback)
    {
        JsonElement element = object.get(key);

        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    static float number(JsonObject object, String key, float fallback)
    {
        JsonElement element = object.get(key);

        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
        {
            return element.getAsFloat();
        }

        return fallback;
    }

    /** A size: a missing, zero or negative one falls back. */
    private static float size(JsonObject object, String key, float fallback)
    {
        float value = number(object, key, 0F);

        return value > 0F ? value : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback)
    {
        JsonElement element = object.get(key);

        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : fallback;
    }

    private static Vector3f vector(JsonObject object, String key, Vector3f out)
    {
        JsonElement element = object.get(key);

        if (element != null && element.isJsonArray() && element.getAsJsonArray().size() >= 3)
        {
            JsonArray array = element.getAsJsonArray();

            out.set(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
        }

        return out;
    }
}
