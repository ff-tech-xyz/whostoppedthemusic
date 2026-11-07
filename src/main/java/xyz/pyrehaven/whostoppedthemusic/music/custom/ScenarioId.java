package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.util.Locale;
import java.util.Objects;

public record ScenarioId(Kind kind, String id) implements Comparable<ScenarioId> {
    public enum Kind { BIOME, SPECIAL }

    public ScenarioId {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(id);
        if (kind == Kind.BIOME) {
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                throw new IllegalArgumentException("Expected namespaced biome id: " + id);
            }
        } else {
            Special.valueOf(id.toUpperCase(Locale.ROOT));
            if (!id.equals(id.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Expected lowercase special id: " + id);
            }
        }
    }

    public static ScenarioId biome(String id) { return new ScenarioId(Kind.BIOME, id); }

    public static ScenarioId special(Special special) {
        return new ScenarioId(Kind.SPECIAL, special.name().toLowerCase(Locale.ROOT));
    }

    @Override public String toString() {
        return kind == Kind.BIOME ? "biome/" + id.replace(':', '/') : "special/" + id;
    }

    @Override public int compareTo(ScenarioId other) { return toString().compareTo(other.toString()); }
}
