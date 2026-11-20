package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.canvas.CanvasFrameData;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.render.CanvasDraw;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

public final class UICanvasFrameStrip extends UIElement
{
    private final CanvasClip clip;
    private final IUIClipsDelegate editor;

    public UICanvasFrameStrip(CanvasClip clip, IUIClipsDelegate editor)
    {
        this.clip = clip;
        this.editor = editor;
        this.h(34);
    }

    @Override
    public void render(UIContext context)
    {
        int duration = Math.max(1, this.clip.duration.get());
        int background = Colors.setA(0xFF000000, 0.35F);
        int axis = Colors.setA(Colors.WHITE, 0.35F);
        int marker = Colors.setA(0xF28C28, 1F);
        int cursor = Colors.setA(Colors.WHITE, 0.8F);
        int localTick = this.editor.getCursor() - this.clip.tick.get();
        CanvasFrameData selectedFrame = this.clip.getFrameAtLocalTick(Math.max(0, localTick));

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), background);
        context.batcher.box(this.area.x, this.area.ey() - 8, this.area.ex(), this.area.ey() - 7, axis);

        for (CanvasFrameData frame : this.clip.frames.getFrames())
        {
            int x = this.area.x + Math.round((frame.tick / (float) duration) * this.area.w);

            context.batcher.box(x, this.area.ey() - 15, x + 2, this.area.ey() - 6, marker);

            if (this.clip.frames.getFrames().size() <= 64)
            {
                int center = Math.max(this.area.x + 7, Math.min(this.area.ex() - 7, x));
                int y = this.area.y + 2;

                context.batcher.box(center - 7, y - 1, center + 7, y + 12,
                    frame == selectedFrame ? marker : Colors.DARKER_GRAY);

                for (CanvasLayerData layer : frame.layers)
                {
                    if (!layer.visible || layer.pixels.length != this.clip.width.get() * this.clip.height.get())
                    {
                        continue;
                    }

                    CanvasDraw.layer(context.batcher, layer, this.clip.width.get(), this.clip.height.get(),
                        Colors.setA(Colors.WHITE, layer.opacity), center - 6, y, 12, 10);
                }
            }
        }

        int cursorX = this.area.x + Math.round((Math.max(0, Math.min(duration, localTick)) / (float) duration) * this.area.w);

        context.batcher.box(cursorX, this.area.y, cursorX + 1, this.area.ey(), cursor);
        super.render(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton != 0 || !this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        int duration = Math.max(1, this.clip.duration.get());
        int localTick = Math.max(0, Math.min(duration - 1,
            Math.round((context.mouseX - this.area.x) / (float) Math.max(1, this.area.w) * duration)));

        this.editor.setCursor(this.clip.tick.get() + localTick);

        return true;
    }
}
