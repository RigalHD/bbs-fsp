package mchorse.bbs_fsp.ui.graffiti;

import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiKeyframeFactory;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.graffiti.ValueGraffitiPicture;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What the graffiti editor paints into: the pixel art track of a replay at the film's cursor,
 * one keyframe opened from a keyframe editor, or the form's own picture when there is no
 * animation around it. Every edit goes through {@link #begin()} and {@link #end()}, which is
 * what BBS's undo listens to.
 *
 * <p>In a film nothing is held on to: the replay's form, its pixel art track and the copy of
 * the form the film draws are looked up again on every use, since undo, a form picked in the
 * palette or a restarted film replace each of them with a new object.</p>
 */
public final class GraffitiSession
{
    private static Form lastRoot;
    private static GraffitiForm lastFound;

    public final String path;
    public final UIFilmPanel panel;
    public final Replay replay;

    private final GraffitiForm form;
    private final Keyframe<GraffitiPicture> fixed;
    private final List<GraffitiForm> copies = new ArrayList<>();
    private BaseValue editing;

    private GraffitiSession(String path, UIFilmPanel panel, Replay replay, GraffitiForm form, Keyframe<GraffitiPicture> fixed)
    {
        this.path = path;
        this.panel = panel;
        this.replay = replay;
        this.form = form;
        this.fixed = fixed;
    }

    public static GraffitiSession ofForm(GraffitiForm form)
    {
        return new GraffitiSession(FormUtils.getPropertyPath(form.pixelArt), null, null, form, null);
    }

    public static GraffitiSession ofKeyframe(GraffitiForm form, Keyframe<GraffitiPicture> keyframe)
    {
        return new GraffitiSession(FormUtils.getPropertyPath(form.pixelArt), null, null, form, keyframe);
    }

    /** The replay's graffiti and its pixel art track; null when the replay has no graffiti. */
    public static GraffitiSession ofReplay(UIFilmPanel panel, Replay replay)
    {
        GraffitiForm form = find(replay == null ? null : replay.form.get());

        return form == null ? null : new GraffitiSession(FormUtils.getPropertyPath(form.pixelArt), panel, replay, null, null);
    }

    /** The first graffiti in the form or any of its body parts. */
    public static GraffitiForm find(Form root)
    {
        if (root instanceof GraffitiForm graffiti)
        {
            return graffiti;
        }

        if (root == null)
        {
            return null;
        }

        /* Asked every frame for the selected replay, and a form is replaced rather than changed
         * whenever it is edited, so the last answer holds for as long as the form is the same. */
        if (root != lastRoot)
        {
            lastRoot = root;
            lastFound = null;

            for (String path : FormUtils.collectPropertyPaths(root))
            {
                if (path.endsWith(GraffitiForm.PIXEL_ART) && (lastFound = graffitiAt(root, path)) != null)
                {
                    break;
                }
            }
        }

        return lastFound;
    }

    private static GraffitiForm graffitiAt(Form root, String path)
    {
        BaseValueBasic property = root == null ? null : FormUtils.getProperty(root, path);

        return property instanceof ValueGraffitiPicture && FormUtils.getForm(property) instanceof GraffitiForm graffiti ? graffiti : null;
    }

    /** Also writes the graffiti's settings into this copy of it, the one an open form editor holds. */
    public GraffitiSession withCopy(GraffitiForm copy)
    {
        if (copy != null && !this.copies.contains(copy))
        {
            this.copies.add(copy);
        }

        return this;
    }

    public boolean isFilm()
    {
        return this.replay != null;
    }

    public boolean isValid()
    {
        return !this.isFilm() || this.panel.getData() != null && this.panel.replayEditor.getReplay() == this.replay
            && this.getForm() != null;
    }

    /** The graffiti whose settings are saved: the replay's own in a film. */
    public GraffitiForm getForm()
    {
        return this.isFilm() ? graffitiAt(this.replay.form.get(), this.path) : this.form;
    }

    /** The copy of the replay's form the film draws, or null while it draws none. */
    public GraffitiForm getLiveForm()
    {
        if (!this.isFilm())
        {
            return null;
        }

        IEntity entity = this.panel.getController().getEntities().get(this.replay.getId());

        return entity == null ? null : graffitiAt(entity.getForm(), this.path);
    }

    /**
     * Changes one of the graffiti's settings on every copy of the form at once: the one that
     * is saved, the one the film draws (otherwise the change would only show once the film
     * makes its copies again) and the one an open form editor holds.
     */
    public <T> void setSetting(Function<GraffitiForm, ? extends BaseValueBasic<T>> property, T value)
    {
        GraffitiForm form = this.getForm();

        if (form == null)
        {
            return;
        }

        if (this.isFilm())
        {
            this.replay.form.preNotify(IValueListener.FLAG_DEFAULT);
            property.apply(form).set(value);
            this.replay.form.postNotify(IValueListener.FLAG_DEFAULT);
        }
        else
        {
            property.apply(form).set(value);
        }

        GraffitiForm live = this.getLiveForm();

        if (live != null && live != form)
        {
            property.apply(live).set(value);
        }

        for (GraffitiForm copy : this.copies)
        {
            if (copy != form && copy != live)
            {
                property.apply(copy).set(value);
            }
        }
    }

    public <T> T getSetting(Function<GraffitiForm, ? extends BaseValueBasic<T>> property, T fallback)
    {
        GraffitiForm form = this.getForm();

        return form == null ? fallback : property.apply(form).get();
    }

    /* Frames */

    @SuppressWarnings("unchecked")
    private KeyframeChannel<GraffitiPicture> getChannel(boolean create)
    {
        if (!this.isFilm())
        {
            return null;
        }

        KeyframeChannel channel = create
            ? this.replay.properties.getOrCreate(this.replay.form.get(), this.path)
            : this.replay.properties.get(TrackId.parse(this.path));

        return channel != null && channel.getFactory() == GraffitiKeyframeFactory.INSTANCE
            ? (KeyframeChannel<GraffitiPicture>) channel
            : null;
    }

    /** The film's tick in the replay's own time. */
    public int getTick()
    {
        return this.isFilm() ? this.replay.getTick(this.panel.getCursor()) : 0;
    }

    public List<Keyframe<GraffitiPicture>> getFrames()
    {
        KeyframeChannel<GraffitiPicture> channel = this.getChannel(false);

        if (channel != null)
        {
            return channel.getKeyframes();
        }

        return this.fixed == null ? List.of() : List.of(this.fixed);
    }

    /** The keyframe whose picture shows at the cursor: the last one at or before it, or the first one. */
    public Keyframe<GraffitiPicture> getCurrentFrame()
    {
        if (this.fixed != null)
        {
            return this.fixed;
        }

        List<Keyframe<GraffitiPicture>> frames = this.getFrames();

        if (frames.isEmpty())
        {
            return null;
        }

        Keyframe<GraffitiPicture> current = frames.get(0);
        int tick = this.getTick();

        for (Keyframe<GraffitiPicture> frame : frames)
        {
            if (frame.getTick() <= tick)
            {
                current = frame;
            }
        }

        return current;
    }

    public Keyframe<GraffitiPicture> getPreviousFrame()
    {
        List<Keyframe<GraffitiPicture>> frames = this.getFrames();
        int index = frames.indexOf(this.getCurrentFrame());

        return index > 0 ? frames.get(index - 1) : null;
    }

    /** The picture shown right now. */
    public GraffitiPicture getPicture()
    {
        Keyframe<GraffitiPicture> frame = this.getCurrentFrame();

        if (frame != null)
        {
            return frame.getValue();
        }

        GraffitiForm form = this.getForm();

        return form == null ? null : form.pixelArt.getOriginalValue();
    }

    public GraffitiPicture getPreviousPicture()
    {
        Keyframe<GraffitiPicture> previous = this.getPreviousFrame();

        return previous == null ? null : previous.getValue();
    }

    /**
     * Starts an edit and hands out the picture to change, or null when there is nothing to
     * paint any more. A film without pixel art keyframes gets its first one here, holding what
     * the form showed.
     */
    public GraffitiPicture begin()
    {
        this.end();

        GraffitiForm form = this.getForm();

        if (form == null)
        {
            return null;
        }

        if (this.isFilm() && this.getFrames().isEmpty())
        {
            this.insert(this.getTick(), form.pixelArt.getOriginalValue().copy(), 0F, 0F);
        }

        Keyframe<GraffitiPicture> frame = this.getCurrentFrame();

        this.editing = frame != null ? frame : form.pixelArt;
        this.editing.preNotify(IValueListener.FLAG_UNMERGEABLE);

        GraffitiPicture picture = frame != null ? frame.getValue() : form.pixelArt.getOriginalValue();

        picture.unshare();

        return picture;
    }

    public void end()
    {
        if (this.editing != null)
        {
            this.editing.postNotify(IValueListener.FLAG_UNMERGEABLE);
            this.editing = null;
        }
    }

    public void edit(Consumer<GraffitiPicture> edit)
    {
        GraffitiPicture picture = this.begin();

        if (picture == null)
        {
            return;
        }

        try
        {
            edit.accept(picture);
            picture.touch();
        }
        finally
        {
            this.end();
        }
    }

    /** One edit of every frame, as one step of undo, the cursor staying where it is. */
    public void editAll(Consumer<GraffitiPicture> edit)
    {
        KeyframeChannel<GraffitiPicture> channel = this.getChannel(false);

        if (channel == null || channel.isEmpty())
        {
            this.edit(edit);

            return;
        }

        this.end();
        channel.preNotify(IValueListener.FLAG_UNMERGEABLE);

        try
        {
            for (Keyframe<GraffitiPicture> frame : channel.getKeyframes())
            {
                GraffitiPicture picture = frame.getValue();

                picture.unshare();
                edit.accept(picture);
                picture.touch();
            }
        }
        finally
        {
            channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }
    }

    /**
     * A new frame: at the cursor when no frame starts there, otherwise right after the current
     * frame, {@code length} ticks later, the frames after it moving along when they are in the
     * way. It is empty with the layers of the current frame, or a copy of it, and the cursor
     * goes to it.
     */
    public Keyframe<GraffitiPicture> addFrame(boolean duplicate, int length)
    {
        GraffitiPicture current = this.getPicture();

        if (!this.isFilm() || current == null)
        {
            return null;
        }

        GraffitiPicture picture = current.copy();

        picture.unshare();

        if (!duplicate)
        {
            for (CanvasLayerData layer : picture.layers)
            {
                Arrays.fill(layer.pixels, 0);
                layer.touch();
            }
        }

        picture.touch();

        List<Keyframe<GraffitiPicture>> frames = this.getFrames();
        Keyframe<GraffitiPicture> frame = this.getCurrentFrame();
        int tick = this.getTick();

        if (frame == null || Math.round(frame.getTick()) != tick)
        {
            return this.seekTo(this.insert(tick, picture, tick, 0F));
        }

        int index = frames.indexOf(frame);
        Keyframe<GraffitiPicture> next = index + 1 < frames.size() ? frames.get(index + 1) : null;

        length = Math.max(1, length);
        tick += length;

        return this.seekTo(this.insert(tick, picture, frame.getTick(), next == null ? 0F : Math.max(0F, tick + length - next.getTick())));
    }

    /** Inserts a frame as one step of undo, moving the frames after {@code after} by {@code shift} first. */
    private Keyframe<GraffitiPicture> insert(int tick, GraffitiPicture picture, float after, float shift)
    {
        KeyframeChannel<GraffitiPicture> channel = this.getChannel(true);

        if (channel == null)
        {
            return null;
        }

        this.end();
        channel.preNotify(IValueListener.FLAG_UNMERGEABLE);

        try
        {
            if (shift > 0F)
            {
                for (Keyframe<GraffitiPicture> frame : channel.getKeyframes())
                {
                    if (frame.getTick() > after)
                    {
                        frame.setTick(frame.getTick() + shift);
                    }
                }
            }

            return channel.get(channel.insert(tick, picture));
        }
        finally
        {
            channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }
    }

    public void removeFrame()
    {
        KeyframeChannel<GraffitiPicture> channel = this.getChannel(false);
        Keyframe<GraffitiPicture> frame = this.getCurrentFrame();
        int index = channel == null || frame == null ? -1 : channel.getKeyframes().indexOf(frame);

        if (index >= 0)
        {
            this.end();
            channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
            channel.remove(index);
            channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }
    }

    /* The film's cursor */

    private Keyframe<GraffitiPicture> seekTo(Keyframe<GraffitiPicture> frame)
    {
        this.seek(frame);

        return frame;
    }

    /** Puts the film's cursor on a frame. */
    public void seek(Keyframe<GraffitiPicture> frame)
    {
        if (this.isFilm() && frame != null)
        {
            this.seekTick(Math.round(frame.getTick()));
        }
    }

    /** Puts the film's cursor on a tick of the replay's own time. */
    public void seekTick(int tick)
    {
        if (this.isFilm())
        {
            int offset = this.panel.getCursor() - this.getTick();

            this.panel.setCursor(Math.max(0, tick + offset));
        }
    }

    public void seekBy(int direction)
    {
        List<Keyframe<GraffitiPicture>> frames = this.getFrames();
        int index = frames.indexOf(this.getCurrentFrame());
        int tick = this.getTick();

        /* Before the first frame the first one is the current one too, so "next" goes to it. */
        if (index == 0 && direction > 0 && frames.get(0).getTick() > tick)
        {
            index -= 1;
        }

        index += direction;

        if (index >= 0 && index < frames.size())
        {
            this.seek(frames.get(index));
        }
    }

    public void step(int ticks)
    {
        if (this.isFilm())
        {
            this.panel.setCursor(Math.max(0, this.panel.getCursor() + ticks));
        }
    }

    /** The length of the film, or at least enough to see every frame and the cursor. */
    public int getDuration()
    {
        if (!this.isFilm())
        {
            return 0;
        }

        List<Keyframe<GraffitiPicture>> frames = this.getFrames();
        int last = frames.isEmpty() ? 0 : Math.round(frames.get(frames.size() - 1).getTick());

        return Math.max(Math.max(this.panel.getData().camera.calculateDuration(), last + 20), this.getTick() + 1);
    }

    public boolean isPlaying()
    {
        return this.isFilm() && this.panel.isRunning();
    }

    public void togglePlayback()
    {
        if (this.isFilm())
        {
            this.end();
            this.panel.togglePlayback();
        }
    }

    public boolean isFlying()
    {
        return this.isFilm() && this.panel.isFlying();
    }

    public void toggleFlight()
    {
        if (this.isFilm())
        {
            this.panel.toggleFlight();
        }
    }

    public void undo()
    {
        if (this.panel != null)
        {
            this.end();
            this.panel.undo();
        }
    }

    public void redo()
    {
        if (this.panel != null)
        {
            this.end();
            this.panel.redo();
        }
    }
}
