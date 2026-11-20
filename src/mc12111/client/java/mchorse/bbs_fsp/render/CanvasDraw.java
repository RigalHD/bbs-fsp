package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Interface drawing that differs between Minecraft versions: the canvas transform on the 2D
 * interface matrix stack and textured layer quads recorded into the deferred interface.
 */
public final class CanvasDraw
{
    private CanvasDraw()
    {}

    /**
     * Move the interface into canvas space: offset from the viewport centre, then rotated
     * around it. Drawing the viewport rectangle afterwards places the canvas where it renders.
     */
    public static void pushCanvasTransform(Batcher2D batcher, float centerX, float centerY,
        float offsetX, float offsetY, float degrees)
    {
        Matrix3x2fStack matrices = batcher.getContext().getMatrices();

        matrices.pushMatrix();
        matrices.translate(centerX + offsetX, centerY + offsetY);
        matrices.rotate((float) Math.toRadians(degrees));
        matrices.translate(-centerX, -centerY);
    }

    public static void popTransform(Batcher2D batcher)
    {
        batcher.getContext().getMatrices().popMatrix();
    }

    public static void layer(Batcher2D batcher, CanvasLayerData layer, int width, int height, int color,
        float x, float y, float w, float h)
    {
        if ((color >>> 24) == 0)
        {
            return;
        }

        Identifier id = CanvasTextureCache.SHARED.get(layer, width, height);

        batcher.getContext().drawTexture(RenderPipelines.GUI_TEXTURED, id, Math.round(x), Math.round(y), 0F, 0F,
            Math.round(w), Math.round(h), width, height, width, height, color);
    }

    /** A straight line of the given thickness, drawn as a turned box along its right side. */
    public static void line(Batcher2D batcher, float x1, float y1, float x2, float y2, float thickness, int color)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 0.01F)
        {
            return;
        }

        Matrix3x2fStack matrices = batcher.getContext().getMatrices();

        matrices.pushMatrix();
        matrices.translate(x1, y1);
        matrices.rotate((float) Math.atan2(dy, dx));
        batcher.box(0F, 0F, length, thickness, color);
        matrices.popMatrix();
    }

    public static void clearTextures()
    {
        CanvasTextureCache.SHARED.clear();
    }
}
