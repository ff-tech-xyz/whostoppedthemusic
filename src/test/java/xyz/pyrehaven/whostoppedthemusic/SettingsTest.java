package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        assertTrue(Files.readString(file).contains("\"configVersion\": 1"));
    }

    @Test
    void brokenOrUnknownEnumFileIsPreservedAndDefaultsAreRecovered() throws Exception {
        Path file = directory.resolve("whostoppedthemusic.json");
        for (String broken : new String[]{"{broken", "null", "[]", "{\"jukebox\":\"NEW_VALUE\"}"}) {
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
}
