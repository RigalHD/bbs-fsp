package mchorse.bbs_fsp;

import mchorse.bbs_fsp.bbmodel.BBModelLoader;
import mchorse.bbs_fsp.render.CanvasDraw;
import mchorse.bbs_fsp.render.CanvasRuntimeOverlay;
import mchorse.bbs_fsp.render.GraffitiScene;
import mchorse.bbs_fsp.render.GraffitiTextures;
import mchorse.bbs_fsp.ui.CanvasBrushStore;
import mchorse.bbs_fsp.ui.CanvasEyedropper;
import mchorse.bbs_fsp.ui.CanvasViewportController;
import mchorse.bbs_fsp.ui.graffiti.UIGraffitiEditor;
import mchorse.bbs_mod.cubic.model.ModelManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class BbsFspClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        CanvasRuntimeOverlay.register();
        ModelManager.registerLoader(BBModelLoader::new);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() ->
        {
            UIGraffitiEditor graffiti = UIGraffitiEditor.getCurrent();

            if (graffiti != null)
            {
                graffiti.close();
            }

            CanvasViewportController.finish();
            CanvasEyedropper.disarm();
            CanvasDraw.clearTextures();
            GraffitiTextures.clear();
            GraffitiScene.clear();
            CanvasBrushStore.save();
        }));
        ClientLifecycleEvents.CLIENT_STOPPING.register((client) -> CanvasBrushStore.save());
    }
}
