package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.canvas.CanvasFrameData;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

public final class UICanvasLayers extends UIElement
{
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 5;

    private final CanvasClip clip;
    private final IUIClipsDelegate editor;
    private int firstRow;

    public UICanvasLayers(CanvasClip clip, IUIClipsDelegate editor)
    {
        this.clip = clip;
        this.editor = editor;
        this.h(ROW_HEIGHT * VISIBLE_ROWS);
    }

    private CanvasFrameData currentFrame()
    {
        CanvasFrameData frame = this.clip.getFrameAtLocalTick(Math.max(0, this.editor.getCursor() - this.clip.tick.get()));

        if (frame != null && !frame.layers.isEmpty())
        {
            String selected = CanvasViewportController.getSelectedLayerId();

            if (selected == null || frame.layers.stream().noneMatch(layer -> layer.id.equals(selected)))
            {
                CanvasViewportController.selectLayer(frame.layers.get(0).id);
            }
        }

        return frame;
    }

    @Override
    public void render(UIContext context)
    {
        CanvasFrameData frame = this.currentFrame();

        if (frame != null)
        {
            this.firstRow = Math.min(this.firstRow, Math.max(0, frame.layers.size() - VISIBLE_ROWS));

            for (int row = 0; row < VISIBLE_ROWS && row + this.firstRow < frame.layers.size(); row++)
            {
                CanvasLayerData layer = frame.layers.get(row + this.firstRow);
                int y = this.area.y + row * ROW_HEIGHT;
                boolean selected = layer.id.equals(CanvasViewportController.getSelectedLayerId())
                    || CanvasViewportController.getSelectedLayerId() == null && row + this.firstRow == 0;
                int background = selected ? 0xFFB06A20 : 0xFF333333;

                context.batcher.box(this.area.x, y, this.area.ex(), y + ROW_HEIGHT - 1, background);
                String name = layer.name.matches("[0-9]+")
                    ? UIKeys.TEXTURES_LAYERS_DEFAULT_NAME.format(layer.name).get() : layer.name;

                context.batcher.text(name, this.area.x + 4, y + 5, Colors.WHITE, false);
            }
        }

        super.render(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        CanvasFrameData frame = this.currentFrame();

        if (context.mouseButton == 0 && this.area.isInside(context) && frame != null)
        {
            int index = this.firstRow + (context.mouseY - this.area.y) / ROW_HEIGHT;

            if (index < frame.layers.size())
            {
                CanvasViewportController.selectLayer(frame.layers.get(index).id);
                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        CanvasFrameData frame = this.currentFrame();

        if (this.area.isInside(context) && frame != null)
        {
            this.firstRow = Math.max(0, Math.min(Math.max(0, frame.layers.size() - VISIBLE_ROWS),
                this.firstRow - (int) Math.signum(context.mouseWheel)));
            return true;
        }

        return super.subMouseScrolled(context);
    }
}
