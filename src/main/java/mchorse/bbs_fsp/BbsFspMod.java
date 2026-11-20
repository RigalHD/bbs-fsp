package mchorse.bbs_fsp;

import mchorse.bbs_mod.resources.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BbsFspMod
{
    public static final String MOD_ID = "bbs_fsp";
    public static final Link CANVAS_LINK = new Link(MOD_ID, "canvas");
    public static final int CANVAS_COLOR = 0xF28C28;
    public static final Link GRAFFITI_LINK = new Link(MOD_ID, "graffiti");
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private BbsFspMod()
    {}
}
