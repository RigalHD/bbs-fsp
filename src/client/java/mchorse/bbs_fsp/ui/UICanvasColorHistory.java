package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.IntConsumer;

public final class UICanvasColorHistory extends UIElement
{
    private static final int[] PALETTE = {
        0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFFFF0000, 0xFFFF8000,
        0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF,
        0xFF663300, 0xFFFFCC99, 0xFFCC3333, 0xFFCC9933, 0xFF99CC33,
        0xFF33CC99, 0xFF3399CC, 0xFF6633CC, 0xFFCC33CC, 0xFF333333
    };

    private final CanvasClip clip;
    private final IntConsumer selectColor;

    public UICanvasColorHistory(CanvasClip clip, IntConsumer selectColor)
    {
        this.clip = clip;
        this.selectColor = selectColor;
        this.h(64);
    }

    @Override
    public void render(UIContext context)
    {
        int size = Math.min(20, Math.max(1, this.area.w / 13));

        context.batcher.box(this.area.x, this.area.y, this.area.x + size - 1, this.area.y + 20, Colors.WHITE);
        context.batcher.box(this.area.x + 1, this.area.y + 1, this.area.x + size - 2,
            this.area.y + 19, CanvasViewportController.getColor());

        for (int i = 0; i < this.clip.recentColors.getColors().size(); i++)
        {
            int x = this.area.x + (i + 1) * size;
            int color = this.clip.recentColors.getColors().get(i);

            context.batcher.box(x, this.area.y, x + size - 1, this.area.y + 20, Colors.WHITE);
            context.batcher.box(x + 1, this.area.y + 1, x + size - 2, this.area.y + 19, color);
        }

        int paletteSize = Math.min(20, Math.max(3, this.area.w / 10));

        for (int i = 0; i < PALETTE.length; i++)
        {
            int x = this.area.x + i % 10 * paletteSize;
            int y = this.area.y + 24 + i / 10 * paletteSize;

            context.batcher.box(x, y, x + paletteSize - 1, y + paletteSize - 1, Colors.WHITE);
            context.batcher.box(x + 1, y + 1, x + paletteSize - 2, y + paletteSize - 2, PALETTE[i]);
        }

        super.render(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton != 0 || !this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        int localY = context.mouseY - this.area.y;

        if (localY < 24)
        {
            int size = Math.min(20, Math.max(1, this.area.w / 13));
            int index = (context.mouseX - this.area.x) / size - 1;

            if (index >= 0 && index < this.clip.recentColors.getColors().size())
            {
                this.selectColor.accept(this.clip.recentColors.getColors().get(index));
                return true;
            }
        }
        else
        {
            int size = Math.min(20, Math.max(3, this.area.w / 10));
            int row = (localY - 24) / size;
            int column = (context.mouseX - this.area.x) / size;
            int index = row * 10 + column;

            if (row >= 0 && row < 2 && column >= 0 && column < 10 && index < PALETTE.length)
            {
                this.selectColor.accept(PALETTE[index]);
                return true;
            }
        }

        return false;
    }
}
