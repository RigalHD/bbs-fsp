package mchorse.bbs_fsp.graffiti;

import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;

public final class ValueGraffitiPicture extends BaseKeyframeFactoryValue<GraffitiPicture>
{
    public ValueGraffitiPicture(String id, GraffitiPicture value)
    {
        super(id, GraffitiKeyframeFactory.INSTANCE, value);
    }
}
