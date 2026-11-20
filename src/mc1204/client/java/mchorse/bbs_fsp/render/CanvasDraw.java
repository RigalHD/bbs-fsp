package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Interface drawing that differs between Minecraft versions: the canvas transform on the
 * interface matrix stack and textured layer quads.
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
        MatrixStack matrices = batcher.getContext().getMatrices();

        matrices.push();
        matrices.translate(centerX + offsetX, centerY + offsetY, 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        matrices.translate(-centerX, -centerY, 0F);
    }

    public static void popTransform(Batcher2D batcher)
    {
        batcher.getContext().getMatrices().pop();
    }

    public static void layer(Batcher2D batcher, CanvasLayerData layer, int width, int height, int color,
        float x, float y, float w, float h)
    {
        Texture texture = CanvasTextureCache.SHARED.get(layer, width, height);

        batcher.texturedBox(texture, color, x, y, w, h, 0, 0, texture.width, texture.height,
            texture.width, texture.height);
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

        MatrixStack matrices = batcher.getContext().getMatrices();

        matrices.push();
        matrices.translate(x1, y1, 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        batcher.box(0F, 0F, length, thickness, color);
        matrices.pop();
    }

    public static void clearTextures()
    {
        CanvasTextureCache.SHARED.clear();
    }
}
