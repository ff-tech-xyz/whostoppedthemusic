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

    /** Lower scores sort first; artist and file names remain searchable behind titles. */
    public static int searchRank(String query, String title, String id) {
        String needle = normalize(query);
        if (needle.isEmpty()) return 0;
        String full = normalize(title);
        String[] parts = title.split(" - ", 2);
        String song = normalize(parts.length == 2 ? parts[1] : title);
        String artist = parts.length == 2 ? normalize(parts[0]) : "";
        if (song.equals(needle) || full.equals(needle)) return 0;
        if (song.startsWith(needle)) return 10;
        if (song.contains(" " + needle)) return 20;
        if (artist.equals(needle)) return 30;
        if (artist.startsWith(needle)) return 40;
        if (full.contains(needle)) return 50;
        String haystack = full + " " + normalize(id);
        if (java.util.Arrays.stream(needle.split(" ")).allMatch(haystack::contains)) return 60;
        if (needle.length() < 3) return Integer.MAX_VALUE;
        int distance = Math.min(distance(needle, song), distance(needle, artist));
        for (String word : full.split(" ")) distance = Math.min(distance, distance(needle, word));
        int tolerance = Math.min(3, Math.max(1, needle.length() / 4));
        return distance <= tolerance ? 100 + distance : Integer.MAX_VALUE;
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1], previous[j]) + 1,
                        previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return previous[right.length()];
    }
}
