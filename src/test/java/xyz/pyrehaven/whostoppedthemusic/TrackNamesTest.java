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

    @Test void searchRanksSongBeforePrefixArtistAndLooseMatches() {
        int exact = TrackNames.searchRank("cat", "C418 - Cat", "minecraft:records/cat");
        int prefix = TrackNames.searchRank("cat", "Artist - Catwalk", "other:catwalk");
        int artist = TrackNames.searchRank("cat", "Cat - Elsewhere", "other:elsewhere");
        int loose = TrackNames.searchRank("cat", "Artist - Scattered", "other:scattered");
        assertTrue(exact < prefix && prefix < artist && artist < loose);
        assertEquals(30, TrackNames.searchRank("  LENA RAINE  ", "Lena Raine - Pigstep", "minecraft:records/pigstep"));
        assertEquals(60, TrackNames.searchRank("pig raine", "Lena Raine - Pigstep", "minecraft:records/pigstep"));
        assertEquals(0, TrackNames.searchRank("  ", "C418 - Sweden", "minecraft:music/game/sweden"));
    }

    @Test void searchAcceptsTyposAndAccentsButRejectsUnrelatedAndShortFuzzyQueries() {
        assertEquals(101, TrackNames.searchRank("swedn", "C418 - Sweden", "minecraft:music/game/sweden"));
        assertEquals(101, TrackNames.searchRank("lena rane", "Lena Raine - Pigstep", "minecraft:records/pigstep"));
        assertEquals(0, TrackNames.searchRank("ÉCHO", "Artist - Echo", "other:echo"));
        assertEquals(Integer.MAX_VALUE, TrackNames.searchRank("zzzzzz", "C418 - Sweden", "minecraft:music/game/sweden"));
        assertEquals(Integer.MAX_VALUE, TrackNames.searchRank("ct", "C418 - Cat", "minecraft:records/cat"));
    }
}
