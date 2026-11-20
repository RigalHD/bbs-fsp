package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.Area;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the graffiti of the replay selected in the film editor ended up on the last rendered
 * frame. The editor paints through it (a cursor is turned into a ray and met with the quad) and
 * the grid is drawn along it, so both follow the graffiti however it is moved, turned or
 * parented, and stay right for any camera.
 */
public final class GraffitiScene
{
    private static final long LIFETIME = 500L;
    private static final Map<String, Capture> CAPTURES = new HashMap<>();

    private GraffitiScene()
    {}

    public record Cell(int x, int y, float u, float v)
    {}

    /**
     * The quad as the frame saw it: the full projection × model view of its plane, and the
     * extent of the quad on it, the same one BBS's billboard gives a picture of that size.
     */
    public record Capture(Matrix4f matrix, Matrix4f inverse, int width, int height, long time)
    {
        public float halfWidth()
        {
            return 0.5F * (this.height > this.width ? this.width / (float) this.height : 1F);
        }

        public float halfHeight()
        {
            return 0.5F * (this.width > this.height ? this.height / (float) this.width : 1F);
        }

        /** The cell under a point of the viewport the frame is shown in, or null when the ray misses the plane. */
        public Cell cellAt(Area viewport, double mouseX, double mouseY)
        {
            float ndcX = (float) ((mouseX - viewport.x) / viewport.w * 2D - 1D);
            float ndcY = (float) (1D - (mouseY - viewport.y) / viewport.h * 2D);
            Vector4f near = this.inverse.transform(new Vector4f(ndcX, ndcY, -0.5F, 1F));
            Vector4f far = this.inverse.transform(new Vector4f(ndcX, ndcY, 0.5F, 1F));

            if (near.w == 0F || far.w == 0F)
            {
                return null;
            }

            near.div(near.w);
            far.div(far.w);

            float dz = near.z - far.z;

            if (Math.abs(dz) < 1e-7F)
            {
                return null;
            }

            float t = near.z / dz;
            float x = near.x + (far.x - near.x) * t;
            float y = near.y + (far.y - near.y) * t;
            float u = (x + this.halfWidth()) / (this.halfWidth() * 2F);
            float v = (this.halfHeight() - y) / (this.halfHeight() * 2F);

            return new Cell((int) Math.floor(u * this.width), (int) Math.floor(v * this.height), u, v);
        }

        /** Where a point of the picture (0..1, v down) lands in the viewport, or null behind the camera. */
        public float[] project(Area viewport, float u, float v)
        {
            Vector4f point = this.matrix.transform(new Vector4f(
                (u * 2F - 1F) * this.halfWidth(),
                (1F - v * 2F) * this.halfHeight(),
                0F, 1F));

            if (point.w <= 1e-5F)
            {
                return null;
            }

            return new float[] {
                viewport.x + (point.x / point.w * 0.5F + 0.5F) * viewport.w,
                viewport.y + (0.5F - point.y / point.w * 0.5F) * viewport.h
            };
        }
    }

    public static boolean isFocused(IEntity entity)
    {
        UIFilmPanel panel = CanvasPlayback.getFilmPanel();

        return panel != null && entity != null && entity == panel.getController().getCurrentEntity();
    }

    public static void capture(GraffitiForm form, GraffitiPicture picture, FormRenderingContext context)
    {
        if (picture == null || context.isPicking() || context.ui || context.stack == null
            || BBSRendering.isIrisShadowPass() || !isFocused(context.entity))
        {
            return;
        }

        Matrix4f model = new Matrix4f(context.stack.peek().getPositionMatrix());

        if (form.billboard.get())
        {
            Vector3f scale = model.getScale(new Vector3f());

            model.m00(scale.x).m01(0).m02(0);
            model.m10(0).m11(scale.y).m12(0);
            model.m20(0).m21(0).m22(scale.z);
        }

        Matrix4f matrix = GraffitiRenderBridge.projection().mul(GraffitiRenderBridge.modelView()).mul(model);
        Matrix4f inverse = new Matrix4f(matrix).invert();

        CAPTURES.put(FormUtils.getPropertyPath(form.pixelArt),
            new Capture(matrix, inverse, picture.width, picture.height, System.currentTimeMillis()));
    }

    public static Capture get(String path)
    {
        Capture capture = CAPTURES.get(path);

        return capture != null && System.currentTimeMillis() - capture.time() < LIFETIME ? capture : null;
    }

    public static void clear()
    {
        CAPTURES.clear();
    }
}
