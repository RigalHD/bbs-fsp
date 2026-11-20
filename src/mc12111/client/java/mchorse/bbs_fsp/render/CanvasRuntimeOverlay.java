package mchorse.bbs_fsp.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_fsp.BbsFspMod;
import mchorse.bbs_fsp.canvas.CanvasComposition;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasRenderBand;
import mchorse.bbs_fsp.canvas.CanvasViewportMath;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the canvas layers into the frame being rendered: the two world bands in the world pass,
 * the foreground band right before BBS copies the frame for the preview and the export.
 *
 * <p>1.21.11 has no global shader state to borrow, so every band draws through a pipeline of
 * its own under an orthographic projection in the pixels of the target the frame renders into
 * (the export-sized buffer in the film editor, the window otherwise). The quad behind structures
 * sits on the far plane with the depth test on, so only empty sky lets it through.</p>
 */
public final class CanvasRuntimeOverlay
{
    private static final Map<String, RenderLayer> LAYERS = new HashMap<>();

    private static RenderPipeline depthTested;
    private static RenderPipeline overlay;
    private static RawProjectionMatrix projection;

    private CanvasRuntimeOverlay()
    {}

    public static void register()
    {
        WorldRenderEvents.BEFORE_ENTITIES.register((context) -> renderWorldBands());
        CanvasTextureCache.SHARED.onRemove((id) -> LAYERS.keySet().removeIf((key) -> key.endsWith("|" + id)));
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
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

        if (framebuffer == null || framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0)
        {
            return;
        }

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;

        if (projection == null)
        {
            projection = new RawProjectionMatrix(BbsFspMod.MOD_ID + "_canvas");
        }

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousType = RenderSystem.getProjectionType();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();

        RenderSystem.setProjectionMatrix(projection.set(new Matrix4f().setOrtho(0, width, height, 0, -1, 1)),
            ProjectionType.ORTHOGRAPHIC);
        modelView.pushMatrix();
        modelView.identity();

        try
        {
            if (world)
            {
                renderSamples(samples, CanvasRenderBand.BEHIND_STRUCTURES, width, height, -1F);
                renderSamples(samples, CanvasRenderBand.BEHIND_ENTITIES, width, height, 0F);
            }
            else
            {
                renderSamples(samples, CanvasRenderBand.FOREGROUND, width, height, 0F);
            }
        }
        finally
        {
            modelView.popMatrix();

            if (previousProjection != null)
            {
                RenderSystem.setProjectionMatrix(previousProjection, previousType);
            }
        }
    }

    private static void renderSamples(List<CanvasComposition.Sample> samples, CanvasRenderBand band,
        int width, int height, float depth)
    {
        float centerX = width / 2F;
        float centerY = height / 2F;

        for (CanvasComposition.Sample sample : samples)
        {
            int canvasWidth = sample.clip().width.get();
            int canvasHeight = sample.clip().height.get();
            float offsetX = (float) CanvasViewportMath.offsetToArea(sample.clip().offsetX.get(), width, canvasWidth);
            float offsetY = (float) CanvasViewportMath.offsetToArea(sample.clip().offsetY.get(), height, canvasHeight);
            double angle = Math.toRadians(sample.clip().rotation.get());
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            for (CanvasLayerData layer : CanvasComposition.layersForBand(sample.frame(), band))
            {
                if (layer.pixels.length != canvasWidth * canvasHeight || layer.opacity <= 0F)
                {
                    continue;
                }

                Identifier texture = CanvasTextureCache.SHARED.get(layer, canvasWidth, canvasHeight);
                int color = Colors.setA(Colors.WHITE, layer.opacity);
                BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                vertex(builder, 0, 0, 0F, 0F, centerX, centerY, offsetX, offsetY, cos, sin, depth, color);
                vertex(builder, 0, height, 0F, 1F, centerX, centerY, offsetX, offsetY, cos, sin, depth, color);
                vertex(builder, width, height, 1F, 1F, centerX, centerY, offsetX, offsetY, cos, sin, depth, color);
                vertex(builder, width, 0, 1F, 0F, centerX, centerY, offsetX, offsetY, cos, sin, depth, color);

                BuiltBuffer built = builder.endNullable();

                if (built != null)
                {
                    layer(band, texture).draw(built);
                }
            }
        }
    }

    private static void vertex(BufferBuilder builder, float x, float y, float u, float v, float centerX, float centerY,
        float offsetX, float offsetY, float cos, float sin, float depth, int color)
    {
        float localX = x - centerX;
        float localY = y - centerY;

        builder.vertex(centerX + offsetX + localX * cos - localY * sin, centerY + offsetY + localX * sin + localY * cos, depth)
            .texture(u, v)
            .color(color);
    }

    private static RenderLayer layer(CanvasRenderBand band, Identifier texture)
    {
        boolean tested = band == CanvasRenderBand.BEHIND_STRUCTURES;
        String key = (tested ? "tested" : "overlay") + "|" + texture;

        return LAYERS.computeIfAbsent(key, (k) -> RenderLayer.of(
            BbsFspMod.MOD_ID + "_canvas_" + (tested ? "tested_" : "overlay_") + texture.getPath().replace('/', '_'),
            RenderSetup.builder(tested ? depthTestedPipeline() : overlayPipeline())
                .texture("Sampler0", texture)
                .build()));
    }

    private static RenderPipeline depthTestedPipeline()
    {
        if (depthTested == null)
        {
            depthTested = pipeline("canvas_behind_structures", DepthTestFunction.LEQUAL_DEPTH_TEST);
        }

        return depthTested;
    }

    private static RenderPipeline overlayPipeline()
    {
        if (overlay == null)
        {
            overlay = pipeline("canvas_overlay", DepthTestFunction.NO_DEPTH_TEST);
        }

        return overlay;
    }

    private static RenderPipeline pipeline(String name, DepthTestFunction depthTest)
    {
        Identifier shader = Identifier.of("minecraft", "core/position_tex_color");

        return RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.of(BbsFspMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(depthTest)
            .withDepthWrite(false)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .build());
    }
}
