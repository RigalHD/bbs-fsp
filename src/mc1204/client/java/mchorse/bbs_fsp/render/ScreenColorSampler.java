package mchorse.bbs_fsp.render;

import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Reads the colour of the finished interface under the cursor. On 1.20.4 the interface draws
 * straight into the framebuffer, so once the menu has painted everything the pixel is there.
 */
public final class ScreenColorSampler
{
    private ScreenColorSampler()
    {}

    /** The opaque RGB under the cursor, or -1 when nothing could be read. */
    public static int sample(UIContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();

        if (width <= 0 || height <= 0 || context.menu.width <= 0)
        {
            return -1;
        }

        context.batcher.flush();

        float scale = width / (float) context.menu.width;
        int x = Math.max(0, Math.min(width - 1, Math.round(context.globalX(context.mouseX) * scale)));
        int y = Math.max(0, Math.min(height - 1, height - 1 - Math.round(context.globalY(context.mouseY) * scale)));

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer floats = stack.mallocFloat(4);

            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floats);

            int red = Math.round(Math.max(0F, Math.min(1F, floats.get(0))) * 255F);
            int green = Math.round(Math.max(0F, Math.min(1F, floats.get(1))) * 255F);
            int blue = Math.round(Math.max(0F, Math.min(1F, floats.get(2))) * 255F);

            return 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
    }
}
