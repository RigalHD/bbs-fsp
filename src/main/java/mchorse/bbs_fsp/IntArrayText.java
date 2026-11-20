package mchorse.bbs_fsp;

import mchorse.bbs_mod.data.types.IntArrayType;

/**
 * Reads back the text BBS writes for an int array ({@code [i;1,2,3]}). BBS turns data into text
 * whenever it goes through the clipboard (copying keyframes, clips, forms, saving presets), but
 * its parser has no case for that form and throws, so anything holding pixels could be copied
 * and never pasted. A mixin on BBS's parser hands such values here.
 */
public final class IntArrayText
{
    private static final String PREFIX = "[i;";

    private IntArrayText()
    {}

    /** The int array the trimmed text spells, or null when it is not one. */
    public static IntArrayType parse(String string)
    {
        if (!string.startsWith(PREFIX) || !string.endsWith("]"))
        {
            return null;
        }

        int end = string.length() - 1;
        int count = 0;
        boolean digits = false;

        for (int i = PREFIX.length(); i < end; i++)
        {
            if (string.charAt(i) == ',')
            {
                count += 1;
            }
            else if (!Character.isWhitespace(string.charAt(i)))
            {
                digits = true;
            }
        }

        int[] values = new int[digits ? count + 1 : 0];
        int start = PREFIX.length();

        try
        {
            for (int i = 0; i < values.length; i++)
            {
                int comma = string.indexOf(',', start);
                int stop = comma == -1 || comma > end ? end : comma;

                values[i] = Integer.parseInt(string.substring(start, stop).trim());
                start = stop + 1;
            }
        }
        catch (NumberFormatException e)
        {
            return null;
        }

        return new IntArrayType(values);
    }
}
