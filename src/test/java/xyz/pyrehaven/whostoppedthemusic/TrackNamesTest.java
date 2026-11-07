package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import xyz.pyrehaven.whostoppedthemusic.music.TrackNames;

final class TrackNamesTest {
    @Test void metadataPreservesHyphensAndUnknownArtists() {
        assertEquals(new TrackNames.Metadata("Sweden", "C418"), TrackNames.metadata("C418 - Sweden"));
        assertEquals(new TrackNames.Metadata("Night-Song - Reprise", "Artist-Name"),
                TrackNames.metadata("Artist-Name - Night-Song - Reprise"));
        assertEquals(new TrackNames.Metadata("Night-Song", ""), TrackNames.metadata("Night-Song"));
        assertEquals(new TrackNames.Metadata("Forest Theme", ""), TrackNames.metadata("Forest Theme"));
        assertEquals(60, TrackNames.searchRank("other forest", TrackNames.metadata("Night-Song"), "other:music/forest_theme"));
    }
    @Test void soundtrackAndDiscKeysUseVanillaTranslations() {
        assertEquals("music.game.sweden", TrackNames.key("minecraft:music/game/sweden", null));
        assertEquals("jukebox_song.minecraft.pigstep", TrackNames.key("minecraft:records/pigstep", "minecraft:music_disc.pigstep"));
    }

    @Test void missingAndModdedNamesFallBackToReadableFileName() {
        assertEquals("other.music.game.night_song", TrackNames.key("other:music/game/night_song", null));
        assertEquals("Night Song", TrackNames.prettify("other:music/game/night_song"));
        assertEquals("Aerie", TrackNames.prettify("minecraft:music/game/swamp/aerie"));
    }

    @Test void searchRanksSongBeforePrefixArtistAndLooseMatches() {
        int exact = TrackNames.searchRank("cat", TrackNames.metadata("C418 - Cat"), "minecraft:records/cat");
        int prefix = TrackNames.searchRank("cat", TrackNames.metadata("Artist - Catwalk"), "other:catwalk");
        int artist = TrackNames.searchRank("cat", TrackNames.metadata("Cat - Elsewhere"), "other:elsewhere");
        int loose = TrackNames.searchRank("cat", TrackNames.metadata("Artist - Scattered"), "other:scattered");
        assertTrue(exact < prefix && prefix < artist && artist < loose);
        assertEquals(30, TrackNames.searchRank("  LENA RAINE  ", TrackNames.metadata("Lena Raine - Pigstep"), "minecraft:records/pigstep"));
        assertEquals(60, TrackNames.searchRank("pig raine", TrackNames.metadata("Lena Raine - Pigstep"), "minecraft:records/pigstep"));
        assertEquals(0, TrackNames.searchRank("  ", TrackNames.metadata("C418 - Sweden"), "minecraft:music/game/sweden"));
    }

    @Test void searchAcceptsTyposAndAccentsButRejectsUnrelatedAndShortFuzzyQueries() {
        assertEquals(101, TrackNames.searchRank("swedn", TrackNames.metadata("C418 - Sweden"), "minecraft:music/game/sweden"));
        assertEquals(101, TrackNames.searchRank("lena rane", TrackNames.metadata("Lena Raine - Pigstep"), "minecraft:records/pigstep"));
        assertEquals(0, TrackNames.searchRank("ÉCHO", TrackNames.metadata("Artist - Echo"), "other:echo"));
        assertEquals(Integer.MAX_VALUE, TrackNames.searchRank("zzzzzz", TrackNames.metadata("C418 - Sweden"), "minecraft:music/game/sweden"));
        assertEquals(Integer.MAX_VALUE, TrackNames.searchRank("ct", TrackNames.metadata("C418 - Cat"), "minecraft:records/cat"));
    }
}
