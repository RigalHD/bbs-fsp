package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.brush.CanvasBrush;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushSet;
import mchorse.bbs_fsp.canvas.brush.CanvasStroke;
import mchorse.bbs_fsp.render.CanvasDraw;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Arrays;
import java.util.function.DoubleSupplier;

/**
 * A sample stroke painted with the current brush, redrawn whenever a setting changes, so the
 * effect of size, shape, softness and pattern is visible before touching the canvas.
 */
public final class UICanvasBrushPreview extends UIElement
{
    private static final long SEED = 0x5EEDL;

    private final CanvasLayerData layer = new CanvasLayerData(1, 1);
    private final DoubleSupplier paintOpacity;
    private long revision = -1;
    private float opacity = -1F;
    private int cellSize;
    private int cellsX;
    private int cellsY;

    public UICanvasBrushPreview(DoubleSupplier paintOpacity)
    {
        this.paintOpacity = paintOpacity;
        this.h(52);
    }

    @Override
    public void render(UIContext context)
    {
        CanvasBrushSet brushes = CanvasViewportController.brushes();
        float paintOpacity = (float) this.paintOpacity.getAsDouble();

        this.rebuild(brushes, paintOpacity);

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50);
        context.batcher.iconArea(Icons.CHECKBOARD, Colors.setA(Colors.WHITE, 0.35F),
            this.area.x + 1, this.area.y + 1, this.area.w - 2, this.area.h - 2);

        if (this.cellsX > 0 && this.cellsY > 0)
        {
            CanvasDraw.layer(context.batcher, this.layer, this.cellsX, this.cellsY, Colors.WHITE,
                this.area.x + 1, this.area.y + 1, this.cellsX * this.cellSize, this.cellsY * this.cellSize);
        }

        CanvasBrush brush = brushes.getBrush();
        String label = BbsFspKeys.pattern(brushes.getPattern()).get() + " · " + brush.size + "px";

        if (brushes.isEraser())
        {
            label = BbsFspKeys.BRUSH_ERASER.get() + " · " + label;
        }

        context.batcher.textShadow(label, this.area.x + 4, this.area.y + 4);

        super.render(context);
    }

    private void rebuild(CanvasBrushSet brushes, float paintOpacity)
    {
        int innerW = Math.max(1, this.area.w - 2);
        int innerH = Math.max(1, this.area.h - 2);
        CanvasBrush brush = brushes.getBrush();
        int cellSize = Math.max(1, Math.min(4, innerH / (brush.size + 8)));
        int cellsX = innerW / cellSize;
        int cellsY = innerH / cellSize;

        if (this.revision == brushes.getRevision() && this.opacity == paintOpacity
            && this.cellSize == cellSize && this.cellsX == cellsX && this.cellsY == cellsY)
        {
            return;
        }

        this.revision = brushes.getRevision();
        this.opacity = paintOpacity;
        this.cellSize = cellSize;
        this.cellsX = cellsX;
        this.cellsY = cellsY;

        if (cellsX <= 0 || cellsY <= 0)
        {
            return;
        }

        int[] pixels = new int[cellsX * cellsY];

        if (brushes.isEraser())
        {
            int band = Math.max(1, cellsY / 3);
            int fill = Colors.setA(brushes.getPrimary(), 1F);

            for (int y = band; y < cellsY - band; y++)
            {
                Arrays.fill(pixels, y * cellsX, (y + 1) * cellsX, fill);
            }
        }

        this.layer.pixels = pixels;

        CanvasStroke stroke = new CanvasStroke(this.layer, cellsX, cellsY, brushes.getPattern(), brush,
            brushes.isEraser(), brushes.getPrimary(), brushes.getSecondary(), paintOpacity, SEED);
        int margin = Math.min(cellsX / 4, brush.size / 2 + 2);
        float amplitude = Math.max(0F, cellsY / 2F - brush.size / 2F - 2F);
        int steps = Math.max(2, (cellsX - margin * 2) / 2);

        for (int i = 0; i <= steps; i++)
        {
            float t = i / (float) steps;
            int x = Math.round(margin + t * (cellsX - margin * 2 - 1));
            int y = Math.round(cellsY / 2F + (float) Math.sin(t * Math.PI * 2D) * amplitude);

            stroke.moveTo(x, y);
        }

        this.layer.touch();
    }
}
