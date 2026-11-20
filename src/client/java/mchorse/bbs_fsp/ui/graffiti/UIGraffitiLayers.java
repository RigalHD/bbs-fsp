package mchorse.bbs_fsp.ui.graffiti;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The layers of the frame being painted, top layer first like in any paint program. The eye
 * shows and hides a layer, the sun turns it into glowing paint, a click on the name selects it.
 */
public final class UIGraffitiLayers extends UIElement
{
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 6;
    private static final int ICON = 16;

    private final UIGraffitiEditor editor;
    private int firstRow;

    public UIGraffitiLayers(UIGraffitiEditor editor)
    {
        this.editor = editor;
        this.h(ROW_HEIGHT * VISIBLE_ROWS);
    }

    private CanvasLayerData layerAtRow(GraffitiPicture picture, int row)
    {
        int index = picture.layers.size() - 1 - row;

        return index >= 0 && index < picture.layers.size() ? picture.layers.get(index) : null;
    }

    @Override
    public void render(UIContext context)
    {
        GraffitiPicture picture = this.editor.getPicture();

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF202020);

        if (picture != null)
        {
            String selected = this.editor.getSelectedLayer(picture).id;

            this.firstRow = Math.max(0, Math.min(this.firstRow, picture.layers.size() - VISIBLE_ROWS));

            for (int row = 0; row < VISIBLE_ROWS; row++)
            {
                CanvasLayerData layer = this.layerAtRow(picture, row + this.firstRow);

                if (layer == null)
                {
                    break;
                }

                int y = this.area.y + row * ROW_HEIGHT;
                int background = layer.id.equals(selected) ? (layer.glow ? 0xFF8A6A10 : 0xFF2A5A9A) : 0xFF303030;

                context.batcher.box(this.area.x, y, this.area.ex(), y + ROW_HEIGHT - 1, background);
                context.batcher.icon(layer.visible ? Icons.VISIBLE : Icons.INVISIBLE, Colors.WHITE, this.area.x + 1, y + 1);
                context.batcher.icon(Icons.SUN, layer.glow ? 0xFFFFD040 : 0x50FFFFFF, this.area.x + 1 + ICON, y + 1);
                context.batcher.text(this.editor.layerName(layer), this.area.x + 4 + ICON * 2, y + 5, Colors.WHITE, false);
            }
        }

        super.render(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        GraffitiPicture picture = this.editor.getPicture();

        if (context.mouseButton != 0 || !this.area.isInside(context) || picture == null)
        {
            return super.subMouseClicked(context);
        }

        CanvasLayerData layer = this.layerAtRow(picture, this.firstRow + (context.mouseY - this.area.y) / ROW_HEIGHT);

        if (layer == null)
        {
            return true;
        }

        int x = context.mouseX - this.area.x;
        String id = layer.id;

        if (x < ICON + 1)
        {
            this.editor.editLayer(id, (target) -> target.visible = !target.visible);
        }
        else if (x < ICON * 2 + 1)
        {
            this.editor.editLayer(id, (target) -> target.glow = !target.glow);
        }

        this.editor.selectLayer(id);

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        GraffitiPicture picture = this.editor.getPicture();

        if (this.area.isInside(context) && picture != null)
        {
            this.firstRow = Math.max(0, Math.min(Math.max(0, picture.layers.size() - VISIBLE_ROWS),
                this.firstRow - (int) Math.signum(context.mouseWheel)));

            return true;
        }

        return super.subMouseScrolled(context);
    }
}
