package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.canvas.CanvasComposition;
import mchorse.bbs_fsp.canvas.CanvasFrameData;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasViewportMath;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushSet;
import mchorse.bbs_fsp.canvas.brush.CanvasStroke;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.utils.Area;
import net.minecraft.client.MinecraftClient;

/**
 * Painting on the film preview: turns cursor positions into canvas cells and feeds them to the
 * active brush stroke. Holding Alt turns a click into the eyedropper instead.
 */
public final class CanvasViewportController
{
    private static CanvasStroke stroke;
    private static CanvasClip activeClip;
    private static Area activeViewport;
    private static String selectedLayerId;
    private static boolean strokeFocused;

    private CanvasViewportController()
    {}

    public static CanvasBrushSet brushes()
    {
        return CanvasBrushStore.get();
    }

    public static void setColor(int argb)
    {
        brushes().setPrimary(argb);
    }

    public static int getColor()
    {
        return brushes().getPrimary();
    }

    public static void selectLayer(String id)
    {
        selectedLayerId = id;
    }

    public static String getSelectedLayerId()
    {
        return selectedLayerId;
    }

    public static boolean isDrawing()
    {
        return stroke != null;
    }

    public static boolean begin(CanvasClip clip, int localTick, Area viewport, int mouseX, int mouseY)
    {
        CanvasViewportMath.Cell cell = cell(clip, viewport, mouseX, mouseY);

        if (cell == null)
        {
            return false;
        }

        CanvasFrameData frame = clip.getFrameAtLocalTick(Math.max(0, localTick));

        if (frame == null || frame.layers.isEmpty())
        {
            return false;
        }

        if (Window.isAltPressed())
        {
            pickFromCanvas(clip, frame, cell);

            return true;
        }

        finish();

        CanvasBrushSet brushes = brushes();
        CanvasLayerData layer = selectedLayer(frame);
        int color = brushes.getPrimary();

        activeClip = clip;
        activeViewport = viewport;
        strokeFocused = MinecraftClient.getInstance().isWindowFocused();
        clip.frames.preNotify(IValueListener.FLAG_UNMERGEABLE);

        if (!brushes.isEraser())
        {
            clip.recentColors.preNotify(IValueListener.FLAG_UNMERGEABLE);
            clip.recentColors.remember(color);
            clip.recentColors.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        stroke = new CanvasStroke(layer, clip.width.get(), clip.height.get(), brushes.getPattern(), brushes.getBrush(),
            brushes.isEraser(), color, brushes.getSecondary(), clip.paintOpacity.get(), System.nanoTime());
        stroke.moveTo(cell.x(), cell.y());

        return true;
    }

    public static boolean continueDrawing(double mouseX, double mouseY)
    {
        if (stroke == null || activeClip == null || activeViewport == null)
        {
            return false;
        }

        CanvasViewportMath.Cell cell = rawCell(activeClip, activeViewport, (int) mouseX, (int) mouseY);

        stroke.moveTo(cell.x(), cell.y());

        return true;
    }

    public static void finish()
    {
        if (stroke != null && activeClip != null)
        {
            activeClip.frames.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        stroke = null;
        activeClip = null;
        activeViewport = null;
    }

    /**
     * Whether the window lost focus in the middle of a stroke. The button may be let go somewhere
     * the game never hears about, so such a stroke is ended rather than left following the cursor.
     */
    public static boolean lostFocus()
    {
        return stroke != null && strokeFocused && !MinecraftClient.getInstance().isWindowFocused();
    }

    public static CanvasViewportMath.Cell hoverCell(CanvasClip clip, Area viewport, int mouseX, int mouseY)
    {
        return cell(clip, viewport, mouseX, mouseY);
    }

    public static CanvasLayerData selectedLayer(CanvasFrameData frame)
    {
        for (CanvasLayerData layer : frame.layers)
        {
            if (layer.id.equals(selectedLayerId))
            {
                return layer;
            }
        }

        return frame.layers.get(0);
    }

    /**
     * Take the colour the canvas really has in that cell, every visible layer composited and
     * alpha kept. A cell nothing was painted in falls back to what the screen shows there.
     */
    private static void pickFromCanvas(CanvasClip clip, CanvasFrameData frame, CanvasViewportMath.Cell cell)
    {
        int color = CanvasComposition.colorAt(frame, clip.width.get(), clip.height.get(), cell.x(), cell.y());

        if ((color >>> 24) == 0)
        {
            CanvasEyedropper.applyScreenSample();

            return;
        }

        setColor(color);
    }

    private static CanvasViewportMath.Cell cell(CanvasClip clip, Area viewport, int mouseX, int mouseY)
    {
        CanvasViewportMath.Cell cell = rawCell(clip, viewport, mouseX, mouseY);

        return cell.x() >= 0 && cell.y() >= 0 && cell.x() < clip.width.get() && cell.y() < clip.height.get()
            ? cell
            : null;
    }

    private static CanvasViewportMath.Cell rawCell(CanvasClip clip, Area viewport, int mouseX, int mouseY)
    {
        return CanvasViewportMath.cellAt(
            mouseX,
            mouseY,
            viewport.x,
            viewport.y,
            viewport.w,
            viewport.h,
            clip.width.get(),
            clip.height.get(),
            clip.offsetX.get(),
            clip.offsetY.get(),
            clip.rotation.get()
        );
    }
}
