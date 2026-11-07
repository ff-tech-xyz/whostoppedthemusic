package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;

final class DeckTest {
    @Test void orderedCyclesAndHistoryIncludeRowPlay() {
        var deck = new Deck(new Random(1));
        deck.configure(List.of(new Deck.Entry("A", 1), new Deck.Entry("B", 1)), false);
        assertEquals("A", deck.next());
        assertEquals("B", deck.next());
        assertEquals("A", deck.next());
        assertEquals("B", deck.previous());
        assertEquals("A", deck.next());
        deck.played("row");
        assertEquals("A", deck.previous());
        assertEquals("row", deck.next());
        assertEquals("B", deck.next());
    }

    @Test void weightedShuffleHasExactCountsAndAvoidsRefillRepeat() {
        var deck = new Deck(new Random(15));
        deck.configure(List.of(new Deck.Entry("A", 5), new Deck.Entry("B", 29)), true);
        String last = null;
        for (int cycle = 0; cycle < 10; cycle++) {
            int a = 0;
            String first = deck.next();
            if (last != null) assertNotEquals(last, first);
            if (first.equals("A")) a++;
            for (int i = 1; i < 34; i++) {
                last = deck.next();
                if (last.equals("A")) a++;
            }
            assertEquals(5, a);
        }
    }

    @Test void emptySingleAndChangedSelection() {
        var deck = new Deck(new Random(2));
        assertNull(deck.next());
        assertNull(deck.previous());
        deck.configure(List.of(new Deck.Entry("A", 1)), true);
        assertEquals("A", deck.next());
        assertEquals("A", deck.next());
        deck.configure(List.of(new Deck.Entry("B", 1)), true);
        assertFalse(deck.canPrevious());
        assertEquals("B", deck.next());
        assertThrows(IllegalArgumentException.class, () -> new Deck.Entry("bad", 0));
    }
}
