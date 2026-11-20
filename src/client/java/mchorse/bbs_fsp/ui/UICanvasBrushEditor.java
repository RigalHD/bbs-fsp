package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.canvas.brush.CanvasBrush;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushPattern;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushSet;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushShape;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.function.DoubleSupplier;

/**
 * The brush editor: which pattern paints (pencil, marker, gradient, dither, spray), the shape
 * and size of a dab and the settings that only some patterns use. Every pattern remembers its
 * own settings, the controls always show the active one.
 */
public final class UICanvasBrushEditor extends UIElement
{
    private final UICanvasBrushPreview preview;
    private final UIElement softnessRow;
    private final UIElement flowRow;
    private final UIElement densityRow;
    private final UIElement lengthRow;
    private CanvasBrushPattern shownPattern;

    public UICanvasBrushEditor(DoubleSupplier paintOpacity)
    {
        this.preview = new UICanvasBrushPreview(paintOpacity);

        UIElement patterns = UI.row(0, 0, 20,
            this.patternIcon(CanvasBrushPattern.PENCIL, Icons.EDIT),
            this.patternIcon(CanvasBrushPattern.MARKER, Icons.BRUSH),
            this.patternIcon(CanvasBrushPattern.GRADIENT, Icons.FADING),
            this.patternIcon(CanvasBrushPattern.DITHER, Icons.CHECKBOARD),
            this.patternIcon(CanvasBrushPattern.SPRAY, Icons.SPRAY));
        UIElement shapes = UI.row(0, 0, 20,
            this.shapeIcon(CanvasBrushShape.SQUARE, Icons.SQUARE),
            this.shapeIcon(CanvasBrushShape.CIRCLE, Icons.CIRCLE),
            this.shapeIcon(CanvasBrushShape.DIAMOND, Icons.DIAMOND),
            this.shapeIcon(CanvasBrushShape.HORIZONTAL, Icons.HORIZONTAL),
            this.shapeIcon(CanvasBrushShape.VERTICAL, Icons.VERTICAL));

        UITrackpad size = this.trackpad((brush, value) -> brush.size = CanvasBrush.clampSize(value.intValue()),
            CanvasBrush.MIN_SIZE, CanvasBrush.MAX_SIZE, true, (brush) -> brush.size);
        UITrackpad softness = this.trackpad((brush, value) -> brush.softness = value.floatValue(),
            0, 1, false, (brush) -> brush.softness);
        UITrackpad flow = this.trackpad((brush, value) -> brush.flow = value.floatValue(),
            0, 1, false, (brush) -> brush.flow);
        UITrackpad density = this.trackpad((brush, value) -> brush.density = value.floatValue(),
            0, 1, false, (brush) -> brush.density);
        UITrackpad length = this.trackpad((brush, value) -> brush.length = CanvasBrush.clampLength(value.intValue()),
            CanvasBrush.MIN_LENGTH, CanvasBrush.MAX_LENGTH, true, (brush) -> brush.length);

        UIIcon eraser = new UIIcon(Icons.ERASER, (button) ->
        {
            CanvasBrushSet brushes = brushes();

            brushes.setEraser(!brushes.isEraser());
        });
        UIIcon reset = new UIIcon(Icons.REFRESH, (button) -> brushes().resetBrush());

        eraser.highlight(() -> brushes().isEraser(), Direction.BOTTOM);
        eraser.tooltip(BbsFspKeys.BRUSH_ERASER, Direction.BOTTOM);
        reset.tooltip(BbsFspKeys.BRUSH_RESET, Direction.BOTTOM);

        this.softnessRow = UI.labelRow(BbsFspKeys.BRUSH_SOFTNESS, softness);
        this.flowRow = UI.labelRow(BbsFspKeys.BRUSH_FLOW, flow);
        this.densityRow = UI.labelRow(BbsFspKeys.BRUSH_DENSITY, density);
        this.lengthRow = UI.labelRow(BbsFspKeys.BRUSH_LENGTH, length);

        this.column().stretch().vertical().height(20);
        this.add(
            this.preview,
            UI.label(BbsFspKeys.BRUSH_PATTERN),
            patterns,
            UI.label(BbsFspKeys.BRUSH_SHAPE),
            shapes,
            UI.labelRow(BbsFspKeys.BRUSH_SIZE, size),
            this.softnessRow,
            this.flowRow,
            this.densityRow,
            this.lengthRow,
            UI.row(0, 0, 20, eraser, reset)
        );
        this.updateRows();
    }

    private static CanvasBrushSet brushes()
    {
        return CanvasViewportController.brushes();
    }

    private UIIcon patternIcon(CanvasBrushPattern pattern, Icon icon)
    {
        UIIcon button = new UIIcon(icon, (b) ->
        {
            brushes().setPattern(pattern);
            this.updateRows();
        });

        button.highlight(() -> brushes().getPattern() == pattern, Direction.BOTTOM);
        button.tooltip(BbsFspKeys.pattern(pattern), Direction.BOTTOM);

        return button;
    }

    private UIIcon shapeIcon(CanvasBrushShape shape, Icon icon)
    {
        UIIcon button = new UIIcon(icon, (b) ->
        {
            CanvasBrushSet brushes = brushes();

            brushes.getBrush().shape = shape;
            brushes.touch();
        });

        button.highlight(() -> brushes().getBrush().shape == shape, Direction.BOTTOM);
        button.tooltip(BbsFspKeys.shape(shape), Direction.BOTTOM);

        return button;
    }

    private UITrackpad trackpad(BrushSetter setter, double min, double max, boolean integer, BrushGetter getter)
    {
        UITrackpad trackpad = new UITrackpad((value) ->
        {
            CanvasBrushSet brushes = brushes();

            setter.set(brushes.getBrush(), value);
            brushes.touch();
        });

        trackpad.limit(min, max, integer);
        trackpad.valueBinding(() -> trackpad.setValue(getter.get(brushes().getBrush())));

        return trackpad;
    }

    private void updateRows()
    {
        CanvasBrushPattern pattern = brushes().getPattern();

        this.shownPattern = pattern;
        this.softnessRow.setVisible(pattern.softness);
        this.flowRow.setVisible(pattern.flow);
        this.densityRow.setVisible(pattern.density);
        this.lengthRow.setVisible(pattern.gradient);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.shownPattern != brushes().getPattern())
        {
            this.updateRows();
        }

        super.render(context);
    }

    private interface BrushSetter
    {
        void set(CanvasBrush brush, Double value);
    }

    private interface BrushGetter
    {
        double get(CanvasBrush brush);
    }
}
