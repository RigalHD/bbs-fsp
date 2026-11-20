package mchorse.bbs_fsp;

import mchorse.bbs_fsp.canvas.CanvasClip;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.graffiti.GraffitiKeyframeFactory;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.events.RegisterCameraClipsEvent;
import mchorse.bbs_mod.api.events.RegisterFormsEvent;
import mchorse.bbs_mod.api.events.RegisterKeyframeFactoriesEvent;
import mchorse.bbs_mod.api.events.RegisterSourcePacksEvent;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public final class BbsFspAddon implements BBSAddonMod
{
    @Subscribe
    public void registerSourcePack(RegisterSourcePacksEvent event)
    {
        event.registerAddon(BbsFspMod.MOD_ID, BbsFspAddon.class);
    }

    @Subscribe
    public void registerCameraClips(RegisterCameraClipsEvent event)
    {
        event.factory.register(BbsFspMod.CANVAS_LINK, CanvasClip.class,
            new ClipFactoryData(Icons.BRUSH, BbsFspMod.CANVAS_COLOR));
    }

    @Subscribe
    public void registerKeyframeFactories(RegisterKeyframeFactoriesEvent event)
    {
        event.register(GraffitiKeyframeFactory.KEY, GraffitiKeyframeFactory.INSTANCE);
    }

    @Subscribe
    public void registerForms(RegisterFormsEvent event)
    {
        event.forms.register(BbsFspMod.GRAFFITI_LINK, GraffitiForm.class, null);
    }
}
