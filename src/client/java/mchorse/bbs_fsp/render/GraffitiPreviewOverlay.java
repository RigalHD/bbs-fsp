package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.ui.graffiti.GraffitiKeys;
import mchorse.bbs_fsp.ui.graffiti.GraffitiSession;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiEditor;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Direction;

/**
 * The film preview while the selected replay wears a graffiti: its pixel grid along the quad,
 * and a button in the preview's icon bar that opens the pixel art editor.
 */
public final class GraffitiPreviewOverlay
{
    private GraffitiPreviewOverlay()
    {}

    public static UIIcon createButton(UIFilmPreview preview, UIFilmPanel panel)
    {
        UIIcon button = new UIIcon(GraffitiForm.ICON, (b) ->
        {
            GraffitiSession session = GraffitiSession.ofReplay(panel, panel.replayEditor.getReplay());

            if (session != null)
            {
                UIGraffitiEditor.open(b.getContext(), session);
            }
        });

        button.tooltip(GraffitiKeys.OPEN_EDITOR_TOOLTIP, Direction.TOP);
        button.setVisible(false);

        return button;
    }

    public static void render(UIFilmPreview preview, UIFilmPanel panel, UIIcon button, UIContext context)
    {
        Replay replay = panel == null || panel.getData() == null ? null : panel.replayEditor.getReplay();
        GraffitiForm form = replay == null ? null : GraffitiSession.find(replay.form.get());

        if (button != null && button.isVisible() != (form != null))
        {
            button.setVisible(form != null);
            preview.icons.resize();
        }

        if (form == null || UIGraffitiEditor.isOpen() || !panel.isReplayEditorSelected())
        {
            return;
        }

        GraffitiScene.Capture capture = GraffitiScene.get(FormUtils.getPropertyPath(form.pixelArt));

        if (capture != null)
        {
            Area viewport = preview.getViewport();

            context.batcher.clip(viewport, context);
            UIGraffitiEditor.renderGrid(context, capture, viewport, form.gridOpacity.get());
            context.batcher.unclip(context);
        }
    }
}
