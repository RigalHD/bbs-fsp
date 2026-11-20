package mchorse.bbs_fsp;

import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.graffiti.GraffitiCycle;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiKeyframeFactory;
import mchorse.bbs_fsp.render.GraffitiFormRenderer;
import mchorse.bbs_fsp.ui.UICanvasClip;
import mchorse.bbs_fsp.ui.graffiti.GraffitiKeys;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiEditor;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiForm;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiKeyframeFactory;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterClipPanelsEvent;
import mchorse.bbs_mod.api.client.events.RegisterFormEditorsEvent;
import mchorse.bbs_mod.api.client.events.RegisterFormRenderersEvent;
import mchorse.bbs_mod.api.client.events.RegisterKeyframeEditorsEvent;
import mchorse.bbs_mod.api.client.events.RegisterL10nEvent;
import mchorse.bbs_mod.resources.Link;

public final class BbsFspClientAddon implements BBSAddonMod
{
    @Subscribe
    public void registerLanguage(RegisterL10nEvent event)
    {
        event.l10n.registerOne((language) -> new Link(
            BbsFspMod.MOD_ID,
            "strings/" + language + ".json"
        ));
    }

    @Subscribe
    public void registerClipPanels(RegisterClipPanelsEvent event)
    {
        event.register(CanvasClip.class, UICanvasClip::new);
    }

    @Subscribe
    public void registerFormRenderers(RegisterFormRenderersEvent event)
    {
        GraffitiForm.labels = GraffitiKeys::label;
        GraffitiCycle.paused = UIGraffitiEditor::isOpen;
        event.register(GraffitiForm.class, GraffitiFormRenderer::new);
    }

    @Subscribe
    public void registerFormEditors(RegisterFormEditorsEvent event)
    {
        event.register(GraffitiForm.class, UIGraffitiForm::new);
    }

    @Subscribe
    public void registerKeyframeEditors(RegisterKeyframeEditorsEvent event)
    {
        event.register(GraffitiKeyframeFactory.INSTANCE, UIGraffitiKeyframeFactory::new);
    }
}
