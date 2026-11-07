package xyz.pyrehaven.whostoppedthemusic.music;

import java.util.Locale;
import java.util.function.Function;

public final class TrackNames {
    private TrackNames() { }

    public static String key(String fileId, String discEvent) {
        String[] parts = fileId.split(":", 2);
        if (discEvent != null) {
            String[] event = discEvent.split(":", 2);
            return "jukebox_song." + event[0] + "." + event[1].substring("music_disc.".length());
        }
        return (parts[0].equals("minecraft") ? "" : parts[0] + ".") + parts[1].replace('/', '.');
    }

    public static String title(String fileId, String discEvent, Function<String, String> translations) {
        String key = key(fileId, discEvent);
        String translated = translations.apply(key);
        return translated == null || translated.equals(key) ? prettify(fileId) : translated;
    }

    public static String prettify(String id) {
        String name = id.substring(Math.max(id.lastIndexOf('/'), id.lastIndexOf(':')) + 1);
        StringBuilder result = new StringBuilder();
        for (String word : name.replace('_', ' ').replace('-', ' ').split(" +")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }
}
