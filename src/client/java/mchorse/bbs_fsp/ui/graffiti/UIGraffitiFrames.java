package mchorse.bbs_fsp.ui.graffiti;

import mchorse.bbs_fsp.graffiti.GraffitiCycle;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.render.GraffitiTextures;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.List;

/**
 * The bottom of the editor, standing in for the film editor's timeline under it: a ruler of
 * ticks with the pixel art keyframes and the cursor on it (a click or a drag moves the
 * cursor, the scroll wheel steps it), and the frames as a strip of thumbnails (a click puts
 * the cursor on that frame, the scroll wheel walks along the strip).
 */
public final class UIGraffitiFrames extends UIElement
{
    public static final int HEIGHT = 60;

    private static final int RULER = 18;
    private static final int CELL = 34;
    private static final int GAP = 3;
    private static final int PAD = 6;
    private static final int CURSOR_COLOR = 0xFFFFA020;
    private static final int CYCLE_COLOR = 0xFF4080FF;
    private static final int[] STEPS = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000};

    private final UIGraffitiEditor editor;
    private int first;
    private boolean scrubbing;
    private Keyframe<GraffitiPicture> shown;

    public UIGraffitiFrames(UIGraffitiEditor editor)
    {
        this.editor = editor;
    }

    public void release()
    {
        this.scrubbing = false;
    }

    private int visibleCells()
    {
        return Math.max(1, (this.area.w - GAP) / (CELL + GAP));
    }

    private float tickX(int tick, int duration)
    {
        return this.area.x + PAD + tick / (float) Math.max(1, duration) * (this.area.w - PAD * 2);
    }

    private int tickAt(int mouseX, int duration)
    {
        float x = (mouseX - this.area.x - PAD) / (float) Math.max(1, this.area.w - PAD * 2);

        return Math.max(0, Math.min(duration, Math.round(x * duration)));
    }

    /** Where a tick of the replay's time is on the ruler. */
    public float[] rulerPoint(int tick)
    {
        return new float[] {this.tickX(tick, this.editor.getSession().getDuration()), this.area.y + RULER / 2F};
    }

    private boolean onRuler(UIContext context)
    {
        return this.area.isInside(context) && context.mouseY < this.area.y + RULER;
    }

    @Override
    public void render(UIContext context)
    {
        GraffitiSession session = this.editor.getSession();
        int duration = session.getDuration();

        if (this.scrubbing)
        {
            session.seekTick(this.tickAt(context.mouseX, duration));
        }

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF1A1A1A);
        this.renderRuler(context, session, duration);
        this.renderStrip(context, session);

        super.render(context);
    }

    private void renderRuler(UIContext context, GraffitiSession session, int duration)
    {
        int y = this.area.y;
        int step = STEPS[STEPS.length - 1];
        float perTick = (this.area.w - PAD * 2) / (float) Math.max(1, duration);

        for (int candidate : STEPS)
        {
            if (candidate * perTick >= 28F)
            {
                step = candidate;

                break;
            }
        }

        context.batcher.box(this.area.x, y, this.area.ex(), y + RULER, 0xFF101010);

        for (int tick = 0; tick <= duration; tick += step)
        {
            int x = (int) this.tickX(tick, duration);
            String label = String.valueOf(tick);

            context.batcher.box(x, y + RULER - 4, x + 1, y + RULER, 0xFF606060);

            if (x + 2 + context.batcher.getFont().getWidth(label) <= this.area.ex())
            {
                context.batcher.text(label, x + 2, y + 3, 0xFF808080, false);
            }
        }

        this.renderCycleEnd(context, session, duration, y);

        Keyframe<GraffitiPicture> current = session.getCurrentFrame();

        for (Keyframe<GraffitiPicture> frame : session.getFrames())
        {
            int x = (int) this.tickX(Math.round(frame.getTick()), duration);

            context.batcher.box(x - 2, y + RULER - 8, x + 3, y + RULER - 1, frame == current ? CURSOR_COLOR : Colors.LIGHTEST_GRAY);
        }

        int cursor = (int) this.tickX(session.getTick(), duration);

        context.batcher.box(cursor, y, cursor + 1, y + RULER, CURSOR_COLOR);
    }

    /** Where the animation's cycle ends: a line, and the time after it dimmed. */
    private void renderCycleEnd(UIContext context, GraffitiSession session, int duration, int y)
    {
        List<Keyframe<GraffitiPicture>> frames = session.getFrames();

        if (frames.isEmpty())
        {
            return;
        }

        float cycle = GraffitiCycle.length(session.getSetting((form) -> form.cycleLength, 0), frames);
        int end = Math.round(frames.get(0).getTick() + cycle);

        if (cycle <= 0F || end > duration)
        {
            return;
        }

        int x = (int) this.tickX(end, duration);

        context.batcher.box(x, y, this.area.ex(), y + RULER, 0x80000000);
        context.batcher.box(x, y, x + 1, y + RULER, CYCLE_COLOR);
    }

    private void renderStrip(UIContext context, GraffitiSession session)
    {
        List<Keyframe<GraffitiPicture>> frames = session.getFrames();
        Keyframe<GraffitiPicture> current = session.getCurrentFrame();
        int visible = this.visibleCells();
        int top = this.area.y + RULER + 4;

        /* When the cursor moves to another frame, the strip follows it. */
        if (current != this.shown)
        {
            int index = frames.indexOf(current);

            this.shown = current;

            if (index >= 0 && (index < this.first || index >= this.first + visible))
            {
                this.first = index - visible / 2;
            }
        }

        this.first = Math.max(0, Math.min(this.first, frames.size() - visible));

        if (frames.isEmpty())
        {
            context.batcher.text(GraffitiKeys.NO_FRAMES.get(), this.area.x + PAD, top + CELL / 2 - 4, Colors.LIGHTEST_GRAY, false);

            return;
        }

        context.batcher.clip(this.area, context);

        for (int i = this.first; i < frames.size() && i < this.first + visible; i++)
        {
            Keyframe<GraffitiPicture> frame = frames.get(i);
            GraffitiPicture picture = frame.getValue();
            int x = this.area.x + GAP + (i - this.first) * (CELL + GAP);

            context.batcher.box(x - 1, top - 1, x + CELL + 1, top + CELL + 1, frame == current ? CURSOR_COLOR : 0xFF404040);
            context.batcher.iconArea(Icons.CHECKBOARD, x, top, CELL, CELL);

            Texture texture = picture == null ? null
                : GraffitiTextures.get(frame, GraffitiTextures.Kind.ALL, picture, GraffitiTextures.Settings.NONE).texture();

            if (texture != null)
            {
                float scale = Math.min(CELL / (float) picture.width, CELL / (float) picture.height);
                float w = picture.width * scale;
                float h = picture.height * scale;

                context.batcher.texturedBox(texture, Colors.WHITE, x + (CELL - w) / 2F, top + (CELL - h) / 2F, w, h,
                    0, 0, texture.width, texture.height, texture.width, texture.height);
            }

            context.batcher.textCard(String.valueOf(Math.round(frame.getTick())), x + 1, top + CELL - 9);
        }

        context.batcher.unclip(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        if (context.mouseButton != 0)
        {
            return true;
        }

        GraffitiSession session = this.editor.getSession();

        if (this.onRuler(context))
        {
            this.scrubbing = true;
            session.seekTick(this.tickAt(context.mouseX, session.getDuration()));

            return true;
        }

        List<Keyframe<GraffitiPicture>> frames = session.getFrames();
        int column = (context.mouseX - this.area.x - GAP) / (CELL + GAP);
        int index = this.first + column;

        if (column >= 0 && column < this.visibleCells() && index < frames.size())
        {
            session.seek(frames.get(index));
        }

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.scrubbing = false;

        return false;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.subMouseScrolled(context);
        }

        int direction = (int) Math.signum(context.mouseWheel);

        if (this.onRuler(context))
        {
            this.editor.getSession().step(-direction);
        }
        else
        {
            this.first = Math.max(0, this.first - direction);
        }

        return true;
    }
}
