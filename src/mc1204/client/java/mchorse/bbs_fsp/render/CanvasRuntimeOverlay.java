package mchorse.bbs_fsp.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_fsp.canvas.CanvasComposition;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasRenderBand;
import mchorse.bbs_fsp.canvas.CanvasViewportMath;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.List;

/**
 * Draws the canvas layers into the frame being rendered: the two world bands in the world pass,
 * the foreground band right before BBS copies the frame for the preview and the export.
 *
 * <p>Both passes work in the pixels of whatever target is bound (the GL viewport). In the film
 * editor the world renders into an export-sized buffer while the interface keeps the window
 * size, so the window's scaled size would stretch a rotated canvas.</p>
 */
public final class CanvasRuntimeOverlay
{
    private CanvasRuntimeOverlay()
    {}

    public static void register()
    {
        WorldRenderEvents.BEFORE_ENTITIES.register((context) -> renderWorldBands());
    }

    public static void render()
    {
        List<CanvasComposition.Sample> samples = CanvasPlayback.samples();

        if (!samples.isEmpty())
        {
            renderPass(samples, false);
        }
    }

    public static void renderWorldBands()
    {
        List<CanvasComposition.Sample> samples = CanvasPlayback.samples();

        if (!samples.isEmpty())
        {
            renderPass(samples, true);
        }
    }

    private static void renderPass(List<CanvasComposition.Sample> samples, boolean world)
    {
        int width;
        int height;

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer viewport = stack.mallocInt(4);

            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            width = viewport.get(2);
            height = viewport.get(3);
        }

        if (width <= 0 || height <= 0)
        {
            return;
        }

        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorting = RenderSystem.getVertexSorting();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, width, height, 0, -1, 1), VertexSorter.BY_Z);
        RenderSystem.depthMask(false);

        try
        {
            if (world)
            {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                renderSamples(samples, CanvasRenderBand.BEHIND_STRUCTURES, width, height, -1F);

                RenderSystem.disableDepthTest();
                renderSamples(samples, CanvasRenderBand.BEHIND_ENTITIES, width, height, 0F);
            }
            else
            {
                RenderSystem.disableDepthTest();
                renderSamples(samples, CanvasRenderBand.FOREGROUND, width, height, 0F);
            }
        }
        finally
        {
            MatrixStackUtils.restoreMatrices();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.depthMask(depthWrite);

            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();

            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
        }
    }

    /**
     * {@code depth} -1 puts the quad on the far plane: with the depth test on, only pixels nothing
     * solid was drawn into (sky, background) let it through, so structures stay in front of it.
     */
    private static void renderSamples(List<CanvasComposition.Sample> samples,
        CanvasRenderBand band, int width, int height, float depth)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        Batcher2D batcher = new Batcher2D(context);
        MatrixStack matrices = batcher.getContext().getMatrices();
        float centerX = width / 2F;
        float centerY = height / 2F;

        for (CanvasComposition.Sample sample : samples)
        {
            int canvasWidth = sample.clip().width.get();
            int canvasHeight = sample.clip().height.get();

            matrices.push();
            matrices.translate(
                centerX + (float) CanvasViewportMath.offsetToArea(sample.clip().offsetX.get(), width, canvasWidth),
                centerY + (float) CanvasViewportMath.offsetToArea(sample.clip().offsetY.get(), height, canvasHeight),
                depth);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sample.clip().rotation.get()));
            matrices.translate(-centerX, -centerY, 0F);

            try
            {
                for (CanvasLayerData layer : CanvasComposition.layersForBand(sample.frame(), band))
                {
                    if (layer.pixels.length != canvasWidth * canvasHeight)
                    {
                        continue;
                    }

                    CanvasDraw.layer(batcher, layer, canvasWidth, canvasHeight, Colors.setA(Colors.WHITE, layer.opacity),
                        0, 0, width, height);
                }
            }
            finally
            {
                matrices.pop();
            }
        }
    }
}
