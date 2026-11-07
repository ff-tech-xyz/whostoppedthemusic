package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import xyz.pyrehaven.whostoppedthemusic.JukeboxSetting;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;

/** Effective settings after defaults/overrides; persistence remains owned by Settings. */
public record ScenarioConfig(int priority, boolean active, boolean interrupt, Frequency frequency,
                             JukeboxSetting jukebox, boolean mustBeOutside, int yLevel,
                             List<Deck.Entry> songs) {
    // Kept game-independent; the playback adapter maps these values to vanilla MusicFrequency.
    public enum Frequency { DEFAULT, FREQUENT, CONSTANT }

    public ScenarioConfig withFrequency(Frequency value) {
        return new ScenarioConfig(priority, active, interrupt, value, jukebox, mustBeOutside, yLevel, songs);
    }

    public ScenarioConfig withJukebox(JukeboxSetting value) {
        return new ScenarioConfig(priority, active, interrupt, frequency, value, mustBeOutside, yLevel, songs);
    }

    public ScenarioConfig withActive(boolean value) {
        return new ScenarioConfig(priority, value, interrupt, frequency, jukebox, mustBeOutside, yLevel, songs);
    }

    public ScenarioConfig {
        if (priority < 1 || priority > 10) throw new IllegalArgumentException("priority must be 1–10");
        Objects.requireNonNull(frequency);
        Objects.requireNonNull(jukebox);
        songs = List.copyOf(songs);
        var ids = new HashSet<String>();
        for (var song : songs) {
            if (song.weight() > 100) throw new IllegalArgumentException("scenario weight must be 1–100");
            if (!ids.add(song.id())) throw new IllegalArgumentException("Duplicate song: " + song.id());
        }
    }
}
