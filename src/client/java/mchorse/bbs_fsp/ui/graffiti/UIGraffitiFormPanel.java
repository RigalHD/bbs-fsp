package mchorse.bbs_fsp.ui.graffiti;

import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.render.CanvasPlayback;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.utils.Direction;

public class UIGraffitiFormPanel extends UIFormPanel<GraffitiForm>
{
    private GraffitiSession session = GraffitiSession.ofForm(new GraffitiForm());

    public UIGraffitiFormPanel(UIForm editor)
    {
        super(editor);

        UIButton open = new UIButton(GraffitiKeys.OPEN_EDITOR, (b) -> UIGraffitiEditor.open(this.getContext(), this.createSession()));

        open.tooltip(GraffitiKeys.OPEN_EDITOR_TOOLTIP, Direction.LEFT);

        this.options.add(open);
        this.options.add(GraffitiControls.animation(() -> this.session));
        this.options.add(GraffitiControls.view(() -> this.session));
        this.options.add(GraffitiControls.effects(() -> this.session));
    }

    /**
     * Inside a film whose selected replay wears this graffiti, the editor paints its pixel art
     * keyframes over the scene; anywhere else it paints the form's own picture.
     */
    private GraffitiSession createSession()
    {
        UIFilmPanel panel = CanvasPlayback.getFilmPanel();
        GraffitiSession film = panel == null ? null : GraffitiSession.ofReplay(panel, panel.replayEditor.getReplay());

        if (film != null && film.path.equals(FormUtils.getPropertyPath(this.form.pixelArt)))
        {
            /* The form editor works on a copy of the replay's form and hands it back when it is
             * done, so the settings changed in the pixel art editor go into that copy too. */
            return film.withCopy(this.form);
        }

        return GraffitiSession.ofForm(this.form);
    }

    @Override
    public void startEdit(GraffitiForm form)
    {
        super.startEdit(form);

        if (form.pixelArt.get() == null)
        {
            form.pixelArt.set(new GraffitiPicture(GraffitiPicture.DEFAULT_SIZE, GraffitiPicture.DEFAULT_SIZE));
        }

        this.session = GraffitiSession.ofForm(form);
    }
}
