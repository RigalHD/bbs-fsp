package mchorse.bbs_fsp.render;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The world render state a graffiti needs that differs between Minecraft versions: the
 * matrices to find its quad on screen and the camera to find it in the world.
 *
 * <p>1.21.11 keeps no projection matrix on the render system any more; BBS remembers the one
 * the world was rendered with, and the view rotation is the camera's own.</p>
 */
public final class GraffitiRenderBridge
{
    private GraffitiRenderBridge()
    {}

    public static Matrix4f projection()
    {
        return new Matrix4f(BBSRendering.getWorldProjection());
    }

    public static Matrix4f modelView()
    {
        return new Matrix4f(RenderSystem.getModelViewMatrix());
    }

    public static Matrix3f inverseViewRotation()
    {
        return new Matrix3f().rotation(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
    }

    public static Vec3d cameraPosition()
    {
        return MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
    }
}
