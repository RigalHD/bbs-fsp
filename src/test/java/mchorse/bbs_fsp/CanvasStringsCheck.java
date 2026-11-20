package mchorse.bbs_fsp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CanvasStringsCheck
{
    private static final Path FOLDER = Path.of("src/main/resources/assets/bbs_fsp/assets/strings");
    private static final String[] LANGUAGES = {
        "ar_ar", "zh_cn", "zh_tw", "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "id_id", "ko_kr",
        "pl_pl", "pt_br", "pt_pt", "ru_ru", "th_th", "tr_tr", "uk_ua", "vi_vn", "ur_pk"
    };
    private static final Pattern PLACEHOLDER = Pattern.compile("%[0-9]*\\$?[sd]|\\{[0-9]+}");

    public static void main(String[] args) throws IOException
    {
        Map<String, String> english = read("en_us");
        List<String> problems = new ArrayList<>();

        for (String language : LANGUAGES)
        {
            Map<String, String> strings = read(language);
            Set<String> missing = new TreeSet<>(english.keySet());
            Set<String> extra = new TreeSet<>(strings.keySet());

            missing.removeAll(strings.keySet());
            extra.removeAll(english.keySet());

            if (!missing.isEmpty())
            {
                problems.add(language + " is missing " + missing);
            }

            if (!extra.isEmpty())
            {
                problems.add(language + " has unknown keys " + extra);
            }

            for (Map.Entry<String, String> entry : strings.entrySet())
            {
                if (entry.getValue().isBlank())
                {
                    problems.add(language + " has an empty value for " + entry.getKey());
                }

                String source = english.get(entry.getKey());

                if (source != null && !placeholders(source).equals(placeholders(entry.getValue())))
                {
                    problems.add(language + " changes the placeholders of " + entry.getKey());
                }
            }
        }

        if (!problems.isEmpty())
        {
            throw new AssertionError(String.join("\n", problems));
        }
    }

    private static Map<String, String> read(String language) throws IOException
    {
        Path file = FOLDER.resolve(language + ".json");
        JsonObject object = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> strings = new TreeMap<>();

        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            strings.put(entry.getKey(), entry.getValue().getAsString());
        }

        return strings;
    }

    private static List<String> placeholders(String value)
    {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);

        while (matcher.find())
        {
            found.add(matcher.group());
        }

        found.sort(String::compareTo);

        return found;
    }
}
