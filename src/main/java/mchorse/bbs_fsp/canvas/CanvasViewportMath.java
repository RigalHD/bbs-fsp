package mchorse.bbs_fsp.canvas;

/**
 * Mapping between screen positions and canvas cells. The canvas fills the viewport, is moved by
 * its offset (measured in canvas cells, so the same value lines up at every resolution the frame
 * is drawn at) and is then rotated around the viewport centre.
 */
public final class CanvasViewportMath
{
    private CanvasViewportMath()
    {}

    /** Offset in canvas cells turned into the units of a drawing area {@code size} wide or tall. */
    public static double offsetToArea(double offset, double size, int cells)
    {
        return offset * size / Math.max(1, cells);
    }

    public record Cell(int x, int y)
    {}

    public static Cell cellAt(double mouseX, double mouseY, double viewportX, double viewportY,
        double viewportWidth, double viewportHeight, int canvasWidth, int canvasHeight,
        double offsetX, double offsetY, double rotationDegrees)
    {
        double localX = mouseX - (viewportX + viewportWidth / 2D) - offsetToArea(offsetX, viewportWidth, canvasWidth);
        double localY = mouseY - (viewportY + viewportHeight / 2D) - offsetToArea(offsetY, viewportHeight, canvasHeight);
        double angle = Math.toRadians(-rotationDegrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rotatedX = localX * cos - localY * sin;
        double rotatedY = localX * sin + localY * cos;
        double cellWidth = viewportWidth / canvasWidth;
        double cellHeight = viewportHeight / canvasHeight;
        int x = (int) Math.floor(rotatedX / cellWidth + canvasWidth / 2D);
        int y = (int) Math.floor(rotatedY / cellHeight + canvasHeight / 2D);

        return new Cell(x, y);
    }

    public static double[] screenAt(int cellX, int cellY, double viewportX, double viewportY,
        double viewportWidth, double viewportHeight, int canvasWidth, int canvasHeight,
        double offsetX, double offsetY, double rotationDegrees)
    {
        double cellWidth = viewportWidth / canvasWidth;
        double cellHeight = viewportHeight / canvasHeight;
        double localX = (cellX + 0.5D - canvasWidth / 2D) * cellWidth;
        double localY = (cellY + 0.5D - canvasHeight / 2D) * cellHeight;
        double angle = Math.toRadians(rotationDegrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rotatedX = localX * cos - localY * sin;
        double rotatedY = localX * sin + localY * cos;

        return new double[] {
            viewportX + viewportWidth / 2D + offsetToArea(offsetX, viewportWidth, canvasWidth) + rotatedX,
            viewportY + viewportHeight / 2D + offsetToArea(offsetY, viewportHeight, canvasHeight) + rotatedY
        };
    }
}
