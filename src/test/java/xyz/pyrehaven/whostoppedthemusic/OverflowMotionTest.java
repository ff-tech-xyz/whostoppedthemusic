package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import xyz.pyrehaven.whostoppedthemusic.music.OverflowMotion;

final class OverflowMotionTest {
    @Test void waitsOneSecondAtEachEndAndReversesAtTwentyPixelsPerSecond() {
        assertEquals(0, OverflowMotion.offset(40, 999));
        assertEquals(0, OverflowMotion.offset(40, 1000));
        assertEquals(10, OverflowMotion.offset(40, 1500));
        assertEquals(40, OverflowMotion.offset(40, 3000));
        assertEquals(40, OverflowMotion.offset(40, 3999));
        assertEquals(30, OverflowMotion.offset(40, 4500));
        assertEquals(0, OverflowMotion.offset(40, 6000));
        assertEquals(0, OverflowMotion.offset(40, 6999));
        assertEquals(0.02, OverflowMotion.offset(40, 7001), 0.000001);
        assertEquals(39.98, OverflowMotion.offset(40, 4001), 0.000001);
    }

    @Test void fittingLinesStayStillAndOnlyTheirOwnContentResetsTheClock() {
        var title = new OverflowMotion();
        var artist = new OverflowMotion();
        assertEquals(0, title.offset("title", 100, 100, true));
        assertEquals(0, artist.offset("artist", 50, 100, true));
        assertEquals(20, title.offset("title", 100, 2100, true));
        assertEquals(20, artist.offset("artist", 50, 2100, true));
        assertEquals(0, title.offset("new title", 100, 2100, true));
        assertEquals(30, artist.offset("artist", 50, 2600, true));
        assertEquals(0, title.offset("new title", -1, 9000, true));
        assertEquals(0, OverflowMotion.offset(0, Long.MAX_VALUE));
        assertEquals(0, OverflowMotion.offset(-10, 4500));
    }

    @Test void onlyHoverStartsMotionAndLeavingOrFittingRestartsTheWait() {
        var line = new OverflowMotion();
        assertEquals(0, line.offset("title", 100, 0, false));
        assertEquals(0, line.offset("title", 100, 8000, false));
        assertEquals(0, line.offset("title", 100, 8000, true));
        assertEquals(0, line.offset("title", 100, 8999, true));
        assertEquals(20, line.offset("title", 100, 10000, true));
        assertEquals(0, line.offset("title", 100, 10001, false));
        assertEquals(0, line.offset("title", 100, 20000, true));
        assertEquals(0, line.offset("title", 100, 20999, true));
        assertEquals(20, line.offset("title", 100, 22000, true));
        assertEquals(0, line.offset("title", -1, 22001, true));
        assertEquals(0, line.offset("title", 100, 25000, true));
    }
}
