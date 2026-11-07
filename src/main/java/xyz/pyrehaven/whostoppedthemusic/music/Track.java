package xyz.pyrehaven.whostoppedthemusic.music;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;

public record Track(String id, Kind kind, Component title, Sound sound, Metadata explicitMetadata, LocalSong local) {
    public enum Kind { SOUNDTRACK, DISC, LOCAL }

    public Track(String id, Kind kind, Component title, Sound sound) { this(id, kind, title, sound, null, null); }
    public Track(String id, Kind kind, Component title, Sound sound, Metadata metadata) { this(id, kind, title, sound, metadata, null); }

    public boolean playable() { return local == null || local.playable(); }

    public record Metadata(Component title, Component artist) {
        public Component description() {
            return artist.getString().isEmpty() ? title : title.copy().append("\n").append(artist);
        }
    }

    public Metadata metadata() { return explicitMetadata == null ? metadata(title) : explicitMetadata; }

    /** Resolve the retained translation in the current language, not at resource-apply time. */
    public static Metadata metadata(Component display) {
        var names = TrackNames.metadata(display.getString());
        if (names.artist().isEmpty()) return new Metadata(display, Component.empty());
        int start = names.artist().length() + 3;
        return new Metadata(slice(display, start, display.getString().length()), slice(display, 0, start - 3));
    }

    private static Component slice(Component source, int start, int end) {
        var result = Component.empty();
        int[] position = {0};
        source.visit((style, text) -> {
            int from = Math.max(0, start - position[0]);
            int to = Math.min(text.length(), end - position[0]);
            if (from < to) result.append(Component.literal(text.substring(from, to)).withStyle(style));
            position[0] += text.length();
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return result;
    }
}
