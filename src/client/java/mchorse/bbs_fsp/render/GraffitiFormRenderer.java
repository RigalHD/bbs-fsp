package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiEditor;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.renderers.BillboardFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Draws a graffiti as BBS draws a billboard, once per image: the onion skin of the previous
 * frame while it is being painted, the plain paint lit like the world around it (or at full
 * brightness when the graffiti is unlit), and the
 * glowing paint at full brightness over the same quad (equal depth passes the depth test, so
 * it lands exactly on top). Glowing paint also becomes light through {@link GraffitiLights}.
 */
public class GraffitiFormRenderer extends BillboardFormRenderer<GraffitiForm>
{
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int ICON_SIZE = 32;

    private GraffitiTextures.Kind pass = GraffitiTextures.Kind.PLAIN;
    private GraffitiPicture picture;

    public GraffitiFormRenderer(GraffitiForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.picture = this.form.pixelArt.get();

        if (this.picture == null || this.entry(GraffitiTextures.Kind.PLAIN).texture() == null
            && this.entry(GraffitiTextures.Kind.GLOW).texture() == null)
        {
            context.batcher.scaledIcon(GraffitiForm.ICON, Colors.WHITE, (x1 + x2 - ICON_SIZE) / 2, (y1 + y2 - ICON_SIZE) / 2, ICON_SIZE);

            return;
        }

        this.pass = GraffitiTextures.Kind.PLAIN;
        super.renderInUI(context, x1, y1, x2, y2);
        this.pass = GraffitiTextures.Kind.GLOW;
        super.renderInUI(context, x1, y1, x2, y2);
        this.pass = GraffitiTextures.Kind.PLAIN;
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.picture = this.form.pixelArt.get();

        if (this.picture == null)
        {
            return;
        }

        GraffitiScene.capture(this.form, this.picture, context);

        if (context.isPicking())
        {
            this.pass = GraffitiTextures.Kind.ALL;
            super.render3D(context);
            this.pass = GraffitiTextures.Kind.PLAIN;

            return;
        }

        boolean unlit = this.form.unlit.get();

        if (!context.ui && GraffitiScene.isFocused(context.entity) && UIGraffitiEditor.onionSkin(this.form) != null)
        {
            this.renderPass(context, GraffitiTextures.Kind.ONION, unlit);
        }

        this.renderPass(context, GraffitiTextures.Kind.PLAIN, unlit);

        GraffitiTextures.Entry glow = this.entry(GraffitiTextures.Kind.GLOW);

        if (glow.texture() != null && !BBSRendering.isIrisShadowPass())
        {
            this.renderPass(context, GraffitiTextures.Kind.GLOW, true);
            GraffitiLights.register(this.form, context, glow.lights(), halfWidth(this.picture), halfHeight(this.picture));
        }
    }

    /**
     * One image of the graffiti over the quad. A bright pass ignores the light around it: full
     * brightness and no shading, the way glowing paint and an unlit graffiti are drawn.
     */
    private void renderPass(FormRenderingContext context, GraffitiTextures.Kind kind, boolean bright)
    {
        int light = context.light;
        Boolean shading = this.form.shading.getRuntimeValue();

        this.pass = kind;

        if (bright)
        {
            context.light = FULL_BRIGHT;
            this.form.shading.setRuntimeValue(false);
        }

        try
        {
            super.render3D(context);
        }
        finally
        {
            if (bright)
            {
                this.form.shading.setRuntimeValue(shading);
                context.light = light;
            }

            this.pass = GraffitiTextures.Kind.PLAIN;
        }
    }

    @Override
    protected Texture getTexture()
    {
        return this.entry(this.pass).texture();
    }

    private GraffitiTextures.Entry entry(GraffitiTextures.Kind kind)
    {
        if (kind == GraffitiTextures.Kind.ONION)
        {
            UIGraffitiEditor.Onion onion = UIGraffitiEditor.onionSkin(this.form);

            return GraffitiTextures.get(this, kind, onion == null ? null : onion.picture(),
                new GraffitiTextures.Settings(0F, 0F, 0F, onion == null ? 0F : onion.opacity()));
        }

        return GraffitiTextures.get(this, kind, this.picture, GraffitiTextures.Settings.of(this.form));
    }

    public static float halfWidth(GraffitiPicture picture)
    {
        return 0.5F * (picture.height > picture.width ? picture.width / (float) picture.height : 1F);
    }

    public static float halfHeight(GraffitiPicture picture)
    {
        return 0.5F * (picture.width > picture.height ? picture.height / (float) picture.width : 1F);
    }
}
