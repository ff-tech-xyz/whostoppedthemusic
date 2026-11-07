package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.custom.*;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig.Frequency;
import xyz.pyrehaven.whostoppedthemusic.music.custom.Situation.Precipitation;

final class ContextResolverTest {
    private static final ScenarioId FOREST = ScenarioId.biome("minecraft:forest");

    private static ScenarioConfig config(int priority, boolean active, boolean interrupt,
                                         Frequency frequency, JukeboxSetting jukebox,
                                         boolean outside, int y, Deck.Entry... songs) {
        return new ScenarioConfig(priority, active, interrupt, frequency, jukebox, outside, y, List.of(songs));
    }

    private static ScenarioConfig config(int priority, boolean active, Deck.Entry... songs) {
        return config(priority, active, false, Frequency.CONSTANT, JukeboxSetting.PAUSE_MUSIC, true, 64, songs);
    }

    private static Map<ScenarioId, ScenarioConfig> configs() {
        var result = new HashMap<ScenarioId, ScenarioConfig>();
        result.put(FOREST, config(2, true, new Deck.Entry("forest", 1)));
        for (var special : Special.values()) result.put(ScenarioId.special(special), config(3, false));
        return result;
    }

    private static Situation world(int y, boolean sky, boolean exposed, boolean rain, boolean thunder,
                                   Precipitation precipitation) {
        return new Situation(false, false, false, false, false, FOREST, Set.of(),
                y, sky, exposed, rain, thunder, precipitation);
    }

    private static Situation clear() { return world(64, true, true, false, false, Precipitation.NONE); }

    private static Resolution resolve(Situation situation, Map<ScenarioId, ScenarioConfig> configs) {
        return ContextResolver.resolve(situation, configs, Set.of());
    }

    @Test void creditsBeforeMenuBeforeEveryWorldCandidate() {
        var menu = ScenarioId.special(Special.MENU);
        var credits = ScenarioId.special(Special.CREDITS);
        var values = Map.of(menu, config(1, false, new Deck.Entry("menu", 1)),
                credits, config(1, false, new Deck.Entry("credits", 1)));
        for (boolean hasCredits : List.of(false, true)) {
            var situation = new Situation(hasCredits, true, true, true, true, null, Set.of(),
                    0, false, false, true, true, Precipitation.SNOW);
            var expected = hasCredits ? credits : menu;
            var result = resolve(situation, values);
            assertEquals(List.of(expected), result.winners());
            assertTrue(result.interrupt(), "unconditional specials ignore active/priority/interrupt fields");
            assertEquals(values.get(expected).songs(), result.entries());
            assertTrue(ContextResolver.resolve(situation, values, result.winners()).interrupt());
        }
        var creditsWithPlayer = new Situation(true, false, true, false, false, FOREST, Set.of(),
                64, true, true, false, false, Precipitation.NONE);
        assertEquals(List.of(credits), resolve(creditsWithPlayer, values).winners());
    }

    @Test void priorityAndActiveEmptySilenceNeverFallThrough() {
        var values = configs();
        var boss = ScenarioId.special(Special.BOSS);
        var s = new Situation(false, false, true, true, false, FOREST, Set.of(),
                64, true, true, false, false, Precipitation.NONE);
        values.put(boss, config(10, false));
        assertEquals(List.of(FOREST), resolve(s, values).winners());
        values.put(ScenarioId.special(Special.CREATIVE), config(4, true, new Deck.Entry("creative", 1)));
        assertEquals("creative", resolve(s, values).entries().getFirst().id());
        values.put(boss, config(10, true));
        var result = resolve(s, values);
        assertEquals(List.of(boss), result.winners());
        assertTrue(result.silent());
        values.put(FOREST, config(10, false, new Deck.Entry("forest", 1)));
        assertFalse(resolve(s, values).silent(), "biome is always a candidate; tied empty list contributes no songs");
    }

    @Test void tiesKeepRawDuplicateSumsAndStableKeysThroughTheDeck() {
        var values = configs();
        var rain = ScenarioId.special(Special.RAIN);
        values.put(FOREST, config(6, true, new Deck.Entry("shared", 100), new Deck.Entry("forest", 2)));
        values.put(rain, config(6, true, new Deck.Entry("shared", 100), new Deck.Entry("rain", 3)));
        var situation = world(70, true, true, true, false, Precipitation.RAIN);
        var result = resolve(situation, values);
        assertEquals(List.of(FOREST, rain), result.winners());
        assertEquals("biome/minecraft/forest+special/rain", result.key());
        assertEquals(List.of(new Deck.Entry("forest", 2), new Deck.Entry("rain", 3), new Deck.Entry("shared", 200)),
                result.entries());
        var reversed = new java.util.LinkedHashMap<ScenarioId, ScenarioConfig>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.reverseOrder()))
                .forEach(e -> reversed.put(e.getKey(), e.getValue()));
        assertEquals(result, resolve(situation, reversed));
        var deck = new Deck(new Random(1));
        deck.configure(result.entries(), true);
        var counts = new HashMap<String, Integer>();
        for (int i = 0; i < 205; i++) counts.merge(deck.next(), 1, Integer::sum);
        assertEquals(Map.of("shared", 200, "forest", 2, "rain", 3), counts);
    }

    @Test void onlyNewWinnersInterruptWhileFrequencyAndJukeboxCombineAcrossAllWinners() {
        var values = configs();
        var rain = ScenarioId.special(Special.RAIN);
        values.put(FOREST, config(6, true, true, Frequency.DEFAULT, JukeboxSetting.PLAY_OVER, true, 64));
        values.put(rain, config(6, true, false, Frequency.FREQUENT, JukeboxSetting.PAUSE_MUSIC, true, 64));
        var wet = world(70, true, true, true, false, Precipitation.RAIN);
        var first = resolve(clear(), values);
        assertTrue(first.interrupt());
        assertFalse(ContextResolver.resolve(clear(), values, first.winners()).interrupt());
        var tie = ContextResolver.resolve(wet, values, first.winners());
        assertFalse(tie.interrupt(), "the existing interrupting biome did not just become a winner");
        assertEquals(Frequency.FREQUENT, tie.frequency());
        assertEquals(JukeboxSetting.PAUSE_MUSIC, tie.jukebox());
        assertFalse(ContextResolver.resolve(clear(), values, tie.winners()).interrupt(), "leaving winner cannot interrupt");
        values.put(rain, config(6, true, true, Frequency.CONSTANT, JukeboxSetting.PLAY_OVER, true, 64));
        var joined = ContextResolver.resolve(wet, values, first.winners());
        assertTrue(joined.interrupt());
        assertEquals(Frequency.CONSTANT, joined.frequency());
        assertEquals(JukeboxSetting.PLAY_OVER, joined.jukebox());
        assertFalse(ContextResolver.resolve(wet, values, joined.winners()).interrupt());
        assertTrue(ContextResolver.resolve(wet, values, Set.of()).interrupt(), "prior state belongs to caller");
        values.put(FOREST, config(6, true, false, Frequency.DEFAULT, JukeboxSetting.PLAY_OVER, true, 64));
        values.put(rain, config(6, true, false, Frequency.DEFAULT, JukeboxSetting.PLAY_OVER, true, 64));
        assertEquals(Frequency.DEFAULT, resolve(wet, values).frequency());
    }

    @Test void undergroundUsesStrictEditableThresholdAndNoSky() {
        var values = configs();
        var underground = ScenarioId.special(Special.UNDERGROUND);
        values.put(underground, config(3, true));
        for (int y : List.of(63, 64)) for (boolean sky : List.of(false, true)) {
            assertEquals(List.of(y < 64 && !sky ? underground : FOREST),
                    resolve(world(y, sky, false, false, false, Precipitation.NONE), values).winners());
        }
        values.put(underground, config(3, true, false, Frequency.CONSTANT, JukeboxSetting.PAUSE_MUSIC, true, -10));
        assertEquals(List.of(underground), resolve(world(-11, false, false, false, false, Precipitation.NONE), values).winners());
        assertEquals(List.of(FOREST), resolve(world(-10, false, false, false, false, Precipitation.NONE), values).winners());
        values.put(FOREST, config(5, true, new Deck.Entry("cave", 1)));
        assertEquals(List.of(FOREST), resolve(world(-11, false, false, false, false, Precipitation.NONE), values).winners());
    }

    @Test void everyWeatherPrecipitationAndExposureCombination() {
        for (var precipitation : Precipitation.values()) for (boolean raining : List.of(false, true))
            for (boolean thundering : List.of(false, true)) for (boolean sky : List.of(false, true))
                for (boolean exposed : List.of(false, true)) for (boolean outside : List.of(false, true)) {
                    var values = configs();
                    for (var special : List.of(Special.THUNDER, Special.RAIN, Special.SNOW)) {
                        values.put(ScenarioId.special(special), config(6, true, false, Frequency.CONSTANT,
                                JukeboxSetting.PAUSE_MUSIC, outside, 64, new Deck.Entry(special.name(), 1)));
                    }
                    var expected = new ArrayList<ScenarioId>();
                    if (!outside || (sky && exposed)) {
                        if (thundering && precipitation != Precipitation.NONE) expected.add(ScenarioId.special(Special.THUNDER));
                        if (raining && precipitation == Precipitation.RAIN) expected.add(ScenarioId.special(Special.RAIN));
                        if (raining && precipitation == Precipitation.SNOW) expected.add(ScenarioId.special(Special.SNOW));
                    }
                    if (expected.isEmpty()) expected.add(FOREST);
                    expected.sort(null);
                    var situation = world(70, sky, exposed, raining, thundering, precipitation);
                    assertEquals(expected, resolve(situation, values).winners(), situation + ", outside=" + outside);
                }
    }

    @Test void aquaticNeedsPlayerUnderwaterAndOceanOrRiverAndCreativeNeedsItsOwnFlag() {
        var values = configs();
        var aquatic = ScenarioId.special(Special.AQUATIC);
        var creative = ScenarioId.special(Special.CREATIVE);
        values.put(aquatic, config(8, true));
        values.put(creative, config(4, true));
        for (var tags : List.of(Set.<String>of(), Set.of("minecraft:is_ocean"), Set.of("minecraft:is_river"))) {
            for (boolean underwater : List.of(false, true)) for (boolean isCreative : List.of(false, true)) {
                var s = new Situation(false, false, false, isCreative, underwater, FOREST, tags,
                        70, true, true, false, false, Precipitation.NONE);
                assertEquals(List.of(underwater && !tags.isEmpty() ? aquatic : isCreative ? creative : FOREST),
                        resolve(s, values).winners());
            }
        }
    }

    @Test void contractsAreImmutableAndScenarioWeightsNotMergedWeightsAreBounded() {
        assertEquals("biome/terralith/moonlight_grove", ScenarioId.biome("terralith:moonlight_grove").toString());
        assertEquals("special/aquatic", ScenarioId.special(Special.AQUATIC).toString());
        assertThrows(IllegalArgumentException.class, () -> ScenarioId.biome("Forest"));
        assertThrows(IllegalArgumentException.class, () -> new ScenarioId(ScenarioId.Kind.SPECIAL, "RAIN"));
        assertThrows(IllegalArgumentException.class, () -> config(0, true));
        assertThrows(IllegalArgumentException.class, () -> config(11, true));
        assertThrows(IllegalArgumentException.class, () -> new Deck.Entry("bad", 0));
        assertThrows(IllegalArgumentException.class, () -> config(2, true, new Deck.Entry("big", 101)));
        assertThrows(IllegalArgumentException.class, () -> config(2, true, new Deck.Entry("same", 1), new Deck.Entry("same", 2)));
        var songs = new ArrayList<>(List.of(new Deck.Entry("song", 100)));
        var config = new ScenarioConfig(2, true, false, Frequency.CONSTANT, JukeboxSetting.PAUSE_MUSIC, true, 64, songs);
        songs.clear();
        assertEquals(1, config.songs().size());
        var tags = new HashSet<>(Set.of("minecraft:is_river"));
        var s = new Situation(false, false, false, false, true, FOREST, tags, 64, true, true, false, false, Precipitation.NONE);
        tags.clear();
        assertEquals(Set.of("minecraft:is_river"), s.biomeTags());
        var result = resolve(clear(), configs());
        assertThrows(UnsupportedOperationException.class, () -> result.winners().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
        assertThrows(IllegalArgumentException.class, () -> resolve(clear(), Map.of()));
    }
}
