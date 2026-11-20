package mchorse.bbs_fsp.bbmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static mchorse.bbs_fsp.bbmodel.BBModelParser.array;
import static mchorse.bbs_fsp.bbmodel.BBModelParser.number;
import static mchorse.bbs_fsp.bbmodel.BBModelParser.object;
import static mchorse.bbs_fsp.bbmodel.BBModelParser.string;

/**
 * The animations of a Blockbench project as BBS model animations.
 *
 * <p>Blockbench keeps keyframe values the way bedrock files write them (X of positions, X and Y of
 * rotations mirrored), which is also what BBS reads from bedrock animations and mirrors back when it
 * moves the bones, so values go across as they are, Molang included. Seconds become ticks. A segment
 * between two keyframes moves the way Blockbench moves it: held after a step keyframe, Catmull-Rom
 * when either end is, else Bézier with the keyframes' handles when either end is, else linear. A
 * keyframe with separate values before and after it becomes two keyframes a hair apart.</p>
 */
final class BBModelAnimations
{
    private static final String[] AXES = {"x", "y", "z"};

    /** How far apart (ticks) the "before" and "after" keyframes of a split keyframe are. */
    private static final float SPLIT = 0.001F;

    private static final float[] LEFT_TIME = {-0.1F, -0.1F, -0.1F};
    private static final float[] RIGHT_TIME = {0.1F, 0.1F, 0.1F};
    private static final float[] NO_VALUE = {0F, 0F, 0F};

    private BBModelAnimations()
    {}

    static List<Animation> read(JsonArray animations, Map<String, String> groups, MolangParser parser)
    {
        List<Animation> result = new ArrayList<>();
        Set<String> names = new HashSet<>();

        for (JsonElement element : animations)
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            Animation animation = new Animation(unique(string(object, "name", "animation"), names), parser);
            JsonObject animators = object(object, "animators");
            float end = 0F;

            if (animators != null)
            {
                for (Map.Entry<String, JsonElement> entry : animators.entrySet())
                {
                    String group = groups.get(entry.getKey());

                    if (group == null || !entry.getValue().isJsonObject())
                    {
                        continue;
                    }

                    JsonObject animator = entry.getValue().getAsJsonObject();

                    if (!string(animator, "type", "bone").equals("bone"))
                    {
                        continue;
                    }

                    AnimationPart part = new AnimationPart(parser);
                    JsonArray keyframes = array(animator, "keyframes");

                    end = Math.max(end, readChannel(parser, keyframes, "position", 0D, part.x, part.y, part.z));
                    end = Math.max(end, readChannel(parser, keyframes, "rotation", 0D, part.rx, part.ry, part.rz));
                    end = Math.max(end, readChannel(parser, keyframes, "scale", 1D, part.sx, part.sy, part.sz));

                    if (part.channels.stream().anyMatch((channel) -> !channel.isEmpty()))
                    {
                        animation.parts.put(group, part);
                    }
                }
            }

            float length = number(object, "length", 0F);

            animation.setLength(length > 0F ? length : end);
            result.add(animation);
        }

        return result;
    }

    /** Fills one channel (position, rotation or scale) of a bone; returns its last keyframe's time in seconds. */
    @SafeVarargs
    private static float readChannel(MolangParser parser, JsonArray keyframes, String channel, double fallback, KeyframeChannel<MolangExpression>... axes)
    {
        List<JsonObject> list = new ArrayList<>();

        for (JsonElement element : keyframes)
        {
            if (element.isJsonObject() && string(element.getAsJsonObject(), "channel", "").equals(channel) && array(element.getAsJsonObject(), "data_points").size() > 0)
            {
                list.add(element.getAsJsonObject());
            }
        }

        list.sort(Comparator.comparingDouble((keyframe) -> number(keyframe, "time", 0F)));

        @SuppressWarnings("unchecked")
        Keyframe<MolangExpression>[] last = new Keyframe[3];
        JsonObject previous = null;
        float end = 0F;

        for (JsonObject keyframe : list)
        {
            float time = number(keyframe, "time", 0F);
            JsonArray points = array(keyframe, "data_points");
            JsonObject before = points.get(0).getAsJsonObject();
            JsonObject after = points.get(points.size() - 1).getAsJsonObject();
            IInterp interp = previous == null ? Interpolations.LINEAR : segment(previous, keyframe);

            for (int a = 0; a < 3; a++)
            {
                Keyframe<MolangExpression> inserted = insert(axes[a], time * 20F, value(parser, before, AXES[a], fallback), interp);

                if (interp == Interpolations.BEZIER && last[a] != null)
                {
                    last[a].rx = handle(previous, "bezier_right_time", RIGHT_TIME)[a] * 20F;
                    last[a].ry = handle(previous, "bezier_right_value", NO_VALUE)[a];
                    inserted.lx = -handle(keyframe, "bezier_left_time", LEFT_TIME)[a] * 20F;
                    inserted.ly = handle(keyframe, "bezier_left_value", NO_VALUE)[a];
                }

                last[a] = points.size() > 1
                    ? insert(axes[a], time * 20F + SPLIT, value(parser, after, AXES[a], fallback), Interpolations.LINEAR)
                    : inserted;
            }

            previous = keyframe;
            end = Math.max(end, time);
        }

        return end;
    }

    /** How Blockbench moves from one keyframe to the next. */
    private static IInterp segment(JsonObject from, JsonObject to)
    {
        String a = string(from, "interpolation", "linear");
        String b = string(to, "interpolation", "linear");

        if (a.equals("step"))
        {
            return Interpolations.CONST;
        }

        if (a.equals("catmullrom") || b.equals("catmullrom"))
        {
            return Interpolations.HERMITE;
        }

        if (a.equals("bezier") || b.equals("bezier"))
        {
            return Interpolations.BEZIER;
        }

        return Interpolations.LINEAR;
    }

    private static Keyframe<MolangExpression> insert(KeyframeChannel<MolangExpression> channel, float tick, MolangExpression value, IInterp interp)
    {
        Keyframe<MolangExpression> keyframe = channel.get(channel.insert(tick, value));

        keyframe.getInterpolation().setInterp(interp);

        return keyframe;
    }

    /** A number, or Molang text; blank or broken text is the channel's resting value. */
    static MolangExpression value(MolangParser parser, JsonObject point, String axis, double fallback)
    {
        JsonElement element = point.get(axis);
        double number = fallback;

        if (element != null && element.isJsonPrimitive())
        {
            if (element.getAsJsonPrimitive().isNumber())
            {
                number = element.getAsDouble();
            }
            else
            {
                String text = element.getAsString().trim();

                try
                {
                    number = text.isEmpty() ? fallback : Double.parseDouble(text);
                }
                catch (NumberFormatException e)
                {
                    try
                    {
                        return parser.parseExpression(text);
                    }
                    catch (Exception broken)
                    {
                        number = fallback;
                    }
                }
            }
        }

        return new MolangValue(parser, new Constant(number));
    }

    private static float[] handle(JsonObject keyframe, String key, float[] fallback)
    {
        JsonArray array = array(keyframe, key);

        if (array.size() < 3)
        {
            return fallback;
        }

        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static String unique(String name, Set<String> names)
    {
        String unique = name.isEmpty() ? "animation" : name;
        String base = unique;

        for (int i = 2; !names.add(unique); i++)
        {
            unique = base + "_" + i;
        }

        return unique;
    }
}
