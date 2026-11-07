package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.util.Objects;
import java.util.Set;

/** Camera-block environment plus player abilities/underwater and screen/dragon flags. */
public record Situation(boolean credits, boolean menu, boolean boss, boolean creative,
                        boolean underWater, ScenarioId biome, Set<String> biomeTags,
                        int cameraY, boolean canSeeSky, boolean exposedToRain,
                        boolean raining, boolean thundering, Precipitation precipitation) {
    public enum Precipitation { RAIN, SNOW, NONE }

    public Situation {
        biomeTags = Set.copyOf(biomeTags);
        Objects.requireNonNull(precipitation);
        if (biome != null && biome.kind() != ScenarioId.Kind.BIOME) {
            throw new IllegalArgumentException("Expected biome scenario");
        }
        if (!credits && !menu) Objects.requireNonNull(biome, "In-world situation needs a biome");
    }
}
