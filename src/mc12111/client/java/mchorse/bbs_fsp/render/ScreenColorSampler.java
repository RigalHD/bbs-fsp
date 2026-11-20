package mchorse.bbs_fsp.render;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.ScreenPixelProbe;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.MinecraftClient;

/**
 * Reads the colour of the finished interface under the cursor. On 1.21.11 the interface is
 * composited after the menu has recorded it, so the read is handed to BBS's screen probe and
 * answered once the frame is complete: the colour returned is one frame old.
 */
public final class ScreenColorSampler
{
    private ScreenColorSampler()
    {}

    /** The opaque RGB under the cursor, or -1 when nothing could be read yet. */
    public static int sample(UIContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();

        if (width <= 0 || height <= 0)
        {
            return -1;
        }

        float scale = BBSModClient.getGUIScale();
        int x = Math.max(0, Math.min(width - 1, Math.round(context.globalX(context.mouseX) * scale)));
        int y = Math.max(0, Math.min(height - 1, height - 1 - Math.round(context.globalY(context.mouseY) * scale)));

        ScreenPixelProbe.request(x, y);

        return ScreenPixelProbe.hasSample() ? ScreenPixelProbe.getSample() : -1;
    }
}
