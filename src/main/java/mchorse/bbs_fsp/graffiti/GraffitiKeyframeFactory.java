package mchorse.bbs_fsp.graffiti;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * The pixel art track: every keyframe is a whole drawn frame, held until the next one.
 */
public final class GraffitiKeyframeFactory implements IKeyframeFactory<GraffitiPicture>
{
    public static final String KEY = "bbs_fsp:graffiti_picture";
    public static final GraffitiKeyframeFactory INSTANCE = new GraffitiKeyframeFactory();

    private GraffitiKeyframeFactory()
    {}

    @Override
    public GraffitiPicture fromData(BaseType data)
    {
        return data instanceof MapType map ? GraffitiPicture.fromData(map) : this.createEmpty();
    }

    @Override
    public BaseType toData(GraffitiPicture value)
    {
        return value.toData();
    }

    @Override
    public GraffitiPicture createEmpty()
    {
        return new GraffitiPicture(GraffitiPicture.DEFAULT_SIZE, GraffitiPicture.DEFAULT_SIZE);
    }

    @Override
    public GraffitiPicture copy(GraffitiPicture value)
    {
        return value == null ? null : value.copy();
    }

    @Override
    public boolean isStepped()
    {
        return true;
    }

    @Override
    public int contentHash(GraffitiPicture value)
    {
        return value == null ? 0 : Long.hashCode(value.getStamp());
    }

    @Override
    public GraffitiPicture interpolate(GraffitiPicture preA, GraffitiPicture a, GraffitiPicture b, GraffitiPicture postB,
        IInterp interpolation, float x)
    {
        return a;
    }
}
