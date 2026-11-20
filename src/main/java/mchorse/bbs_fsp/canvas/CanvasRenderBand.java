package mchorse.bbs_fsp.canvas;

public enum CanvasRenderBand
{
    BEHIND_STRUCTURES,
    BEHIND_ENTITIES,
    FOREGROUND;

    public static CanvasRenderBand fromId(String id)
    {
        for (CanvasRenderBand value : values())
        {
            if (value.name().equals(id))
            {
                return value;
            }
        }

        return FOREGROUND;
    }
}
