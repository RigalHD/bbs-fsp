package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.canvas.CanvasFrameData;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.UUID;

public final class UICanvasClip extends UIClip<CanvasClip>
{
    private UITrackpad widthInput;
    private UITrackpad heightInput;
    private UITrackpad offsetXInput;
    private UITrackpad offsetYInput;
    private UITrackpad rotationInput;
    private UITrackpad gridOpacityInput;
    private UITrackpad previousFrameOpacityInput;
    private UITrackpad paintOpacityInput;
    private UITrackpad frameCountInput;
    private UIColor colorInput;
    private UIColor secondaryColorInput;
    private UIIcon swapColors;
    private UIIcon eyedropper;
    private UICanvasColorHistory colorHistory;
    private UICanvasBrushEditor brushEditor;
    private UICanvasLayers layers;
    private UIButton duplicateLayer;
    private UIButton addLayer;
    private UICanvasFrameStrip frameStrip;

    public UICanvasClip(CanvasClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        this.widthInput = new UITrackpad((value) -> this.editor.editMultiple(this.clip.width,
            (width) ->
            {
                width.set(value.intValue());
                this.clip.ensureValidData();
            })).limit(1, 4096, true);
        this.heightInput = new UITrackpad((value) -> this.editor.editMultiple(this.clip.height,
            (height) ->
            {
                height.set(value.intValue());
                this.clip.ensureValidData();
            })).limit(1, 4096, true);
        this.offsetXInput = new UITrackpad((value) -> this.editor.editMultiple(this.clip.offsetX,
            (offset) -> offset.set(value.floatValue())));
        this.offsetYInput = new UITrackpad((value) -> this.editor.editMultiple(this.clip.offsetY,
            (offset) -> offset.set(value.floatValue())));
        this.rotationInput = new UITrackpad((value) -> this.editor.editMultiple(this.clip.rotation,
            (rotation) -> rotation.set(value.floatValue())));
        this.gridOpacityInput = new UITrackpad((value) -> this.editor.editMultiple(this.clip.gridOpacity,
            (opacity) -> opacity.set(value.floatValue()))).limit(0, 1);
        this.previousFrameOpacityInput = new UITrackpad((value) -> this.editor.editMultiple(this.clip.previousFrameOpacity,
            (opacity) -> opacity.set(value.floatValue()))).limit(0, 1);
        this.paintOpacityInput = new UITrackpad((value) -> this.editor.editMultiple(this.clip.paintOpacity,
            (opacity) -> opacity.set(value.floatValue()))).limit(0, 1);
        this.frameCountInput = new UITrackpad((value) ->
        {
            this.clip.frames.preNotify(IValueListener.FLAG_UNMERGEABLE);
            this.clip.frames.setFrameCount(value.intValue(), this.clip.duration.get());
            this.clip.frames.postNotify(IValueListener.FLAG_UNMERGEABLE);
            this.frameCountInput.setValue(this.clip.frames.getFrames().size());
        }).limit(1, 10000, true);

        this.colorInput = new UIColor(CanvasViewportController::setColor).withAlpha();
        this.colorInput.valueBinding(() -> this.colorInput.setColor(CanvasViewportController.getColor()));
        this.colorInput.tooltip(BbsFspKeys.COLOR, Direction.BOTTOM);
        this.secondaryColorInput = new UIColor((color) -> CanvasViewportController.brushes().setSecondary(color)).withAlpha();
        this.secondaryColorInput.valueBinding(() -> this.secondaryColorInput.setColor(CanvasViewportController.brushes().getSecondary()));
        this.secondaryColorInput.tooltip(BbsFspKeys.SECONDARY_COLOR, Direction.BOTTOM);
        this.swapColors = new UIIcon(Icons.EXCHANGE, (button) -> CanvasViewportController.brushes().swapColors());
        this.swapColors.tooltip(BbsFspKeys.SWAP_COLORS, Direction.BOTTOM);
        this.swapColors.w(20);
        this.eyedropper = new UIIcon(Icons.EYEDROPPER, (button) -> CanvasEyedropper.toggle())
        {
            @Override
            public void render(UIContext context)
            {
                super.render(context);
                CanvasEyedropper.setButtonArea(context.globalX(this.area.x), context.globalY(this.area.y),
                    this.area.w, this.area.h);
            }
        };
        this.eyedropper.highlight(CanvasEyedropper::isArmed, Direction.BOTTOM);
        this.eyedropper.tooltip(BbsFspKeys.EYEDROPPER, Direction.BOTTOM);
        this.eyedropper.w(20);
        this.colorHistory = new UICanvasColorHistory(this.clip, CanvasViewportController::setColor);
        this.brushEditor = new UICanvasBrushEditor(() -> this.clip.paintOpacity.get());

        this.layers = new UICanvasLayers(this.clip, this.editor);
        this.duplicateLayer = new UIButton(BbsFspKeys.DUPLICATE_LAYER, (button) -> this.duplicateSelectedLayer());
        this.addLayer = new UIButton(UIKeys.TEXTURES_LAYERS_ADD_EMPTY, (button) ->
        {
            this.clip.frames.preNotify(IValueListener.FLAG_UNMERGEABLE);
            String id = this.clip.addLayer();
            this.clip.frames.postNotify(IValueListener.FLAG_UNMERGEABLE);

            if (id != null)
            {
                CanvasViewportController.selectLayer(id);
            }
        });
        this.frameStrip = new UICanvasFrameStrip(this.clip, this.editor);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(
            BbsFspKeys.CANVAS,
            UI.labelRow(BbsFspKeys.WIDTH, this.widthInput),
            UI.labelRow(BbsFspKeys.HEIGHT, this.heightInput),
            UI.labelRow(BbsFspKeys.OFFSET_X, this.offsetXInput),
            UI.labelRow(BbsFspKeys.OFFSET_Y, this.offsetYInput),
            UI.labelRow(BbsFspKeys.ROTATION, this.rotationInput),
            UI.labelRow(BbsFspKeys.GRID_OPACITY, this.gridOpacityInput),
            UI.labelRow(BbsFspKeys.PREVIOUS_FRAME, this.previousFrameOpacityInput)
        ));
        this.panels.add(this.section(BbsFspKeys.COLOR,
            UI.row(this.colorInput, this.swapColors, this.secondaryColorInput, this.eyedropper),
            UI.labelRow(BbsFspKeys.OPACITY, this.paintOpacityInput),
            UI.labelRow(BbsFspKeys.RECENT_COLORS, this.colorHistory)));
        this.panels.add(this.section(BbsFspKeys.BRUSH, this.brushEditor));
        this.panels.add(this.section(BbsFspKeys.LAYERS, this.layers, this.addLayer, this.duplicateLayer));
        this.panels.add(this.section(BbsFspKeys.FRAMES,
            UI.labelRow(BbsFspKeys.FRAMES, this.frameCountInput), this.frameStrip));
    }

    @Override
    public void editClip(Position position)
    {
        this.clip.ensureValidData();
        super.editClip(position);
    }

    @Override
    public void fillData()
    {
        super.fillData();
        this.widthInput.setValue(this.clip.width.get());
        this.heightInput.setValue(this.clip.height.get());
        this.offsetXInput.setValue(this.clip.offsetX.get());
        this.offsetYInput.setValue(this.clip.offsetY.get());
        this.rotationInput.setValue(this.clip.rotation.get());
        this.gridOpacityInput.setValue(this.clip.gridOpacity.get());
        this.previousFrameOpacityInput.setValue(this.clip.previousFrameOpacity.get());
        this.paintOpacityInput.setValue(this.clip.paintOpacity.get());
        this.frameCountInput.limit(this.clip.frames.getFrames().size(),
            Math.max(this.clip.frames.getFrames().size(), Math.min(this.clip.duration.get(), 10000)), true);
        this.frameCountInput.setValue(this.clip.frames.getFrames().size());
    }

    @Override
    public void render(UIContext context)
    {
        CanvasBrushStore.saveLater();
        super.render(context);
    }

    @Override
    protected void updateDuration(int duration)
    {
        this.clip.ensureValidData();
    }

    private void duplicateSelectedLayer()
    {
        CanvasFrameData frame = this.clip.getFrameAtLocalTick(Math.max(0, this.editor.getCursor() - this.clip.tick.get()));

        if (frame == null || frame.layers.isEmpty())
        {
            return;
        }

        String requested = CanvasViewportController.getSelectedLayerId();
        String selected = requested != null && frame.layers.stream().anyMatch(layer -> layer.id.equals(requested))
            ? requested
            : frame.layers.get(0).id;
        int index = 0;

        for (int i = 0; i < frame.layers.size(); i++)
        {
            if (frame.layers.get(i).id.equals(selected))
            {
                index = i;
                break;
            }
        }

        for (CanvasFrameData candidate : this.clip.frames.getFrames())
        {
            if (candidate.layers.size() <= index || candidate.layers.size() >= 512)
            {
                return;
            }
        }

        String id = UUID.randomUUID().toString();
        this.clip.frames.preNotify(IValueListener.FLAG_UNMERGEABLE);

        for (CanvasFrameData candidate : this.clip.frames.getFrames())
        {
            CanvasLayerData copy = candidate.layers.get(index).duplicate(index + 1);

            copy.id = id;
            candidate.layers.add(index + 1, copy);

            for (int i = 0; i < candidate.layers.size(); i++)
            {
                candidate.layers.get(i).order = i;
            }
        }

        this.clip.frames.postNotify(IValueListener.FLAG_UNMERGEABLE);
        CanvasViewportController.selectLayer(id);
    }
}
