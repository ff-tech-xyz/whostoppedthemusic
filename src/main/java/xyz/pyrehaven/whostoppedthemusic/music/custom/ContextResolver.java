package xyz.pyrehaven.whostoppedthemusic.music.custom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import xyz.pyrehaven.whostoppedthemusic.JukeboxSetting;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig.Frequency;
import xyz.pyrehaven.whostoppedthemusic.music.custom.Situation.Precipitation;

/** Stateless resolver. MusicDirector supplies its previous tick's winners, empty on first use. */
public final class ContextResolver {
    private ContextResolver() {}

    public static Resolution resolve(Situation situation, Map<ScenarioId, ScenarioConfig> configs,
                                     Collection<ScenarioId> previousWinners) {
        if (situation.credits()) return combine(List.of(ScenarioId.special(Special.CREDITS)), configs, previousWinners);
        if (situation.menu()) return combine(List.of(ScenarioId.special(Special.MENU)), configs, previousWinners);

        var candidates = new ArrayList<ScenarioId>();
        candidates.add(situation.biome());
        for (var special : Special.values()) {
            if (special == Special.MENU || special == Special.CREDITS) continue;
            var id = ScenarioId.special(special);
            var config = config(configs, id);
            if (config.active() && applies(special, situation, config)) candidates.add(id);
        }
        int priority = candidates.stream().mapToInt(id -> config(configs, id).priority()).max().orElseThrow();
        return combine(candidates.stream().filter(id -> config(configs, id).priority() == priority).toList(),
                configs, previousWinners);
    }

    private static boolean applies(Special special, Situation s, ScenarioConfig config) {
        return switch (special) {
            case BOSS -> s.boss();
            case AQUATIC -> s.underWater() && (s.biomeTags().contains("minecraft:is_ocean")
                    || s.biomeTags().contains("minecraft:is_river"));
            case CREATIVE -> s.creative();
            case UNDERGROUND -> !s.canSeeSky() && s.cameraY() < config.yLevel();
            case THUNDER -> s.thundering() && s.precipitation() != Precipitation.NONE && outside(s, config);
            case RAIN -> s.raining() && s.precipitation() == Precipitation.RAIN && outside(s, config);
            case SNOW -> s.raining() && s.precipitation() == Precipitation.SNOW && outside(s, config);
            case MENU, CREDITS -> false;
        };
    }

    private static boolean outside(Situation s, ScenarioConfig config) {
        return !config.mustBeOutside() || (s.canSeeSky() && s.exposedToRain());
    }

    private static Resolution combine(List<ScenarioId> winners, Map<ScenarioId, ScenarioConfig> configs,
                                      Collection<ScenarioId> previousWinners) {
        var songs = new TreeMap<String, Integer>();
        boolean interrupt = false;
        var frequency = Frequency.DEFAULT;
        var jukebox = JukeboxSetting.PLAY_OVER;
        for (var id : winners) {
            var config = config(configs, id);
            boolean unconditional = id.equals(ScenarioId.special(Special.MENU))
                    || id.equals(ScenarioId.special(Special.CREDITS));
            interrupt |= unconditional || (!previousWinners.contains(id) && config.interrupt());
            if (config.frequency().ordinal() > frequency.ordinal()) frequency = config.frequency();
            if (config.jukebox() == JukeboxSetting.PAUSE_MUSIC) jukebox = JukeboxSetting.PAUSE_MUSIC;
            for (var song : config.songs()) songs.merge(song.id(), song.weight(), Math::addExact);
        }
        var entries = songs.entrySet().stream().map(e -> new Deck.Entry(e.getKey(), e.getValue())).toList();
        return new Resolution(winners, entries, interrupt, frequency, jukebox);
    }

    private static ScenarioConfig config(Map<ScenarioId, ScenarioConfig> configs, ScenarioId id) {
        var config = configs.get(id);
        if (config == null) throw new IllegalArgumentException("Missing effective scenario config: " + id);
        return config;
    }
}
