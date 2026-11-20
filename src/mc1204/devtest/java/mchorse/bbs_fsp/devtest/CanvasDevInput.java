package mchorse.bbs_fsp.devtest;

/**
 * While the in-game checks run, input comes only from the checks themselves: real mouse moves,
 * clicks and keys over the window would otherwise steer the strokes and the eyedropper.
 */
public final class CanvasDevInput
{
    private static boolean synthetic;
    private static boolean alt;

    private CanvasDevInput()
    {}

    public static boolean isSynthetic()
    {
        return synthetic;
    }

    public static boolean isAltHeld()
    {
        return alt;
    }

    public static void setAlt(boolean held)
    {
        alt = held;
    }

    public static void run(Runnable runnable)
    {
        synthetic = true;

        try
        {
            runnable.run();
        }
        finally
        {
            synthetic = false;
        }
    }
}
