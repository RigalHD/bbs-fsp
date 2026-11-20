package mchorse.bbs_fsp.ui.graffiti;

import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiPicture;
import mchorse.bbs_fsp.render.CanvasPlayback;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * The pixel art keyframe: what size the frame is and a way into the editor, right at this
 * frame of the film.
 */
public class UIGraffitiKeyframeFactory extends UIKeyframeFactory<GraffitiPicture>
{
    public UIGraffitiKeyframeFactory(Keyframe<GraffitiPicture> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        UIButton open = new UIButton(GraffitiKeys.OPEN_EDITOR, (b) -> this.open());

        open.tooltip(GraffitiKeys.OPEN_EDITOR_TOOLTIP, Direction.LEFT);

        this.scroll.add(
            UI.label(IKey.raw(GraffitiKeys.PIXEL_ART.get() + ": " + this.size())),
            open
        );
    }

    private String size()
    {
        GraffitiPicture picture = this.keyframe.getValue();

        return picture == null ? "-" : picture.width + " × " + picture.height;
    }

    private void open()
    {
        UIFilmPanel panel = CanvasPlayback.getFilmPanel();

        if (panel != null && panel.replayEditor.getReplay() != null)
        {
            GraffitiSession session = GraffitiSession.ofReplay(panel, panel.replayEditor.getReplay());

            if (session != null && session.getFrames().contains(this.keyframe))
            {
                session.seek(this.keyframe);
                UIGraffitiEditor.open(this.getContext(), session);

                return;
            }
        }

        UIGraffitiEditor.open(this.getContext(), GraffitiSession.ofKeyframe(this.findForm(), this.keyframe));
    }

    private GraffitiForm findForm()
    {
        UIKeyframeSheet sheet = this.editor.getGraph() == null ? null : this.editor.getGraph().getSheet(this.keyframe);

        if (sheet != null && sheet.property != null && FormUtils.getForm(sheet.property) instanceof GraffitiForm form)
        {
            return form;
        }

        return new GraffitiForm();
    }
}
