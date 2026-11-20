package mchorse.bbs_fsp.render;

import mchorse.bbs_fsp.canvas.CanvasComposition;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Which canvas frames are on screen right now: the film open in the editor at its cursor, or
 * every film that is playing in the world at its own tick.
 */
public final class CanvasPlayback
{
    private CanvasPlayback()
    {}

    public static List<CanvasComposition.Sample> samples()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null)
        {
            return List.of();
        }

        UIFilmPanel panel = getFilmPanel();
        List<CanvasComposition.Sample> samples = new ArrayList<>();

        if (panel != null && panel.getData() != null)
        {
            samples.addAll(CanvasComposition.resolve(panel.getData(), panel.getCursor()));
        }
        else
        {
            for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
            {
                samples.addAll(CanvasComposition.resolve(controller.film, controller.getTick()));
            }
        }

        return samples;
    }

    public static UIFilmPanel getFilmPanel()
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        return menu instanceof UIDashboard dashboard && dashboard.getPanels().panel instanceof UIFilmPanel editor
            ? editor
            : null;
    }
}
