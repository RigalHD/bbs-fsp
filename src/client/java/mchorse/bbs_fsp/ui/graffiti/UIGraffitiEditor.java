package mchorse.bbs_fsp.ui.graffiti;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.canvas.CanvasRasterTools;
import mchorse.bbs_fsp.canvas.brush.CanvasBrush;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushSet;
import mchorse.bbs_fsp.canvas.brush.CanvasStroke;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.graffiti.GraffitiRaster;
import mchorse.bbs_fsp.render.CanvasDraw;
import mchorse.bbs_fsp.render.GraffitiScene;
import mchorse.bbs_fsp.render.GraffitiTextures;
import mchorse.bbs_fsp.ui.BbsFspKeys;
import mchorse.bbs_fsp.ui.CanvasBrushStore;
import mchorse.bbs_fsp.ui.CanvasEyedropper;
import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_fsp.ui.UICanvasBrushEditor;
import mchorse.bbs_mod.camera.OrbitCamera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The graffiti's own paint program, over the whole screen. The middle of it is the scene the
 * film renders, and the brush lands on the graffiti wherever it stands in it: the cursor is
 * cast as a ray onto the quad's plane. The camera flies like it does in the film editor (F),
 * and a flat view of the picture alone (V) is there for close work and for graffiti edited
 * outside a film.
 *
 * <p>Every tool of the texture editor is here (brush, eraser, fill, shapes, move, pipette,
 * flips, resize, layers), with the canvas clip's brush engine, a blur brush and glowing
 * paint on top. The frames are the keyframes of the pixel art track, and the timeline at the
 * bottom moves the film's cursor, since the film editor's own one is under the editor.</p>
 *
 * <p>The keyboard is the editor's alone while it is open: a key it lets through would reach
 * the film editor under it, which acts on whatever is selected there.</p>
 */
public final class UIGraffitiEditor extends UIElement
{
    private static final int TOP = 24;
    private static final int TOOLS = 24;
    private static final int SIDEBAR = 214;
    private static final int BOTTOM = UIGraffitiFrames.HEIGHT;
    private static final int PANEL_COLOR = 0xF0141414;
    private static final int GRID_COLOR = 0x8C8C8C;
    private static final int[] PALETTE = {
        0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFFFF0000, 0xFFFF8000, 0xFFFFFF00, 0xFF00FF00,
        0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFF663300, 0xFFFFCC99, 0xFF33CC99, 0xFF6633CC
    };
    private static final KeyCombo[] FLIGHT_KEYS = {
        Keys.FLIGHT_FORWARD, Keys.FLIGHT_BACKWARD, Keys.FLIGHT_LEFT, Keys.FLIGHT_RIGHT, Keys.FLIGHT_UP, Keys.FLIGHT_DOWN,
        Keys.FLIGHT_TILT_UP, Keys.FLIGHT_TILT_DOWN, Keys.FLIGHT_PAN_LEFT, Keys.FLIGHT_PAN_RIGHT
    };
    private static final List<Integer> RECENT = new ArrayList<>();
    private static final int RECENT_LIMIT = 14;

    private static UIGraffitiEditor current;
    private static Tool tool = Tool.BRUSH;
    private static boolean glowPaint;
    private static boolean filledShapes;
    private static float paintOpacity = 1F;
    private static int frameLength = 2;

    private final GraffitiSession session;
    private final UIGraffitiFrames timeline;
    private final Area center = new Area();
    private final Area viewport = new Area();

    private boolean flat;
    private float zoom = 1F;
    private float panX;
    private float panY;
    private boolean panning;
    private boolean looking;
    private int lastMouseX;
    private int lastMouseY;
    private int newWidth;
    private int newHeight;
    private String selectedLayer;

    private GraffitiPicture strokePicture;
    private CanvasLayerData strokeLayer;
    private CanvasStroke stroke;
    private int[] snapshot;
    private int startX;
    private int startY;
    private int lastX;
    private int lastY;

    public enum Tool
    {
        BRUSH(Icons.BRUSH, GLFW.GLFW_KEY_B),
        ERASER(Icons.ERASER, GLFW.GLFW_KEY_E),
        FILL(Icons.BUCKET, GLFW.GLFW_KEY_G),
        LINE(Icons.LINE, GLFW.GLFW_KEY_L),
        RECTANGLE(Icons.SQUARE, GLFW.GLFW_KEY_R),
        ELLIPSE(Icons.CIRCLE, GLFW.GLFW_KEY_O),
        BLUR(Icons.DROP, GLFW.GLFW_KEY_U),
        MOVE(Icons.ALL_DIRECTIONS, GLFW.GLFW_KEY_M),
        PICKER(Icons.EYEDROPPER, GLFW.GLFW_KEY_I);

        public final Icon icon;
        public final int key;

        Tool(Icon icon, int key)
        {
            this.icon = icon;
            this.key = key;
        }
    }

    public record Onion(GraffitiPicture picture, float opacity)
    {}

    /** The open editor, or null. One taken off the screen by anything but {@link #close()} counts as closed. */
    public static UIGraffitiEditor getCurrent()
    {
        if (current != null && !current.hasParent())
        {
            current.finishStroke();
            current = null;
        }

        return current;
    }

    public static boolean isOpen()
    {
        return getCurrent() != null;
    }

    /** Ends the stroke on any release of the button, wherever in the menu it happens. */
    public static void mouseReleased()
    {
        UIGraffitiEditor editor = getCurrent();

        if (editor != null)
        {
            editor.releaseMouse();
        }
    }

    public static void open(UIContext context, GraffitiSession session)
    {
        UIGraffitiEditor previous = getCurrent();

        if (previous != null)
        {
            previous.close();
        }

        UIGraffitiEditor editor = new UIGraffitiEditor(session);

        editor.full(context.menu.overlay);
        context.menu.overlay.add(editor);
        editor.resize();
        current = editor;
    }

    /** The previous frame under the one being painted, for the graffiti it was rendered as. */
    public static Onion onionSkin(GraffitiForm rendered)
    {
        UIGraffitiEditor editor = getCurrent();

        if (editor == null || editor.flat || !editor.session.isFilm()
            || !editor.session.path.equals(FormUtils.getPropertyPath(rendered.pixelArt)))
        {
            return null;
        }

        float opacity = editor.session.getSetting((form) -> form.onionSkin, 0F);
        GraffitiPicture previous = opacity <= 0F ? null : editor.session.getPreviousPicture();

        return previous == null ? null : new Onion(previous, opacity);
    }

    private UIGraffitiEditor(GraffitiSession session)
    {
        this.session = session;
        this.flat = !session.isFilm();

        /* Hovering the editor hides the tooltips of the film editor under it. */
        this.markContainer();
        this.keyboardEventPropagataion(EventPropagation.BLOCK);

        GraffitiPicture picture = session.getPicture();

        this.newWidth = picture == null ? GraffitiPicture.DEFAULT_SIZE : picture.width;
        this.newHeight = picture == null ? GraffitiPicture.DEFAULT_SIZE : picture.height;

        /* Top bar */
        UIElement topBar = new UIElement();
        UIIcon view = new UIIcon(() -> this.flat ? Icons.IMAGE : Icons.SCENE, (b) -> this.toggleView());
        UIIcon flight = new UIIcon(Icons.PLANE, (b) -> this.toggleFlight());
        UIIcon undo = new UIIcon(Icons.UNDO, (b) -> session.undo());
        UIIcon redo = new UIIcon(Icons.REDO, (b) -> session.redo());
        UIIcon close = new UIIcon(Icons.CLOSE, (b) -> this.close());

        view.tooltip(GraffitiKeys.VIEW_SCENE, Direction.BOTTOM);
        flight.tooltip(GraffitiKeys.FLIGHT, Direction.BOTTOM);
        flight.highlight(session::isFlying, Direction.BOTTOM);
        undo.tooltip(GraffitiKeys.UNDO, Direction.BOTTOM);
        redo.tooltip(GraffitiKeys.REDO, Direction.BOTTOM);
        close.tooltip(GraffitiKeys.CLOSE, Direction.BOTTOM);
        view.setEnabled(session.isFilm());
        flight.setEnabled(session.isFilm());
        undo.setEnabled(session.isFilm());
        redo.setEnabled(session.isFilm());

        UIElement topIcons = UI.row(0, 0, 20, view, flight, undo, redo, close);

        topBar.relative(this).w(1F).h(TOP);
        topIcons.relative(topBar).x(1F, -4).y(2).w(100).h(20).anchorX(1F);
        topBar.add(topIcons);

        /* Tools */
        List<UIElement> toolIcons = new ArrayList<>();

        for (Tool value : Tool.values())
        {
            UIIcon icon = new UIIcon(value.icon, (b) -> tool = value);

            icon.highlight(() -> tool == value, Direction.RIGHT);
            icon.tooltip(GraffitiKeys.tool(value), Direction.RIGHT);
            toolIcons.add(icon);
        }

        UIIcon glow = new UIIcon(Icons.SUN, (b) -> glowPaint = !glowPaint);
        UIIcon filled = new UIIcon(Icons.SHAPES, (b) -> filledShapes = !filledShapes);

        glow.highlight(() -> glowPaint, Direction.RIGHT);
        glow.tooltip(GraffitiKeys.GLOW_PAINT, Direction.RIGHT);
        filled.highlight(() -> filledShapes, Direction.RIGHT);
        filled.tooltip(GraffitiKeys.FILLED_SHAPES, Direction.RIGHT);
        toolIcons.add(glow);
        toolIcons.add(filled);

        UIElement toolColumn = UI.column(0, 2, 20, toolIcons.toArray(new UIElement[0]));

        toolColumn.relative(this).y(TOP).w(TOOLS).h(1F, -TOP);

        /* Sidebar */
        UIScrollView sidebar = UI.scrollView(UIConstants.MARGIN, 6);

        sidebar.scroll.cancelScrolling();
        sidebar.relative(this).x(1F).y(TOP).w(SIDEBAR).h(1F, -TOP).anchorX(1F);
        this.buildSidebar(sidebar);

        this.add(topBar, toolColumn, sidebar);

        /* Timeline */
        this.timeline = session.isFilm() ? new UIGraffitiFrames(this) : null;

        if (this.timeline != null)
        {
            this.timeline.relative(this).x(TOOLS).y(1F, -BOTTOM).w(1F, -TOOLS - SIDEBAR).h(BOTTOM);
            this.add(this.timeline);
        }
    }

    private void buildSidebar(UIScrollView sidebar)
    {
        UIColor primary = new UIColor(CanvasViewportController::setColor).withAlpha();
        UIColor secondary = new UIColor((color) -> CanvasViewportController.brushes().setSecondary(color)).withAlpha();
        UIIcon swap = new UIIcon(Icons.EXCHANGE, (b) -> CanvasViewportController.brushes().swapColors());
        UIIcon eyedropper = new UIIcon(Icons.EYEDROPPER, (b) -> CanvasEyedropper.toggle());
        UITrackpad opacity = new UITrackpad((value) -> paintOpacity = value.floatValue()).limit(0, 1);

        primary.valueBinding(() -> primary.setColor(CanvasViewportController.getColor()));
        primary.tooltip(BbsFspKeys.COLOR, Direction.BOTTOM);
        secondary.valueBinding(() -> secondary.setColor(CanvasViewportController.brushes().getSecondary()));
        secondary.tooltip(BbsFspKeys.SECONDARY_COLOR, Direction.BOTTOM);
        swap.tooltip(BbsFspKeys.SWAP_COLORS, Direction.BOTTOM);
        swap.w(20);
        eyedropper.highlight(CanvasEyedropper::isArmed, Direction.BOTTOM);
        eyedropper.tooltip(BbsFspKeys.EYEDROPPER, Direction.BOTTOM);
        eyedropper.w(20);
        opacity.valueBinding(() -> opacity.setValue(paintOpacity));

        /* Layers */
        UIIcon add = this.layerIcon(Icons.ADD, GraffitiKeys.LAYER_ADD, (picture) -> this.addLayer(picture, false));
        UIIcon addGlow = this.layerIcon(Icons.SUN, GraffitiKeys.LAYER_ADD_GLOW, (picture) -> this.addLayer(picture, true));
        UIIcon duplicate = this.layerIcon(Icons.DUPE, GraffitiKeys.LAYER_DUPLICATE,
            (picture) -> this.selectNew(picture.duplicateLayer(this.getSelectedLayer(picture).id)));
        UIIcon remove = this.layerIcon(Icons.REMOVE, GraffitiKeys.LAYER_REMOVE,
            (picture) -> picture.removeLayer(this.getSelectedLayer(picture).id));
        UIIcon up = this.layerIcon(Icons.MOVE_UP, GraffitiKeys.LAYER_UP,
            (picture) -> picture.moveLayer(this.getSelectedLayer(picture).id, 1));
        UIIcon down = this.layerIcon(Icons.MOVE_DOWN, GraffitiKeys.LAYER_DOWN,
            (picture) -> picture.moveLayer(this.getSelectedLayer(picture).id, -1));
        UIIcon merge = this.layerIcon(Icons.DOWNLOAD, GraffitiKeys.LAYER_MERGE,
            (picture) -> picture.mergeDown(this.getSelectedLayer(picture).id));
        UITrackpad layerOpacity = new UITrackpad((value) -> this.editLayer(null, (layer) -> layer.opacity = value.floatValue()))
            .limit(0, 1);

        layerOpacity.valueBinding(() ->
        {
            GraffitiPicture picture = this.getPicture();

            if (picture != null)
            {
                layerOpacity.setValue(this.getSelectedLayer(picture).opacity);
            }
        });

        /* Picture */
        UITrackpad width = new UITrackpad((value) -> this.newWidth = value.intValue()).limit(1, GraffitiPicture.MAX_SIZE, true);
        UITrackpad height = new UITrackpad((value) -> this.newHeight = value.intValue()).limit(1, GraffitiPicture.MAX_SIZE, true);
        UIButton resize = new UIButton(GraffitiKeys.RESIZE,
            (b) -> this.session.editAll((picture) -> picture.resize(this.newWidth, this.newHeight, 0.5F, 0.5F)));
        UIIcon clear = this.layerIcon(Icons.TRASH, GraffitiKeys.CLEAR, (picture) -> this.editSelected(picture, (layer, w, h) ->
            Arrays.fill(layer.pixels, 0)));
        UIIcon flipH = this.layerIcon(Icons.HORIZONTAL, GraffitiKeys.FLIP_H, (picture) -> this.editSelected(picture, GraffitiRaster::flipHorizontal));
        UIIcon flipV = this.layerIcon(Icons.VERTICAL, GraffitiKeys.FLIP_V, (picture) -> this.editSelected(picture, GraffitiRaster::flipVertical));
        UIIcon blurLayer = this.layerIcon(Icons.DROP, GraffitiKeys.BLUR_LAYER, (picture) -> this.editSelected(picture, (layer, w, h) ->
            CanvasRasterTools.blur(layer, w, h, Math.max(1, CanvasViewportController.brushes().getBrush().size / 2), 0F)));

        width.valueBinding(() -> width.setValue(this.newWidth));
        height.valueBinding(() -> height.setValue(this.newHeight));

        sidebar.add(
            UI.label(GraffitiKeys.COLOR),
            UI.row(primary, swap, secondary, eyedropper),
            UI.labelRow(BbsFspKeys.OPACITY, opacity),
            new UIGraffitiPalette(),
            UI.label(BbsFspKeys.BRUSH).marginTop(UIConstants.SECTION_GAP),
            new UICanvasBrushEditor(() -> paintOpacity),
            UI.label(GraffitiKeys.LAYERS).marginTop(UIConstants.SECTION_GAP),
            new UIGraffitiLayers(this),
            UI.row(0, 0, 20, add, addGlow, duplicate, remove, up, down, merge),
            UI.labelRow(GraffitiKeys.LAYER_OPACITY, layerOpacity),
            UI.label(GraffitiKeys.PICTURE).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(GraffitiKeys.WIDTH, width),
            UI.labelRow(GraffitiKeys.HEIGHT, height),
            resize,
            UI.row(0, 0, 20, clear, flipH, flipV, blurLayer)
        );

        if (this.session.isFilm())
        {
            UIIcon previous = this.icon(Icons.FRAME_PREV, GraffitiKeys.FRAME_PREVIOUS, () -> this.session.seekBy(-1));
            UIIcon next = this.icon(Icons.FRAME_NEXT, GraffitiKeys.FRAME_NEXT, () -> this.session.seekBy(1));
            UIIcon back = this.icon(Icons.MOVE_LEFT, GraffitiKeys.TICK_PREVIOUS, () -> this.session.step(-1));
            UIIcon forward = this.icon(Icons.MOVE_RIGHT, GraffitiKeys.TICK_NEXT, () -> this.session.step(1));
            UIIcon play = new UIIcon(() -> this.session.isPlaying() ? Icons.PAUSE : Icons.PLAY, (b) -> this.session.togglePlayback());
            UIIcon addFrame = this.icon(Icons.ADD, GraffitiKeys.FRAME_ADD, () -> this.addFrame(false));
            UIIcon duplicateFrame = this.icon(Icons.DUPE, GraffitiKeys.FRAME_DUPLICATE, () -> this.addFrame(true));
            UIIcon removeFrame = this.icon(Icons.REMOVE, GraffitiKeys.FRAME_REMOVE, this.session::removeFrame);
            UITrackpad length = new UITrackpad((value) -> frameLength = value.intValue()).limit(1, 200, true);

            play.tooltip(GraffitiKeys.PLAY, Direction.BOTTOM);
            length.tooltip(GraffitiKeys.FRAME_LENGTH_TOOLTIP, Direction.LEFT);
            length.valueBinding(() -> length.setValue(frameLength));

            sidebar.add(
                UI.label(GraffitiKeys.FRAMES).marginTop(UIConstants.SECTION_GAP),
                UI.row(0, 0, 20, previous, next, back, forward, play),
                UI.row(0, 0, 20, addFrame, duplicateFrame, removeFrame),
                UI.labelRow(GraffitiKeys.FRAME_LENGTH, length)
            );
        }

        sidebar.add(GraffitiControls.effects(this::getSession));
        sidebar.add(GraffitiControls.animation(this::getSession));
        sidebar.add(GraffitiControls.view(this::getSession));
    }

    private UIIcon icon(Icon icon, IKey tooltip, Runnable action)
    {
        UIIcon button = new UIIcon(icon, (b) -> action.run());

        button.tooltip(tooltip, Direction.BOTTOM);

        return button;
    }

    private UIIcon layerIcon(Icon icon, IKey tooltip, Consumer<GraffitiPicture> edit)
    {
        return this.icon(icon, tooltip, () -> this.session.edit(edit));
    }

    private void addLayer(GraffitiPicture picture, boolean glow)
    {
        CanvasLayerData layer = picture.addLayer(picture.indexOf(this.getSelectedLayer(picture).id) + 1, glow);

        if (layer != null && glow)
        {
            layer.name = GraffitiKeys.LAYER_GLOW_NAME.get();
        }

        this.selectNew(layer);
    }

    private void selectNew(CanvasLayerData layer)
    {
        if (layer != null)
        {
            this.selectedLayer = layer.id;
        }
    }

    private void editSelected(GraffitiPicture picture, LayerEdit edit)
    {
        CanvasLayerData layer = this.getSelectedLayer(picture);

        edit.apply(layer, picture.width, picture.height);
        layer.touch();
    }

    private interface LayerEdit
    {
        void apply(CanvasLayerData layer, int width, int height);
    }

    /* Data */

    public GraffitiSession getSession()
    {
        return this.session;
    }

    public boolean isFlat()
    {
        return this.flat;
    }

    /** The middle of the editor, between the bars. */
    public Area getCenter()
    {
        this.updateAreas();

        return this.center;
    }

    /** Where the scene is drawn, the area the graffiti's captured matrices map onto. */
    public Area getViewport()
    {
        this.updateAreas();

        return this.viewport;
    }

    public GraffitiPicture getPicture()
    {
        return this.session.getPicture();
    }

    public CanvasLayerData getSelectedLayer(GraffitiPicture picture)
    {
        CanvasLayerData layer = this.selectedLayer == null ? null : picture.findLayer(this.selectedLayer);

        return layer == null ? picture.layers.get(picture.layers.size() - 1) : layer;
    }

    public void selectLayer(String id)
    {
        this.selectedLayer = id;
    }

    public String layerName(CanvasLayerData layer)
    {
        return layer.name.matches("[0-9]+") ? GraffitiKeys.LAYER_NAME.format(layer.name).get() : layer.name;
    }

    /** Changes one layer (the selected one when the id is null) as an undoable edit. */
    public void editLayer(String id, Consumer<CanvasLayerData> edit)
    {
        this.session.edit((picture) ->
        {
            CanvasLayerData layer = id == null ? this.getSelectedLayer(picture) : picture.findLayer(id);

            if (layer != null)
            {
                edit.accept(layer);
                layer.touch();
            }
        });
    }

    private void addFrame(boolean duplicate)
    {
        this.finishStroke();
        this.session.addFrame(duplicate, frameLength);
    }

    private void toggleView()
    {
        if (this.session.isFilm())
        {
            this.flat = !this.flat;
        }
    }

    private void toggleFlight()
    {
        if (this.session.isFilm())
        {
            OrbitCamera orbit = this.session.panel.dashboard.orbit;

            /* Keys held when the flight changes would keep the camera drifting on its own. */
            orbit.reset();
            orbit.release();
            this.looking = false;
            this.session.toggleFlight();
        }
    }

    public void close()
    {
        this.releaseMouse();
        this.session.end();
        this.removeFromParent();

        if (current == this)
        {
            current = null;
        }
    }

    /* Painting */

    private GraffitiScene.Cell cellAt(int mouseX, int mouseY)
    {
        GraffitiPicture picture = this.getPicture();

        if (picture == null)
        {
            return null;
        }

        if (this.flat)
        {
            float scale = this.flatScale(picture);
            float u = (mouseX - this.flatX(picture, scale)) / (picture.width * scale);
            float v = (mouseY - this.flatY(picture, scale)) / (picture.height * scale);

            return new GraffitiScene.Cell((int) Math.floor(u * picture.width), (int) Math.floor(v * picture.height), u, v);
        }

        GraffitiScene.Capture capture = GraffitiScene.get(this.session.path);

        return capture == null ? null : capture.cellAt(this.viewport, mouseX, mouseY);
    }

    private static boolean inside(GraffitiPicture picture, GraffitiScene.Cell cell)
    {
        return cell != null && cell.x() >= 0 && cell.y() >= 0 && cell.x() < picture.width && cell.y() < picture.height;
    }

    private void beginStroke(GraffitiScene.Cell cell)
    {
        GraffitiPicture shown = this.getPicture();

        if (shown == null || !inside(shown, cell))
        {
            return;
        }

        if (tool == Tool.PICKER || Window.isAltPressed())
        {
            int color = GraffitiRaster.colorAt(shown, cell.x(), cell.y());

            if ((color >>> 24) != 0)
            {
                CanvasViewportController.setColor(color);
            }

            return;
        }

        GraffitiPicture picture = this.session.begin();

        if (picture == null)
        {
            return;
        }

        CanvasLayerData layer = this.paintLayer(picture);
        CanvasBrushSet brushes = CanvasViewportController.brushes();
        int color = brushes.getPrimary();

        this.strokePicture = picture;
        this.strokeLayer = layer;
        this.snapshot = layer.pixels.clone();
        this.startX = this.lastX = cell.x();
        this.startY = this.lastY = cell.y();

        if (tool == Tool.BRUSH || tool == Tool.ERASER)
        {
            boolean eraser = tool == Tool.ERASER || brushes.isEraser();

            if (!eraser)
            {
                remember(color);
            }

            this.stroke = new CanvasStroke(layer, picture.width, picture.height, brushes.getPattern(), brushes.getBrush(),
                eraser, color, brushes.getSecondary(), paintOpacity, System.nanoTime());
            this.stroke.moveTo(cell.x(), cell.y());
        }
        else if (tool == Tool.FILL)
        {
            remember(color);
            CanvasRasterTools.fill(layer, picture.width, picture.height, cell.x(), cell.y(), withOpacity(color));
            this.finishStroke();

            return;
        }
        else if (tool == Tool.BLUR)
        {
            this.dab(cell.x(), cell.y());
        }
        else if (tool == Tool.LINE || tool == Tool.RECTANGLE || tool == Tool.ELLIPSE)
        {
            remember(color);
            this.shape(cell.x(), cell.y());
        }

        this.touchStroke();
    }

    private void continueStroke(GraffitiScene.Cell cell)
    {
        if (this.strokeLayer == null || cell == null || cell.x() == this.lastX && cell.y() == this.lastY)
        {
            return;
        }

        int width = this.strokePicture.width;
        int height = this.strokePicture.height;

        if (this.stroke != null)
        {
            this.stroke.moveTo(cell.x(), cell.y());
        }
        else if (tool == Tool.BLUR)
        {
            this.dab(cell.x(), cell.y());
        }
        else if (tool == Tool.MOVE)
        {
            GraffitiRaster.shift(this.strokeLayer, this.snapshot, width, height, cell.x() - this.startX, cell.y() - this.startY);
        }
        else if (tool == Tool.LINE || tool == Tool.RECTANGLE || tool == Tool.ELLIPSE)
        {
            this.shape(cell.x(), cell.y());
        }

        this.lastX = cell.x();
        this.lastY = cell.y();
        this.touchStroke();
    }

    private void dab(int x, int y)
    {
        int size = Math.max(1, CanvasViewportController.brushes().getBrush().size / 2);

        GraffitiRaster.blurDab(this.strokeLayer, this.strokeLayer.pixels.clone(), this.strokePicture.width,
            this.strokePicture.height, x, y, size, Math.max(0.1F, paintOpacity));
    }

    private void shape(int x, int y)
    {
        int width = this.strokePicture.width;
        int height = this.strokePicture.height;
        int color = withOpacity(CanvasViewportController.getColor());

        System.arraycopy(this.snapshot, 0, this.strokeLayer.pixels, 0, this.snapshot.length);

        if (tool == Tool.LINE)
        {
            CanvasRasterTools.line(this.strokeLayer, width, height, this.startX, this.startY, x, y, color);
        }
        else if (tool == Tool.RECTANGLE)
        {
            CanvasRasterTools.rectangle(this.strokeLayer, width, height, this.startX, this.startY, x, y, color, filledShapes);
        }
        else
        {
            CanvasRasterTools.ellipse(this.strokeLayer, width, height, this.startX, this.startY, x, y, color, filledShapes);
        }
    }

    private void touchStroke()
    {
        if (this.strokeLayer != null)
        {
            this.strokeLayer.touch();
            this.strokePicture.touch();
        }
    }

    private void finishStroke()
    {
        if (this.strokeLayer != null)
        {
            this.touchStroke();
            this.session.end();
        }

        this.stroke = null;
        this.strokeLayer = null;
        this.strokePicture = null;
        this.snapshot = null;
    }

    private void releaseMouse()
    {
        if (this.timeline != null)
        {
            this.timeline.release();
        }

        if (this.looking)
        {
            this.session.panel.dashboard.orbit.release();
            this.looking = false;
        }

        this.panning = false;
        this.finishStroke();
    }

    /** The layer a stroke lands on: the selected one, or with glowing paint the glowing layer nearest above it. */
    private CanvasLayerData paintLayer(GraffitiPicture picture)
    {
        CanvasLayerData selected = this.getSelectedLayer(picture);

        if (!glowPaint || selected.glow)
        {
            return selected;
        }

        int index = picture.indexOf(selected.id);

        for (int i = index + 1; i < picture.layers.size(); i++)
        {
            if (picture.layers.get(i).glow)
            {
                this.selectedLayer = picture.layers.get(i).id;

                return picture.layers.get(i);
            }
        }

        CanvasLayerData layer = picture.addLayer(index + 1, true);

        if (layer == null)
        {
            return selected;
        }

        layer.name = GraffitiKeys.LAYER_GLOW_NAME.get();
        this.selectedLayer = layer.id;

        return layer;
    }

    private static int withOpacity(int color)
    {
        int alpha = Math.round(((color >>> 24) & 255) * paintOpacity);

        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static void remember(int color)
    {
        RECENT.remove((Integer) color);
        RECENT.add(0, color);

        while (RECENT.size() > RECENT_LIMIT)
        {
            RECENT.remove(RECENT.size() - 1);
        }
    }

    /* Layout */

    private float flatScale(GraffitiPicture picture)
    {
        float fit = Math.min(this.center.w * 0.9F / picture.width, this.center.h * 0.9F / picture.height);

        return Math.max(0.05F, fit * this.zoom);
    }

    private float flatX(GraffitiPicture picture, float scale)
    {
        return this.center.mx() - picture.width * scale / 2F + this.panX;
    }

    private float flatY(GraffitiPicture picture, float scale)
    {
        return this.center.my() - picture.height * scale / 2F + this.panY;
    }

    private void updateAreas()
    {
        int bottom = this.session.isFilm() ? BOTTOM : 0;

        this.center.set(this.area.x + TOOLS, this.area.y + TOP, this.area.w - TOOLS - SIDEBAR, this.area.h - TOP - bottom);

        int videoWidth = Math.max(1, BBSRendering.getVideoWidth());
        int videoHeight = Math.max(1, BBSRendering.getVideoHeight());
        Vector2i size = Vectors.resize(videoWidth / (float) videoHeight, this.center.w, this.center.h);

        this.viewport.set(this.center.mx() - size.x / 2, this.center.my() - size.y / 2, size.x, size.y);
    }

    /* Input */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        this.updateAreas();

        if (!this.center.isInside(context))
        {
            return true;
        }

        if (context.mouseButton == 0)
        {
            /* Past the frame of the scene the camera sees nothing to paint on. */
            if (this.flat || this.viewport.isInside(context))
            {
                this.beginStroke(this.cellAt(context.mouseX, context.mouseY));
            }
        }
        else if (this.flat)
        {
            this.panning = true;
            this.lastMouseX = context.mouseX;
            this.lastMouseY = context.mouseY;
        }
        else if (context.mouseButton == 1 && this.session.isFlying())
        {
            /* Looking around is the flight camera's left-button drag, on the right button here,
             * since the left one paints. */
            this.session.panel.dashboard.orbit.start(0, context.mouseX, context.mouseY);
            this.looking = true;
        }

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.releaseMouse();

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.center.isInside(context))
        {
            return true;
        }

        if (this.flat)
        {
            float previous = this.zoom;

            this.zoom = Math.max(0.25F, Math.min(64F, this.zoom * (context.mouseWheel > 0 ? 1.25F : 0.8F)));

            float ratio = this.zoom / previous;

            this.panX = (this.panX - (context.mouseX - this.center.mx())) * ratio + (context.mouseX - this.center.mx());
            this.panY = (this.panY - (context.mouseY - this.center.my())) * ratio + (context.mouseY - this.center.my());
        }
        else if (this.session.isFlying())
        {
            this.session.panel.dashboard.orbit.scroll((int) Math.signum(context.mouseWheel));
        }

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        /* A number being typed into a field of the sidebar: "." there is not the next frame.
         * A flight key let go meanwhile still stops the camera. */
        if (context.isFocused())
        {
            if (this.session.isFlying() && context.getKeyAction() == KeyAction.RELEASED)
            {
                this.session.panel.dashboard.orbit.keyPressed(context);
            }

            return true;
        }

        /* While flying, the flight keys are the camera's alone, held, repeated or let go. */
        if (this.session.isFlying())
        {
            this.session.panel.dashboard.orbit.keyPressed(context);

            if (isFlightKey(context.getKeyCode()))
            {
                return true;
            }
        }

        KeyAction action = context.getKeyAction();

        if (action == KeyAction.RELEASED)
        {
            return true;
        }

        int key = context.getKeyCode();
        boolean pressed = action == KeyAction.PRESSED;

        if (Window.isCtrlPressed())
        {
            if (pressed && key == GLFW.GLFW_KEY_Z)
            {
                if (Window.isShiftPressed())
                {
                    this.session.redo();
                }
                else
                {
                    this.session.undo();
                }
            }
            else if (pressed && key == GLFW.GLFW_KEY_Y)
            {
                this.session.redo();
            }
            else if (pressed && key == GLFW.GLFW_KEY_S && this.session.isFilm())
            {
                this.session.panel.save();
            }

            return true;
        }

        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)
        {
            this.session.step(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);

            return true;
        }

        if (key == GLFW.GLFW_KEY_COMMA || key == GLFW.GLFW_KEY_PERIOD)
        {
            this.session.seekBy(key == GLFW.GLFW_KEY_COMMA ? -1 : 1);

            return true;
        }

        if (key == GLFW.GLFW_KEY_LEFT_BRACKET || key == GLFW.GLFW_KEY_RIGHT_BRACKET)
        {
            CanvasBrushSet brushes = CanvasViewportController.brushes();

            brushes.getBrush().size = CanvasBrush.clampSize(brushes.getBrush().size + (key == GLFW.GLFW_KEY_LEFT_BRACKET ? -1 : 1));
            brushes.touch();

            return true;
        }

        if (!pressed)
        {
            return true;
        }

        switch (key)
        {
            case GLFW.GLFW_KEY_ESCAPE -> this.close();
            case GLFW.GLFW_KEY_F -> this.toggleFlight();
            case GLFW.GLFW_KEY_V -> this.toggleView();
            case GLFW.GLFW_KEY_SPACE -> this.session.togglePlayback();
            case GLFW.GLFW_KEY_N -> this.addFrame(Window.isShiftPressed());
            default ->
            {
                for (Tool value : Tool.values())
                {
                    if (value.key == key)
                    {
                        tool = value;
                    }
                }
            }
        }

        return true;
    }

    private static boolean isFlightKey(int key)
    {
        for (KeyCombo combo : FLIGHT_KEYS)
        {
            if (combo.getMainKey() == key)
            {
                return true;
            }
        }

        return false;
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        if (!this.session.isValid())
        {
            this.close();

            return;
        }

        CanvasBrushStore.saveLater();
        this.updateAreas();

        if (this.strokeLayer != null)
        {
            this.continueStroke(this.cellAt(context.mouseX, context.mouseY));
        }

        if (this.panning)
        {
            this.panX += context.mouseX - this.lastMouseX;
            this.panY += context.mouseY - this.lastMouseY;
            this.lastMouseX = context.mouseX;
            this.lastMouseY = context.mouseY;
        }

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF0C0C0C);

        GraffitiPicture picture = this.getPicture();

        context.batcher.clip(this.center, context);

        if (this.flat)
        {
            this.renderFlat(context, picture);
        }
        else
        {
            this.renderScene(context, picture);
        }

        context.batcher.unclip(context);

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + TOP, PANEL_COLOR);
        context.batcher.box(this.area.x, this.area.y + TOP, this.area.x + TOOLS, this.area.ey(), PANEL_COLOR);
        context.batcher.box(this.area.ex() - SIDEBAR, this.area.y + TOP, this.area.ex(), this.area.ey(), PANEL_COLOR);
        context.batcher.text(this.title(), this.area.x + 6, this.area.y + 8, Colors.WHITE, true);

        super.render(context);
    }

    private String title()
    {
        String title = GraffitiKeys.TITLE.get() + " — " + GraffitiKeys.PIXEL_ART.get() + " — " + GraffitiKeys.tool(tool).get();

        if (this.session.isFilm())
        {
            List<Keyframe<GraffitiPicture>> frames = this.session.getFrames();
            int index = frames.indexOf(this.session.getCurrentFrame());

            title += "   " + GraffitiKeys.FRAME_INFO.format(index + 1, frames.size(), this.session.getTick()).get();
        }

        return title;
    }

    private void renderFlat(UIContext context, GraffitiPicture picture)
    {
        if (picture == null)
        {
            return;
        }

        float scale = this.flatScale(picture);
        float x = this.flatX(picture, scale);
        float y = this.flatY(picture, scale);
        float w = picture.width * scale;
        float h = picture.height * scale;
        Onion onion = this.session.isFilm() ? new Onion(this.session.getPreviousPicture(), this.session.getSetting((form) -> form.onionSkin, 0F)) : null;

        context.batcher.iconArea(Icons.CHECKBOARD, x, y, w, h);

        if (onion != null && onion.picture() != null && onion.opacity() > 0F
            && onion.picture().width == picture.width && onion.picture().height == picture.height)
        {
            this.texture(context, GraffitiTextures.Kind.ONION, onion.picture(), new GraffitiTextures.Settings(0F, 0F, 0F, onion.opacity()), x, y, w, h);
        }

        this.texture(context, GraffitiTextures.Kind.ALL, picture, GraffitiTextures.Settings.NONE, x, y, w, h);

        float grid = this.session.getSetting((form) -> form.gridOpacity, 0F);

        if (grid > 0F && scale >= 3F)
        {
            int color = Colors.setA(GRID_COLOR, grid);

            for (int i = 0; i <= picture.width; i++)
            {
                float px = x + i * scale;

                context.batcher.box(px, y, px + 1, y + h, color);
            }

            for (int i = 0; i <= picture.height; i++)
            {
                float py = y + i * scale;

                context.batcher.box(x, py, x + w, py + 1, color);
            }
        }

        GraffitiScene.Cell hover = this.center.isInside(context) ? this.cellAt(context.mouseX, context.mouseY) : null;

        if (inside(picture, hover))
        {
            int[] footprint = this.footprint(hover);

            context.batcher.outline(x + footprint[0] * scale, y + footprint[1] * scale,
                x + footprint[2] * scale, y + footprint[3] * scale, this.cursorColor());
        }
    }

    private void texture(UIContext context, GraffitiTextures.Kind kind, GraffitiPicture picture, GraffitiTextures.Settings settings,
        float x, float y, float w, float h)
    {
        Texture texture = GraffitiTextures.get(this, kind, picture, settings).texture();

        if (texture != null)
        {
            context.batcher.texturedBox(texture, Colors.WHITE, x, y, w, h, 0, 0, texture.width, texture.height,
                texture.width, texture.height);
        }
    }

    private void renderScene(UIContext context, GraffitiPicture picture)
    {
        Texture world = BBSRendering.getTexture();

        context.batcher.box(this.center.x, this.center.y, this.center.ex(), this.center.ey(), 0xFF000000);

        if (world != null)
        {
            context.batcher.texturedBox(world.id, Colors.WHITE, this.viewport.x, this.viewport.y, this.viewport.w, this.viewport.h,
                0, world.height, world.width, 0, world.width, world.height);
        }

        GraffitiScene.Capture capture = GraffitiScene.get(this.session.path);

        if (capture == null || picture == null)
        {
            this.centeredCard(context, GraffitiKeys.NOT_IN_VIEW.get(), this.center.my());
        }
        else
        {
            /* A graffiti close to the camera reaches past the frame, the grid stays inside it. */
            context.batcher.clip(this.viewport, context);
            renderGrid(context, capture, this.viewport, this.session.getSetting((form) -> form.gridOpacity, 0F));

            GraffitiScene.Cell hover = this.viewport.isInside(context) ? capture.cellAt(this.viewport, context.mouseX, context.mouseY) : null;

            if (inside(picture, hover))
            {
                int[] footprint = this.footprint(hover);

                quad(context, capture, this.viewport, footprint[0] / (float) picture.width, footprint[1] / (float) picture.height,
                    footprint[2] / (float) picture.width, footprint[3] / (float) picture.height, this.cursorColor());
            }

            context.batcher.unclip(context);
        }

        if (this.session.isFlying())
        {
            this.centeredCard(context, GraffitiKeys.FLIGHT_HINT.get(), this.center.y + 8);
        }
    }

    private void centeredCard(UIContext context, String text, int y)
    {
        context.batcher.textCard(text, this.center.mx() - context.batcher.getFont().getWidth(text) / 2F, y);
    }

    /** The cells the brush covers around the hovered one, as x1, y1, x2, y2 (exclusive). */
    private int[] footprint(GraffitiScene.Cell cell)
    {
        int size = tool == Tool.BRUSH || tool == Tool.ERASER || tool == Tool.BLUR
            ? CanvasViewportController.brushes().getBrush().size
            : 1;
        int half = (size - 1) / 2;

        return new int[] {cell.x() - half, cell.y() - half, cell.x() - half + size, cell.y() - half + size};
    }

    private int cursorColor()
    {
        return tool == Tool.ERASER ? 0xC0FFFFFF : Colors.setA(CanvasViewportController.getColor(), 0.9F);
    }

    /** The pixel grid drawn along the graffiti as the camera sees it, skipped while its cells are too small to tell apart. */
    public static void renderGrid(UIContext context, GraffitiScene.Capture capture, Area viewport, float opacity)
    {
        float[] a = capture.project(viewport, 0F, 0F);
        float[] b = capture.project(viewport, 1F, 0F);
        float[] c = capture.project(viewport, 0F, 1F);

        if (opacity <= 0F || a == null || b == null || c == null)
        {
            return;
        }

        float cellX = (float) Math.hypot(b[0] - a[0], b[1] - a[1]) / capture.width();
        float cellY = (float) Math.hypot(c[0] - a[0], c[1] - a[1]) / capture.height();
        int color = Colors.setA(GRID_COLOR, opacity);

        if (cellX >= 3F)
        {
            for (int i = 0; i <= capture.width(); i++)
            {
                line(context, capture, viewport, i / (float) capture.width(), 0F, i / (float) capture.width(), 1F, color);
            }
        }

        if (cellY >= 3F)
        {
            for (int i = 0; i <= capture.height(); i++)
            {
                line(context, capture, viewport, 0F, i / (float) capture.height(), 1F, i / (float) capture.height(), color);
            }
        }

        quad(context, capture, viewport, 0F, 0F, 1F, 1F, Colors.setA(Colors.WHITE, Math.min(1F, opacity + 0.25F)));
    }

    private static void quad(UIContext context, GraffitiScene.Capture capture, Area viewport, float u1, float v1, float u2, float v2, int color)
    {
        line(context, capture, viewport, u1, v1, u2, v1, color);
        line(context, capture, viewport, u2, v1, u2, v2, color);
        line(context, capture, viewport, u2, v2, u1, v2, color);
        line(context, capture, viewport, u1, v2, u1, v1, color);
    }

    private static void line(UIContext context, GraffitiScene.Capture capture, Area viewport, float u1, float v1, float u2, float v2, int color)
    {
        float[] a = capture.project(viewport, u1, v1);
        float[] b = capture.project(viewport, u2, v2);

        if (a != null && b != null)
        {
            CanvasDraw.line(context.batcher, a[0], a[1], b[0], b[1], 1F, color);
        }
    }

    /** Recent colours and a fixed palette; a click takes the colour. */
    private static final class UIGraffitiPalette extends UIElement
    {
        private static final int SIZE = 14;

        private UIGraffitiPalette()
        {
            this.h(SIZE * 2 + 4);
        }

        @Override
        public void render(UIContext context)
        {
            for (int i = 0; i < PALETTE.length; i++)
            {
                this.swatch(context, i, 0, PALETTE[i]);
            }

            for (int i = 0; i < RECENT.size(); i++)
            {
                this.swatch(context, i, 1, RECENT.get(i));
            }

            super.render(context);
        }

        private void swatch(UIContext context, int column, int row, int color)
        {
            int x = this.area.x + column * SIZE;
            int y = this.area.y + row * (SIZE + 4);

            context.batcher.box(x, y, x + SIZE - 1, y + SIZE - 1, Colors.WHITE);
            context.batcher.box(x + 1, y + 1, x + SIZE - 2, y + SIZE - 2, color);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (!this.area.isInside(context) || context.mouseButton != 0)
            {
                return super.subMouseClicked(context);
            }

            int column = (context.mouseX - this.area.x) / SIZE;
            int row = (context.mouseY - this.area.y) / (SIZE + 4);
            List<Integer> colors = row == 0 ? Arrays.stream(PALETTE).boxed().toList() : RECENT;

            if (column >= 0 && column < colors.size())
            {
                CanvasViewportController.setColor(colors.get(column));
            }

            return true;
        }
    }
}
