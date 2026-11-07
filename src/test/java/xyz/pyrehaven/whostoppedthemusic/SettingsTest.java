package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;
import xyz.pyrehaven.whostoppedthemusic.music.custom.Special;

final class SettingsTest {
    @TempDir
    Path directory;

    @Test
    void defaultsDoNotWriteUntilFirstRunAndFirstRunIsAppliedOnlyOnce() throws Exception {
        Path file = directory.resolve("config/whostoppedthemusic.json");
        Settings settings = Settings.load(file);
        assertEquals(Mode.VANILLA, settings.mode());
        assertEquals(JukeboxSetting.PAUSE_MUSIC, settings.jukebox());
        assertFalse(settings.firstRunDone());
        assertFalse(Files.exists(file));
        AtomicInteger applications = new AtomicInteger();
        settings.completeFirstRun(applications::incrementAndGet);
        Settings reloaded = Settings.load(file);
        reloaded.completeFirstRun(applications::incrementAndGet);
        assertTrue(reloaded.firstRunDone());
        assertEquals(1, applications.get());
    }

    @Test
    void preferenceRoundTripsWithUnknownKeysAndLeavesNoTemporaryFile() throws Exception {
        Path file = directory.resolve("whostoppedthemusic.json");
        Files.writeString(file, "{\"firstRunDone\":true,\"futureField\":{\"ignored\":1}}");
        Settings settings = Settings.load(file);
        settings.setJukebox(JukeboxSetting.PLAY_OVER);
        Settings reloaded = Settings.load(file);
        assertTrue(reloaded.firstRunDone());
        assertEquals(JukeboxSetting.PLAY_OVER, reloaded.jukebox());
        try (var entries = Files.list(directory)) {
            assertEquals(1, entries.count());
        }
        assertTrue(Files.readString(file).contains("\"configVersion\": 2"));
    }

    @Test
    void brokenOrUnknownEnumFileIsPreservedAndDefaultsAreRecovered() throws Exception {
        Path file = directory.resolve("whostoppedthemusic.json");
        for (String broken : new String[]{"{broken", "null", "[]", "{\"jukebox\":\"NEW_VALUE\"}",
                "{\"mode\":\"UNKNOWN\"}", "{\"basic\":null}", "{\"basic\":[]}", "{\"basic\":{\"unticked\":null}}",
                "{\"custom\":null}"}) {
            Files.writeString(file, broken);
            Settings recovered = Settings.load(file);
            assertFalse(recovered.firstRunDone());
            assertEquals(JukeboxSetting.PAUSE_MUSIC, recovered.jukebox());
            assertFalse(Files.exists(file));
            assertEquals(broken, Files.readString(directory.resolve("whostoppedthemusic.json.broken")));
        }
    }

    @Test
    void failedVanillaInitializationDoesNotMarkFirstRunDone() {
        Path file = directory.resolve("whostoppedthemusic.json");
        Settings settings = Settings.load(file);
        assertThrows(IllegalStateException.class,
                () -> settings.completeFirstRun(() -> { throw new IllegalStateException("not saved"); }));
        assertFalse(settings.firstRunDone());
        assertFalse(Files.exists(file));
    }

    @Test
    void basicSelectionAndModeRoundTripAndNewTracksStartTicked() throws Exception {
        Path file = directory.resolve("whostoppedthemusic.json");
        var settings = Settings.load(file);
        assertTrue(settings.shuffle());
        assertTrue(settings.ticked("minecraft:records/cat"));
        settings.setMode(Mode.BASIC);
        settings.setShuffle(false);
        settings.setAllTicked(List.of("a", "b", "c"), false);
        settings.setTicked("b", true);
        var loaded = Settings.load(file);
        assertEquals(Mode.BASIC, loaded.mode());
        assertFalse(loaded.shuffle());
        assertFalse(loaded.ticked("a"));
        assertTrue(loaded.ticked("b"));
        assertFalse(loaded.ticked("c"));
        assertTrue(loaded.ticked("new-track"));
        loaded.setAllTicked(List.of("a", "c"), true);
        assertTrue(Settings.load(file).ticked("c"));
        assertFalse(Files.readString(file).contains("stopped"));
    }

    @Test
    void v1MigrationAndSparseCustomRoundTripRetainAbsentBiomesAndExplicitSilence() throws Exception {
        Path file = directory.resolve("whostoppedthemusic.json");
        Files.writeString(file, """
                {"configVersion":1,"firstRunDone":true,"mode":"BASIC","jukebox":"PLAY_OVER",
                 "basic":{"shuffle":false,"unticked":["old"]}}
                """);
        var settings = Settings.load(file);
        var id = ScenarioId.biome("pack:grove");
        var defaults = customDefaults(List.of(new Deck.Entry("vanilla", 1)));
        assertEquals(defaults, settings.scenario(id, defaults));
        settings.discoverBiomes(Map.of("pack:grove", "pack"));
        var silent = customDefaults(List.of());
        settings.setScenario(id, silent, defaults);
        settings.setMode(Mode.CUSTOM);
        var loaded = Settings.load(file);
        assertEquals(Mode.CUSTOM, loaded.mode());
        assertFalse(loaded.shuffle());
        assertFalse(loaded.ticked("old"));
        assertTrue(loaded.firstRunDone());
        assertEquals(JukeboxSetting.PLAY_OVER, loaded.jukebox());
        assertTrue(loaded.scenario(id, defaults).songs().isEmpty());
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var override = json.getAsJsonObject("custom").getAsJsonObject("scenarios").getAsJsonObject(id.toString());
        assertEquals(1, override.size());
        assertTrue(override.getAsJsonArray("songs").isEmpty());
        loaded.discoverBiomes(Map.of("minecraft:forest", "overworld"));
        loaded.applyFrequencyToAll(ScenarioConfig.Frequency.FREQUENT);
        loaded.applyJukeboxToAll(JukeboxSetting.PLAY_OVER);
        loaded = Settings.load(file);
        assertEquals("pack", loaded.knownBiomes().get("pack:grove"));
        assertEquals(ScenarioConfig.Frequency.FREQUENT, loaded.scenario(id, defaults).frequency());
        assertEquals(JukeboxSetting.PLAY_OVER, loaded.scenario(id, defaults).jukebox());
        var future = ScenarioId.biome("future:new");
        assertEquals(loaded.scenario(id, defaults).frequency(), loaded.scenario(future, defaults).frequency());
        assertEquals(JukeboxSetting.PLAY_OVER, loaded.scenario(future, defaults).jukebox());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test
    void weightsClampAndMissingSongsFollowReloadedDefaults() throws Exception {
        Path file = directory.resolve("whostoppedthemusic.json");
        Files.writeString(file, """
                {"mode":"CUSTOM","custom":{"scenarios":{
                 "special/rain":{"songs":[{"id":"missing:a","weight":0},{"id":"missing:b","weight":500}]},
                 "biome/minecraft/forest":{"priority":9}}}}
                """);
        var loaded = Settings.load(file);
        var defaults = customDefaults(List.of());
        var rain = ScenarioId.special(Special.RAIN);
        assertEquals(List.of(1, 100), loaded.scenario(rain, defaults).songs().stream().map(e -> e.weight()).toList());
        var forest = ScenarioId.biome("minecraft:forest");
        var changed = customDefaults(List.of(new Deck.Entry("new:pool", 2)));
        assertEquals(changed.songs(), loaded.scenario(forest, changed).songs());
        assertEquals(9, loaded.scenario(forest, changed).priority());
        loaded.setScenario(forest, changed, changed);
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertFalse(json.getAsJsonObject("custom").getAsJsonObject("scenarios").has(forest.toString()));
        assertEquals(2, Settings.load(file).scenario(rain, defaults).songs().size());
    }

    @Test
    void loadingV1PreservesBasicAndLeavesEveryCustomScenarioAtDefaultsWithoutWriting() throws Exception {
        Path file = directory.resolve("whostoppedthemusic.json");
        String original = """
                {"configVersion":1,"firstRunDone":true,"mode":"BASIC","jukebox":"PLAY_OVER",
                 "basic":{"shuffle":false,"unticked":["minecraft:records/cat","custom:My song.mp3"]}}
                """;
        Files.writeString(file, original);
        var loaded = Settings.load(file);
        assertEquals(Mode.BASIC, loaded.mode());
        assertFalse(loaded.shuffle());
        assertFalse(loaded.ticked("minecraft:records/cat"));
        assertFalse(loaded.ticked("custom:My song.mp3"));
        assertTrue(loaded.ticked("new:track"));
        assertTrue(loaded.firstRunDone());
        assertEquals(JukeboxSetting.PLAY_OVER, loaded.jukebox());
        assertTrue(loaded.knownBiomes().isEmpty());
        var defaults = customDefaults(List.of(new Deck.Entry("loaded:default", 2)));
        for (var special : Special.values()) {
            assertEquals(defaults, loaded.scenario(ScenarioId.special(special), defaults));
        }
        assertEquals(defaults, loaded.scenario(ScenarioId.biome("minecraft:forest"), defaults));
        assertEquals(defaults, loaded.scenario(ScenarioId.biome("future:grove"), defaults));
        loaded.completeFirstRun(() -> fail("v1 first-run initialization must not run again"));
        assertEquals(original, Files.readString(file), "loading v1 must not rewrite it");

        loaded.setMode(Mode.CUSTOM);
        var migrated = Settings.load(file);
        assertFalse(migrated.shuffle());
        assertFalse(migrated.ticked("custom:My song.mp3"));
        assertFalse(migrated.ticked("minecraft:records/cat"));
        assertEquals(defaults, migrated.scenario(ScenarioId.special(Special.THUNDER), defaults));
        assertEquals(JukeboxSetting.PLAY_OVER, migrated.jukebox());
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(2, json.get("configVersion").getAsInt());
        assertTrue(json.getAsJsonObject("custom").getAsJsonObject("scenarios").isEmpty());
    }

    @Test
    void v2RoundTripsEveryScenarioFieldAndUnresolvedSongsWithoutLosingBasic() throws Exception {
        Path file = directory.resolve("whostoppedthemusic.json");
        var settings = Settings.load(file);
        settings.completeFirstRun(() -> {});
        settings.setMode(Mode.CUSTOM);
        settings.setShuffle(false);
        settings.setTicked("basic:retained", false);
        settings.setJukebox(JukeboxSetting.PLAY_OVER);
        var absent = ScenarioId.biome("removed:grove");
        var thunder = ScenarioId.special(Special.THUNDER);
        var defaults = customDefaults(List.of(new Deck.Entry("loaded:default", 2)));
        var edited = new ScenarioConfig(10, false, true, ScenarioConfig.Frequency.DEFAULT,
                JukeboxSetting.PLAY_OVER, true, -24,
                List.of(new Deck.Entry("custom:My song.mp3", 1), new Deck.Entry("missing:track", 100)));
        settings.discoverBiomes(Map.of("removed:grove", "removed"));
        settings.setScenario(absent, edited, defaults);
        settings.setScenario(thunder, defaults.withSongs(List.of()), defaults);

        var loaded = Settings.load(file);
        assertEquals(Mode.CUSTOM, loaded.mode());
        assertTrue(loaded.firstRunDone());
        assertFalse(loaded.shuffle());
        assertFalse(loaded.ticked("basic:retained"));
        assertTrue(loaded.ticked("new:track"));
        assertEquals(JukeboxSetting.PLAY_OVER, loaded.jukebox());
        assertEquals(Map.of("removed:grove", "removed"), loaded.knownBiomes());
        assertEquals(edited, loaded.scenario(absent, defaults));
        assertEquals(List.of(), loaded.scenario(thunder, defaults).songs());
        assertEquals(defaults, loaded.scenario(ScenarioId.biome("future:grove"), defaults));
        loaded.setMode(Mode.BASIC);
        var basic = Settings.load(file);
        assertEquals(Mode.BASIC, basic.mode());
        assertFalse(basic.ticked("basic:retained"));
        assertEquals(edited, basic.scenario(absent, defaults));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test
    void failedSaveKeepsLivePreferencesAndCanSaveAfterStorageRecovers() throws Exception {
        Path parent = directory.resolve("blocked");
        Path file = parent.resolve("settings.json");
        var settings = Settings.load(file);
        Files.writeString(parent, "not a directory");
        assertDoesNotThrow(() -> settings.setMode(Mode.CUSTOM));
        assertDoesNotThrow(() -> settings.setTicked("song", false));
        assertEquals(Mode.CUSTOM, settings.mode());
        assertFalse(settings.ticked("song"));
        assertEquals("not a directory", Files.readString(parent));
        Files.delete(parent);
        settings.setShuffle(false);
        assertEquals(Mode.CUSTOM, Settings.load(file).mode());
        assertFalse(Settings.load(file).ticked("song"));
    }

    @Test
    void badCustomEntriesDoNotResetGoodScenariosOrBasicPreferences() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, """
                {"firstRunDone":true,"mode":"CUSTOM","basic":{"shuffle":false,"unticked":["kept"]},
                 "custom":{"knownBiomes":{"bad":"invalid","pack:grove":"pack"},"scenarios":{
                  "special/rain":{"songs":null},"not-a-scenario":{},
                  "special/thunder":{"priority":9},"special/menu":{"songs":[]}}}}
                """);
        var settings = Settings.load(file);
        var defaults = customDefaults(List.of(new Deck.Entry("default", 1)));
        assertTrue(settings.firstRunDone());
        assertEquals(Mode.CUSTOM, settings.mode());
        assertFalse(settings.shuffle());
        assertFalse(settings.ticked("kept"));
        assertEquals(defaults, settings.scenario(ScenarioId.special(Special.RAIN), defaults));
        assertEquals(9, settings.scenario(ScenarioId.special(Special.THUNDER), defaults).priority());
        assertTrue(settings.scenario(ScenarioId.special(Special.MENU), defaults).songs().isEmpty());
        assertEquals(Map.of("pack:grove", "pack"), settings.knownBiomes());
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(directory.resolve("settings.json.broken")));
        settings.setShuffle(true);
        assertEquals(9, Settings.load(file).scenario(ScenarioId.special(Special.THUNDER), defaults).priority());
    }

    private static ScenarioConfig customDefaults(
            List<Deck.Entry> songs) {
        return new ScenarioConfig(2, true, false,
                ScenarioConfig.Frequency.CONSTANT,
                JukeboxSetting.PAUSE_MUSIC, false, 64, songs);
    }
}
