package mchorse.bbs_fsp.graffiti;

import mchorse.bbs_fsp.IntArrayText;
import mchorse.bbs_fsp.canvas.CanvasLayerData;
import mchorse.bbs_mod.data.types.IntArrayType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

import java.util.List;

public final class GraffitiCheck
{
    public static void main(String[] args)
    {
        checkCopies();
        checkData();
        checkLayers();
        checkResize();
        checkComposite();
        checkLights();
        checkTools();
        checkFactory();
        checkClamp();
        checkClipboardText();
        checkCycle();
    }

    /* Frames at 10, 12 and 14: the cycle lasts 6 ticks on its own, or as long as it is told. */
    private static void checkCycle()
    {
        GraffitiKeyframeFactory factory = GraffitiKeyframeFactory.INSTANCE;
        List<mchorse.bbs_mod.utils.keyframes.Keyframe<GraffitiPicture>> frames = List.of(
            new mchorse.bbs_mod.utils.keyframes.Keyframe<>("", factory, 10F, factory.createEmpty()),
            new mchorse.bbs_mod.utils.keyframes.Keyframe<>("", factory, 12F, factory.createEmpty()),
            new mchorse.bbs_mod.utils.keyframes.Keyframe<>("", factory, 14F, factory.createEmpty()));

        require(GraffitiCycle.length(0, frames) == 6F && GraffitiCycle.length(20, frames) == 20F);
        require(GraffitiCycle.remap(GraffitiCycle.LOOP, 0, frames, 5F) == 5F);
        require(GraffitiCycle.remap(GraffitiCycle.LOOP, 0, frames, 13F) == 13F);
        require(GraffitiCycle.remap(GraffitiCycle.LOOP, 0, frames, 17.5F) == 11.5F);
        require(GraffitiCycle.remap(GraffitiCycle.LOOP, 20, frames, 35F) == 15F);
        require(GraffitiCycle.remap(GraffitiCycle.HOLD, 0, frames, 40F) > 15.9F && GraffitiCycle.remap(GraffitiCycle.HOLD, 0, frames, 40F) < 16F);
        require(GraffitiCycle.remap(GraffitiCycle.HOLD, 3, frames, 40F) < 13F);
        require(GraffitiCycle.remap(GraffitiCycle.ONCE, 0, frames, 15F) == 15F);
        require(Float.isNaN(GraffitiCycle.remap(GraffitiCycle.ONCE, 0, frames, 16F)));
        require(GraffitiCycle.remap(GraffitiCycle.ONCE, 0, frames.subList(0, 1), 100F) == 100F);
        require(GraffitiCycle.remap(GraffitiCycle.LOOP, 0, List.of(), 7F) == 7F);
    }

    /* Pixels survive the text BBS puts on the clipboard when keyframes are copied. */
    private static void checkClipboardText()
    {
        GraffitiPicture picture = new GraffitiPicture(3, 2);

        picture.layers.get(0).pixels[0] = 0xFFFF0000;
        picture.layers.get(0).pixels[5] = 0x80123456;

        IntArrayType pixels = (IntArrayType) picture.toData().getList("layers").getMap(0).get("pixels");
        IntArrayType parsed = IntArrayText.parse(pixels.toString());

        require(parsed != null && java.util.Arrays.equals(parsed.value, picture.layers.get(0).pixels));
        require(IntArrayText.parse("[i;]").value.length == 0);
        require(IntArrayText.parse("[i; 7 , -8 ]").value[1] == -8);
        require(IntArrayText.parse("[1,2]") == null);
        require(IntArrayText.parse("[i;x]") == null);
    }

    /* An animated setting is drawn within the range of its field. */
    private static void checkClamp()
    {
        ValueFloat blur = new ValueFloat("blur", 0F, 0F, 32F);

        blur.setRuntimeValue(500F);
        require(GraffitiForm.clamped(blur) == 32F);
        blur.setRuntimeValue(-3F);
        require(GraffitiForm.clamped(blur) == 0F);
        blur.setRuntimeValue(null);
        blur.set(4F);
        require(GraffitiForm.clamped(blur) == 4F);
    }

    /* A copy shares the pixels until the edited side unshares them. */
    private static void checkCopies()
    {
        GraffitiPicture picture = new GraffitiPicture(4, 4);
        GraffitiPicture copy = picture.copy();

        require(copy.getStamp() == picture.getStamp());
        require(copy.layers.get(0).pixels == picture.layers.get(0).pixels);
        require(copy.equals(picture));

        picture.unshare();
        picture.layers.get(0).pixels[0] = 0xFFFF0000;
        picture.touch();

        require(copy.layers.get(0).pixels[0] == 0);
        require(copy.getStamp() != picture.getStamp());
        require(!copy.equals(picture));
    }

    private static void checkData()
    {
        GraffitiPicture picture = new GraffitiPicture(3, 2);
        CanvasLayerData glow = picture.addLayer(1, true);

        glow.pixels[5] = 0x8000FF00;
        glow.opacity = 0.5F;

        MapType data = picture.toData();
        GraffitiPicture read = GraffitiPicture.fromData(data);

        require(read.width == 3 && read.height == 2);
        require(read.layers.size() == 2);
        require(read.layers.get(1).glow && !read.layers.get(0).glow);
        require(read.layers.get(1).pixels[5] == 0x8000FF00);
        require(read.equals(picture));
        require(read.getStamp() != picture.getStamp());

        MapType broken = new MapType();

        broken.putInt("width", 100000);
        require(GraffitiPicture.fromData(broken).width == GraffitiPicture.MAX_SIZE);
        require(GraffitiPicture.fromData(broken).layers.size() == 1);
    }

    private static void checkLayers()
    {
        GraffitiPicture picture = new GraffitiPicture(2, 1);
        CanvasLayerData bottom = picture.layers.get(0);
        CanvasLayerData top = picture.addLayer(1, false);

        bottom.pixels[0] = 0xFF0000FF;
        top.pixels[0] = 0xFFFF0000;
        top.pixels[1] = 0xFF00FF00;

        require(picture.moveLayer(top.id, -1));
        require(picture.layers.get(0) == top && top.order == 0);
        require(picture.moveLayer(top.id, 1));
        require(picture.mergeDown(top.id));
        require(picture.layers.size() == 1);
        require(picture.layers.get(0).pixels[0] == 0xFFFF0000 && picture.layers.get(0).pixels[1] == 0xFF00FF00);
        require(!picture.removeLayer(picture.layers.get(0).id));
        require(picture.duplicateLayer(picture.layers.get(0).id) != null && picture.layers.size() == 2);
    }

    private static void checkResize()
    {
        GraffitiPicture picture = new GraffitiPicture(2, 2);

        picture.layers.get(0).pixels[3] = 0xFFFFFFFF;
        picture.resize(4, 4, 0.5F, 0.5F);

        require(picture.width == 4 && picture.height == 4);
        require(picture.layers.get(0).pixels.length == 16);
        require(picture.layers.get(0).pixels[2 * 4 + 2] == 0xFFFFFFFF);

        picture.resize(1, 1, 0F, 0F);
        require(picture.layers.get(0).pixels.length == 1 && picture.layers.get(0).pixels[0] == 0);
    }

    private static void checkComposite()
    {
        GraffitiPicture picture = new GraffitiPicture(4, 1);

        require(GraffitiRaster.composite(picture, false) == null);
        require(GraffitiRaster.composite(picture, true) == null);

        CanvasLayerData plain = picture.layers.get(0);
        CanvasLayerData glow = picture.addLayer(1, true);

        plain.pixels[0] = 0xFFFF0000;
        glow.pixels[1] = 0xFF00FF00;

        int[] plainImage = GraffitiRaster.composite(picture, false);
        int[] glowImage = GraffitiRaster.composite(picture, true);

        require(plainImage[0] == 0xFFFF0000 && plainImage[1] == 0);
        require(glowImage[1] == 0xFF00FF00 && glowImage[0] == 0);
        require(GraffitiRaster.colorAt(picture, 1, 0) == 0xFF00FF00);

        int[] all = GraffitiRaster.compositeAll(picture);

        for (int x = 0; x < picture.width; x++)
        {
            require(all[x] == GraffitiRaster.colorAt(picture, x, 0));
        }

        glow.visible = false;
        require(GraffitiRaster.composite(picture, true) == null);
        require(GraffitiRaster.compositeAll(picture)[1] == 0);

        plain.visible = false;
        require(GraffitiRaster.compositeAll(picture) == null);
        plain.visible = true;

        int[] blurred = GraffitiRaster.blur(new int[] {0, 0, 0xFFFFFFFF, 0, 0}, 5, 1, 1F);

        require(((blurred[1] >>> 24) & 255) > 0 && ((blurred[2] >>> 24) & 255) < 255);
        require(GraffitiRaster.blur(new int[] {0xFFFFFFFF}, 1, 1, 0F)[0] == 0xFFFFFFFF);

        int[] halo = GraffitiRaster.halo(new int[] {0, 0xFFFFFFFF, 0}, 3, 1, 1F, 1F);

        require(halo[1] == 0xFFFFFFFF && ((halo[0] >>> 24) & 255) > 0);
        require(((GraffitiRaster.fade(new int[] {0xFF000000}, 0.5F)[0] >>> 24) & 255) == 128);
    }

    private static void checkLights()
    {
        int[] glow = new int[8 * 8];

        glow[0] = 0xFFFF0000;
        glow[1] = 0xFFFF0000;
        glow[63] = 0xFF0000FF;

        List<GraffitiRaster.Light> lights = GraffitiRaster.lights(glow, 8, 8);

        require(lights.size() == 2);
        require(lights.get(0).u() < 0.5F && lights.get(0).v() < 0.5F && lights.get(0).red() == 1F && lights.get(0).blue() == 0F);
        require(lights.get(1).u() > 0.5F && lights.get(1).v() > 0.5F && lights.get(1).blue() == 1F);
        require(Math.abs(lights.get(0).weight() + lights.get(1).weight() - 1F) < 1e-5F);
        require(GraffitiRaster.lights(null, 8, 8).isEmpty());
    }

    private static void checkTools()
    {
        GraffitiPicture picture = new GraffitiPicture(3, 2);
        CanvasLayerData layer = picture.layers.get(0);

        layer.pixels[0] = 1 | 0xFF000000;
        GraffitiRaster.flipHorizontal(layer, 3, 2);
        require(layer.pixels[2] == (1 | 0xFF000000) && layer.pixels[0] == 0);
        GraffitiRaster.flipVertical(layer, 3, 2);
        require(layer.pixels[5] == (1 | 0xFF000000));

        int[] source = layer.pixels.clone();

        GraffitiRaster.shift(layer, source, 3, 2, -1, -1);
        require(layer.pixels[1] == (1 | 0xFF000000) && layer.pixels[5] == 0);

        int[] spot = new int[5 * 5];

        spot[12] = 0xFFFFFFFF;

        CanvasLayerData blurLayer = new GraffitiPicture(5, 5).layers.get(0);

        blurLayer.pixels = spot.clone();
        GraffitiRaster.blurDab(blurLayer, spot, 5, 5, 2, 2, 2, 1F);
        require(((blurLayer.pixels[12] >>> 24) & 255) < 255 && ((blurLayer.pixels[11] >>> 24) & 255) > 0);
    }

    private static void checkFactory()
    {
        GraffitiKeyframeFactory factory = GraffitiKeyframeFactory.INSTANCE;
        GraffitiPicture picture = factory.createEmpty();
        int hash = factory.contentHash(picture);

        require(factory.isStepped());
        require(factory.interpolate(null, picture, null, null, null, 0.5F) == picture);
        require(factory.copy(picture).equals(picture));
        require(factory.fromData(factory.toData(picture)).equals(picture));

        picture.touch();
        require(factory.contentHash(picture) != hash);
    }

    private static void require(boolean condition)
    {
        if (!condition)
        {
            throw new AssertionError("Graffiti check failed");
        }
    }
}
