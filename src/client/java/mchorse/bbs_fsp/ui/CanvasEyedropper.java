package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.render.ScreenColorSampler;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Screen-wide eyedropper. Once armed, the next left click anywhere in a BBS menu takes the colour
 * of that pixel as the paint colour: the film preview, the canvas, the panels, any swatch. A right
 * click or Escape puts it away. Holding Alt over the canvas keeps it sampling as well, so an
 * Alt-click on an empty canvas cell can fall back to the screen colour.
 */
public final class CanvasEyedropper
{
    private static final int SAMPLE_SIZE = 16;

    private static boolean armed;
    private static boolean canvasHover;
    private static int canvasColor;
    private static boolean swallowRelease;
    private static int sample = -1;
    private static int frame;
    private static int buttonFrame = -1;
    private static int buttonX;
    private static int buttonY;
    private static int buttonW;
    private static int buttonH;

    private CanvasEyedropper()
    {}

    public static boolean isArmed()
    {
        return armed;
    }

    public static void toggle()
    {
        armed = !armed;
        sample = -1;
    }

    public static void disarm()
    {
        armed = false;
        canvasHover = false;
    }

    /**
     * Where the button that arms the eyedropper was drawn this frame, in menu coordinates, so a
     * second press on it puts the eyedropper away instead of picking the button's own colour.
     */
    public static void setButtonArea(int x, int y, int w, int h)
    {
        buttonFrame = frame;
        buttonX = x;
        buttonY = y;
        buttonW = w;
        buttonH = h;
    }

    private static boolean isOverButton(int mouseX, int mouseY)
    {
        return buttonFrame == frame - 1 && mouseX >= buttonX && mouseY >= buttonY
            && mouseX < buttonX + buttonW && mouseY < buttonY + buttonH;
    }

    /**
     * Set every frame by the preview overlay while the cursor is over a selected canvas clip,
     * together with the composited colour of the cell under it, which is what Alt+click takes.
     */
    public static void setCanvasHover(boolean hover, int color)
    {
        canvasHover = hover;
        canvasColor = color;
    }

    private static boolean isSampling()
    {
        return armed || canvasHover && Window.isAltPressed();
    }

    /** Use the last sampled screen colour as the paint colour, keeping the paint's own alpha. */
    public static boolean applyScreenSample()
    {
        if (sample == -1)
        {
            return false;
        }

        int current = CanvasViewportController.getColor();
        int alpha = (current >>> 24) == 0 ? 0xFF000000 : current & 0xFF000000;

        CanvasViewportController.setColor(alpha | (sample & 0x00FFFFFF));

        return true;
    }

    public static boolean mouseClicked(int mouseX, int mouseY, int button)
    {
        if (!armed)
        {
            return false;
        }

        if (button == 0 && !isOverButton(mouseX, mouseY))
        {
            applyScreenSample();
        }

        disarm();
        swallowRelease = true;

        return true;
    }

    public static boolean mouseReleased()
    {
        if (swallowRelease)
        {
            swallowRelease = false;

            return true;
        }

        return false;
    }

    public static boolean keyPressed(int key, int action)
    {
        if (armed && action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_ESCAPE)
        {
            disarm();

            return true;
        }

        return false;
    }

    /**
     * Called once the menu has painted everything for this frame: read the pixel under the cursor
     * before anything of the eyedropper itself is drawn, then show it beside the cursor.
     */
    public static void render(UIContext context)
    {
        boolean sampling = isSampling();
        boolean canvasPick = !armed && canvasHover && (canvasColor >>> 24) != 0;

        canvasHover = false;
        frame += 1;

        if (!sampling)
        {
            return;
        }

        int read = ScreenColorSampler.sample(context);

        if (read != -1)
        {
            sample = read;
        }

        context.requestCursor(GLFW.GLFW_CROSSHAIR_CURSOR);

        if (sample == -1 && !canvasPick)
        {
            return;
        }

        int shown = canvasPick ? canvasColor : sample;
        String label = canvasPick
            ? String.format(Locale.ROOT, "#%08X", shown)
            : String.format(Locale.ROOT, "#%06X", shown & 0x00FFFFFF);
        int width = SAMPLE_SIZE + 4 + context.batcher.getFont().getWidth(label) + 6;
        int x = context.mouseX + 10;
        int y = context.mouseY + 10;

        if (x + width > context.menu.width)
        {
            x = context.mouseX - 10 - width;
        }

        if (y + SAMPLE_SIZE + 1 > context.menu.height)
        {
            y = context.mouseY - 10 - SAMPLE_SIZE;
        }

        context.batcher.box(x - 1, y - 1, x + SAMPLE_SIZE + 1, y + SAMPLE_SIZE + 1, Colors.A100);

        if ((shown >>> 24) < 255)
        {
            context.batcher.iconArea(Icons.CHECKBOARD, x, y, SAMPLE_SIZE, SAMPLE_SIZE);
        }

        context.batcher.box(x, y, x + SAMPLE_SIZE, y + SAMPLE_SIZE, shown);
        context.batcher.textCard(label, x + SAMPLE_SIZE + 4, y + (SAMPLE_SIZE - context.batcher.getFont().getHeight()) / 2F);
    }
}
