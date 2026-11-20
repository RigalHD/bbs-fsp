package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.BbsFspMod;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiRaster;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * Glowing paint as real light, through IRLights (mod id {@code irlite}) when it is installed:
 * every lit cell of the picture registers a point light into IRLights' per-frame registry, the
 * same call its own light forms make, so the colour falls on models and blocks around the
 * graffiti under a patched shader pack. Without IRLights glowing paint is only drawn at full
 * brightness.
 *
 * <p>IRLights is never a dependency: its registry is looked up once by name, and a missing or
 * changed one turns this off instead of breaking the game.</p>
 */
public final class GraffitiLights
{
    private static final String REGISTRY = "org.qualet.irl.light.LightRegistry";
    private static final float FRONT_OFFSET = 0.05F;

    private static MethodHandle registerPoint;
    private static boolean resolved;

    private GraffitiLights()
    {}

    public static boolean isAvailable()
    {
        resolve();

        return registerPoint != null;
    }

    private static void resolve()
    {
        if (resolved)
        {
            return;
        }

        resolved = true;

        if (!FabricLoader.getInstance().isModLoaded("irlite") && !FabricLoader.getInstance().isModLoaded("irl-core"))
        {
            return;
        }

        try
        {
            Class<?> registry = Class.forName(REGISTRY);

            registerPoint = MethodHandles.publicLookup().findStatic(registry, "registerPoint", MethodType.methodType(void.class,
                double.class, double.class, double.class,
                float.class, float.class, float.class,
                float.class, float.class,
                boolean.class, boolean.class,
                float.class, float.class, float.class, float.class,
                boolean.class, long.class));
        }
        catch (ReflectiveOperationException | LinkageError e)
        {
            BbsFspMod.LOGGER.warn("IRLights is installed, but its light registry could not be found; glowing paint won't light the scene", e);
        }
    }

    public static void register(GraffitiForm form, FormRenderingContext context, List<GraffitiRaster.Light> lights,
        float halfWidth, float halfHeight)
    {
        float intensity = GraffitiForm.clamped(form.lightIntensity);
        float radius = GraffitiForm.clamped(form.lightRadius);

        if (lights.isEmpty() || intensity <= 0F || radius <= 0F || context.isPicking() || context.ui
            || BBSRendering.isIrisShadowPass() || !(context.type == FormRenderType.ENTITY || context.type == FormRenderType.MODEL_BLOCK)
            || !isAvailable())
        {
            return;
        }

        Matrix4f world = new Matrix4f().set(GraffitiRenderBridge.inverseViewRotation())
            .mul(GraffitiRenderBridge.modelView())
            .mul(context.stack.peek().getPositionMatrix());
        Vec3d camera = GraffitiRenderBridge.cameraPosition();
        Vector3f normal = world.transformDirection(new Vector3f(0F, 0F, 1F)).normalize();
        Vector3f toCamera = world.transformPosition(new Vector3f()).negate();

        /* The quad is double sided: light goes out of the side the camera looks at. */
        if (normal.dot(toCamera) < 0F)
        {
            normal.negate();
        }

        for (int i = 0; i < lights.size(); i++)
        {
            GraffitiRaster.Light light = lights.get(i);
            Vector3f point = world.transformPosition(new Vector3f(
                (light.u() * 2F - 1F) * halfWidth,
                (1F - light.v() * 2F) * halfHeight,
                0F)).add(normal.x * FRONT_OFFSET, normal.y * FRONT_OFFSET, normal.z * FRONT_OFFSET);

            try
            {
                registerPoint.invoke(
                    camera.x + point.x, camera.y + point.y, camera.z + point.z,
                    light.red(), light.green(), light.blue(),
                    intensity * (0.25F + 0.75F * light.weight()), radius,
                    false, false,
                    0F, 0F, 0F, 0.1F,
                    false, ((long) System.identityHashCode(form) << 8) | i);
            }
            catch (Throwable e)
            {
                BbsFspMod.LOGGER.warn("IRLights refused a graffiti light, glowing paint won't light the scene any more", e);
                registerPoint = null;

                return;
            }
        }
    }
}
