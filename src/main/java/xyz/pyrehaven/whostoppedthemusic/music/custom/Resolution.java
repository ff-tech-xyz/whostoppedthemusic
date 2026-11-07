package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import xyz.pyrehaven.whostoppedthemusic.JukeboxSetting;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig.Frequency;

public record Resolution(List<ScenarioId> winners, List<Deck.Entry> entries, boolean interrupt,
                         Frequency frequency, JukeboxSetting jukebox) {
    public Resolution {
        winners = winners.stream().distinct().sorted().toList();
        if (winners.isEmpty()) throw new IllegalArgumentException("Resolution needs a winner");
        entries = List.copyOf(entries);
        Objects.requireNonNull(frequency);
        Objects.requireNonNull(jukebox);
    }

    public String key() { return winners.stream().map(ScenarioId::toString).collect(Collectors.joining("+")); }

    public boolean silent() { return entries.isEmpty(); }
}
