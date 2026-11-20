package mchorse.bbs_fsp.ui.graffiti;

import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;

public class UIGraffitiForm extends UIForm<GraffitiForm>
{
    public UIGraffitiForm()
    {
        super();

        this.defaultPanel = new UIGraffitiFormPanel(this);

        this.registerPanel(this.defaultPanel, GraffitiKeys.TITLE, GraffitiForm.ICON);
        this.registerDefaultPanels();
    }
}
