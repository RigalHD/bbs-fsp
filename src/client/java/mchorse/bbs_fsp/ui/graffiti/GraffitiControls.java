package mchorse.bbs_fsp.ui.graffiti;

import mchorse.bbs_fsp.graffiti.GraffitiCycle;
import mchorse.bbs_fsp.graffiti.GraffitiForm;
import mchorse.bbs_fsp.render.GraffitiLights;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The graffiti's own settings as fields, the same in the form editor and in the pixel art
 * editor. They read the graffiti a session points at and write through
 * {@link GraffitiSession#setSetting}, so a change reaches every copy of the form at once.
 */
final class GraffitiControls
{
    /** Where the ranges of the fields come from. */
    private static final GraffitiForm RANGES = new GraffitiForm();

    private GraffitiControls()
    {}

    static UIElement[] effects(Supplier<GraffitiSession> session)
    {
        boolean lights = GraffitiLights.isAvailable();

        return new UIElement[] {
            UI.label(GraffitiKeys.EFFECTS).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(GraffitiKeys.BLUR, number(session, (form) -> form.blur)),
            UI.labelRow(GraffitiKeys.GLOW, number(session, (form) -> form.glow)),
            UI.labelRow(GraffitiKeys.GLOW_RADIUS, number(session, (form) -> form.glowRadius)),
            UI.labelRow(GraffitiKeys.LIGHT_INTENSITY, number(session, (form) -> form.lightIntensity)),
            UI.labelRow(GraffitiKeys.LIGHT_RADIUS, number(session, (form) -> form.lightRadius)),
            UI.label(lights ? GraffitiKeys.LIGHT_ACTIVE : GraffitiKeys.LIGHT_MISSING).color(lights ? 0xFF80FF80 : Colors.LIGHTEST_GRAY)
        };
    }

    /** How long the animation lasts and what happens after it, see {@link GraffitiCycle}. */
    static UIElement[] animation(Supplier<GraffitiSession> session)
    {
        UITrackpad length = new UITrackpad((value) -> session.get().setSetting((form) -> form.cycleLength, value.intValue()));
        UICirculate mode = new UICirculate((b) -> session.get().setSetting((form) -> form.cycleMode, b.getValue()));

        length.integer().limit(0, RANGES.cycleLength.getMax());
        length.valueBinding(() -> length.setValue(session.get().getSetting((form) -> form.cycleLength, 0)));
        length.tooltip(GraffitiKeys.CYCLE_LENGTH_TOOLTIP);

        mode.addLabel(GraffitiKeys.CYCLE_LOOP);
        mode.addLabel(GraffitiKeys.CYCLE_HOLD);
        mode.addLabel(GraffitiKeys.CYCLE_ONCE);
        mode.valueBinding(() -> mode.setValue(session.get().getSetting((form) -> form.cycleMode, GraffitiCycle.HOLD)));
        mode.tooltip(GraffitiKeys.CYCLE_MODE_TOOLTIP);

        return new UIElement[] {
            UI.label(GraffitiKeys.ANIMATION).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(GraffitiKeys.CYCLE_LENGTH, length),
            mode
        };
    }

    static UIElement[] view(Supplier<GraffitiSession> session)
    {
        return new UIElement[] {
            UI.label(GraffitiKeys.VIEW).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(GraffitiKeys.GRID, number(session, (form) -> form.gridOpacity)),
            UI.labelRow(GraffitiKeys.ONION_SKIN, number(session, (form) -> form.onionSkin)),
            toggle(session, GraffitiKeys.BILLBOARD, (form) -> form.billboard),
            toggle(session, GraffitiKeys.UNLIT, (form) -> form.unlit).tooltip(GraffitiKeys.UNLIT_TOOLTIP),
            toggle(session, GraffitiKeys.SHADING, (form) -> form.shading)
        };
    }

    static UITrackpad number(Supplier<GraffitiSession> session, Function<GraffitiForm, ValueFloat> property)
    {
        ValueFloat range = property.apply(RANGES);
        UITrackpad trackpad = new UITrackpad((value) -> session.get().setSetting(property, value.floatValue()));

        trackpad.limit(range.getMin(), range.getMax());
        trackpad.valueBinding(() -> trackpad.setValue(session.get().getSetting(property, range.get())));

        return trackpad;
    }

    static UIToggle toggle(Supplier<GraffitiSession> session, IKey label, Function<GraffitiForm, ValueBoolean> property)
    {
        UIToggle toggle = new UIToggle(label, false, (b) -> session.get().setSetting(property, b.getValue()));

        toggle.valueBinding(() -> toggle.setValue(session.get().getSetting(property, property.apply(RANGES).get())));

        return toggle;
    }
}
