package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import xyz.pyrehaven.whostoppedthemusic.music.TrackNames;

final class TrackNamesTest {
    @Test void soundtrackAndDiscKeysUseVanillaTranslations() {
        assertEquals("music.game.sweden", TrackNames.key("minecraft:music/game/sweden", null));
        assertEquals("C418 - Sweden", TrackNames.title("minecraft:music/game/sweden", null,
                key -> key.equals("music.game.sweden") ? "C418 - Sweden" : key));
        assertEquals("jukebox_song.minecraft.pigstep", TrackNames.key("minecraft:records/pigstep", "minecraft:music_disc.pigstep"));
        assertEquals("Lena Raine - Pigstep", TrackNames.title("minecraft:records/pigstep", "minecraft:music_disc.pigstep",
                key -> "Lena Raine - Pigstep"));
    }

    @Test void missingAndModdedNamesFallBackToReadableFileName() {
        assertEquals("other.music.game.night_song", TrackNames.key("other:music/game/night_song", null));
        assertEquals("Night Song", TrackNames.title("other:music/game/night_song", null, key -> key));
        assertEquals("Aerie", TrackNames.prettify("minecraft:music/game/swamp/aerie"));
    }
}
