package mchorse.bbs_fsp.ui;

import mchorse.bbs_fsp.BbsFspMod;
import mchorse.bbs_fsp.canvas.brush.CanvasBrushSet;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The brush settings and colours survive between sessions in a small file next to the BBS
 * settings. Changes are written out a moment after the last one, not on every trackpad tick.
 */
public final class CanvasBrushStore
{
    private static final String FILE_NAME = "bbs_fsp_brushes.json";
    private static final long SAVE_DELAY = 1000L;

    private static CanvasBrushSet brushes;
    private static long savedRevision = -1;
    private static long changedAt;
    private static long lastRevision = -1;

    private CanvasBrushStore()
    {}

    public static CanvasBrushSet get()
    {
        if (brushes == null)
        {
            brushes = new CanvasBrushSet();
            load();
            savedRevision = brushes.getRevision();
            lastRevision = savedRevision;
        }

        return brushes;
    }

    /** Called every frame the canvas editor is visible; writes the file once changes settle. */
    public static void saveLater()
    {
        if (brushes == null)
        {
            return;
        }

        long revision = brushes.getRevision();
        long now = System.currentTimeMillis();

        if (revision != lastRevision)
        {
            lastRevision = revision;
            changedAt = now;
        }

        if (revision != savedRevision && now - changedAt >= SAVE_DELAY)
        {
            save();
        }
    }

    public static void save()
    {
        if (brushes == null || brushes.getRevision() == savedRevision)
        {
            return;
        }

        File file = getFile();

        if (file == null)
        {
            return;
        }

        try
        {
            file.getParentFile().mkdirs();
            DataToString.write(file, brushes.toData(), true);
            savedRevision = brushes.getRevision();
        }
        catch (IOException e)
        {
            BbsFspMod.LOGGER.warn("Failed to save canvas brushes to " + file, e);
            savedRevision = brushes.getRevision();
        }
    }

    private static void load()
    {
        File file = getFile();

        if (file == null || !file.isFile())
        {
            return;
        }

        try
        {
            BaseType data = DataToString.fromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));

            if (data instanceof MapType map)
            {
                brushes.fromData(map);
            }
        }
        catch (IOException | RuntimeException e)
        {
            BbsFspMod.LOGGER.warn("Failed to read canvas brushes from " + file, e);
        }
    }

    private static File getFile()
    {
        File folder = BBSMod.getSettingsFolder();

        return folder == null ? null : new File(folder, FILE_NAME);
    }
}
