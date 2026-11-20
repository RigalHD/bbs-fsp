package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.canvas.CanvasComposition;
import mchorse.bbs_fsp.canvas.CanvasFrameData;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasViewportMath;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushSet;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushStamp;
import mchorse.bbs_fsp.ui.CanvasEyedropper;
import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Editor-only drawing over the film preview while a canvas clip is selected: the previous frame
 * as an onion skin, the pixel grid and the outline of the brush under the cursor. None of it
 * reaches an export, the painted layers themselves are already part of the rendered frame.
 */
public final class CanvasPreviewOverlay
{
    public static final int GRID_COLOR = 0x8C8C8C;

    private static final int FILL_LIMIT = 8;

    private static CanvasBrushStamp stamp;
    private static boolean[] stampMask;
    private static long stampRevision = -1;

    private CanvasPreviewOverlay()
    {}

    public static void render(UIFilmPreview preview, UIFilmPanel panel, UIContext context)
    {
        if (panel == null || panel.cameraEditor == null || !(panel.cameraEditor.getClip() instanceof CanvasClip clip))
        {
            return;
        }

        if (CanvasViewportController.lostFocus())
        {
            CanvasViewportController.finish();
        }

        Area viewport = preview.getViewport();
        CanvasFrameData frame = clip.getFrameAtLocalTick(Math.max(0, panel.getCursor() - clip.tick.get()));

        if (frame == null)
        {
            return;
        }

        CanvasViewportMath.Cell hover = viewport.isInside(context)
            ? CanvasViewportController.hoverCell(clip, viewport, context.mouseX, context.mouseY)
            : null;
        boolean picking = CanvasEyedropper.isArmed() || hover != null && Window.isAltPressed();

        CanvasEyedropper.setCanvasHover(hover != null, hover == null ? 0
            : CanvasComposition.colorAt(frame, clip.width.get(), clip.height.get(), hover.x(), hover.y()));

        if (picking)
        {
            return;
        }

        CanvasFrameData previous = previousFrame(clip, frame);
        int width = clip.width.get();
        int height = clip.height.get();
        float centerX = viewport.x + viewport.w / 2F;
        float centerY = viewport.y + viewport.h / 2F;

        context.batcher.clip(viewport, context);
        CanvasDraw.pushCanvasTransform(context.batcher, centerX, centerY,
            (float) CanvasViewportMath.offsetToArea(clip.offsetX.get(), viewport.w, width),
            (float) CanvasViewportMath.offsetToArea(clip.offsetY.get(), viewport.h, height),
            clip.rotation.get());

        try
        {
            if (previous != null && clip.previousFrameOpacity.get() > 0F)
            {
                renderFrame(previous, width, height, viewport, context, clip.previousFrameOpacity.get());
            }

            renderGrid(clip, width, height, viewport, context);

            if (hover != null && !CanvasViewportController.isDrawing())
            {
                renderBrush(hover, width, height, viewport, context);
            }
        }
        finally
        {
            CanvasDraw.popTransform(context.batcher);
            context.batcher.unclip(context);
        }
    }

    private static CanvasFrameData previousFrame(CanvasClip clip, CanvasFrameData frame)
    {
        CanvasFrameData previous = null;

        for (CanvasFrameData candidate : clip.frames.getFrames())
        {
            if (candidate.tick >= frame.tick)
            {
                break;
            }

            previous = candidate;
        }

        return previous;
    }

    private static void renderGrid(CanvasClip clip, int width, int height, Area viewport, UIContext context)
    {
        float opacity = clip.gridOpacity.get();

        if (opacity <= 0F)
        {
            return;
        }

        int lineColor = Colors.setA(GRID_COLOR, opacity);

        context.batcher.beginBatch();

        try
        {
            for (int x = 0; x <= width; x++)
            {
                int px = viewport.x + Math.round(x * viewport.w / (float) width);

                context.batcher.box(px, viewport.y, px + 1, viewport.ey(), lineColor);
            }

            for (int y = 0; y <= height; y++)
            {
                int py = viewport.y + Math.round(y * viewport.h / (float) height);

                context.batcher.box(viewport.x, py, viewport.ex(), py + 1, lineColor);
            }
        }
        finally
        {
            context.batcher.endBatch();
        }
    }

    /**
     * The cells the next dab would cover. Small brushes show every cell, larger ones only their
     * rim, which keeps the outline cheap however wide the brush gets.
     */
    private static void renderBrush(CanvasViewportMath.Cell hover, int width, int height, Area viewport, UIContext context)
    {
        CanvasBrushSet brushes = CanvasViewportController.brushes();
        CanvasBrushStamp current = stamp(brushes);
        int color = brushes.isEraser()
            ? Colors.setA(Colors.WHITE, 0.45F)
            : Colors.setA(brushes.getPrimary(), Math.max(0.35F, Math.min(0.75F, ((brushes.getPrimary() >>> 24) & 255) / 255F)));
        boolean fill = Math.max(current.width, current.height) <= FILL_LIMIT;
        boolean[] mask = stampMask;

        context.batcher.beginBatch();

        try
        {
            for (int i = 0; i < current.count; i++)
            {
                int localX = current.offsetX[i] - current.minX();
                int localY = current.offsetY[i] - current.minY();

                if (!fill && !isRim(mask, current.width, current.height, localX, localY))
                {
                    continue;
                }

                int x = hover.x() + current.offsetX[i];
                int y = hover.y() + current.offsetY[i];

                if (x < 0 || y < 0 || x >= width || y >= height)
                {
                    continue;
                }

                float x1 = viewport.x + x * viewport.w / (float) width;
                float y1 = viewport.y + y * viewport.h / (float) height;
                float x2 = viewport.x + (x + 1) * viewport.w / (float) width;
                float y2 = viewport.y + (y + 1) * viewport.h / (float) height;

                context.batcher.box(x1, y1, x2, y2, color);
            }
        }
        finally
        {
            context.batcher.endBatch();
        }
    }

    private static CanvasBrushStamp stamp(CanvasBrushSet brushes)
    {
        if (stamp == null || stampRevision != brushes.getRevision())
        {
            stamp = CanvasBrushStamp.create(brushes.getBrush(), false);
            stampMask = mask(stamp);
            stampRevision = brushes.getRevision();
        }

        return stamp;
    }

    private static boolean[] mask(CanvasBrushStamp stamp)
    {
        boolean[] mask = new boolean[stamp.width * stamp.height];

        for (int i = 0; i < stamp.count; i++)
        {
            mask[(stamp.offsetY[i] - stamp.minY()) * stamp.width + stamp.offsetX[i] - stamp.minX()] = true;
        }

        return mask;
    }

    private static boolean isRim(boolean[] mask, int width, int height, int x, int y)
    {
        return !filled(mask, width, height, x - 1, y) || !filled(mask, width, height, x + 1, y)
            || !filled(mask, width, height, x, y - 1) || !filled(mask, width, height, x, y + 1);
    }

    private static boolean filled(boolean[] mask, int width, int height, int x, int y)
    {
        return x >= 0 && y >= 0 && x < width && y < height && mask[y * width + x];
    }

    private static void renderFrame(CanvasFrameData frame, int width, int height, Area viewport,
        UIContext context, float opacity)
    {
        for (CanvasLayerData layer : frame.layers)
        {
            if (!layer.visible || layer.pixels.length != width * height)
            {
                continue;
            }

            CanvasDraw.layer(context.batcher, layer, width, height, Colors.setA(Colors.WHITE, layer.opacity * opacity),
                viewport.x, viewport.y, viewport.w, viewport.h);
        }
    }
}
