package xyz.pyrehaven.whostoppedthemusic;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.pyrehaven.whostoppedthemusic.custom.MusicFolder;
import xyz.pyrehaven.whostoppedthemusic.music.LocalSong;
import xyz.pyrehaven.whostoppedthemusic.music.Mp3AudioStream;

final class LocalAudioTest {
    @TempDir Path directory;

    @Test void generatedMp3AndOggDecodeToFullNonSilentPcmAndStableEof() throws Exception {
        for (String name : new String[]{"stereo.mp3", "mono.ogg"}) {
            Path file = directory.resolve(name);
            try (var input = getClass().getResourceAsStream("/audio/" + name)) { Files.copy(input, file); }
            var song = new LocalSong(new MusicFolder.Song(file));
            song.probe();
            assertTrue(song.playable());
            try (var stream = song.open()) {
                var format = stream.getFormat();
                assertEquals(name.endsWith("mp3") ? 2 : 1, format.getChannels());
                assertEquals(name.endsWith("mp3") ? 44100 : 48000, format.getSampleRate());
                long bytes = 0;
                boolean nonzero = false;
                int crossings = 0;
                float previous = 0;
                for (int i = 0; i < 1000; i++) {
                    var buffer = stream.read(4096).order(ByteOrder.nativeOrder());
                    assertTrue(buffer.isDirect());
                    if (!buffer.hasRemaining()) break;
                    bytes += buffer.remaining();
                    while (buffer.hasRemaining()) {
                        float sample = format.getSampleSizeInBits() == 16 ? buffer.getShort() : buffer.getFloat();
                        nonzero |= sample != 0;
                        if (previous < 0 && sample >= 0) crossings++;
                        previous = sample;
                    }
                }
                double duration = bytes / (format.getFrameRate() * format.getFrameSize());
                assertTrue(duration >= 2.9 && duration <= 3.2, "full generated signal duration: " + duration);
                assertTrue(nonzero);
                double frequency = crossings / duration;
                assertEquals(name.endsWith("mp3") ? 440 : 660, frequency, 20, "decoded tone frequency and PCM byte order");
                assertFalse(stream.read(4096).hasRemaining());
                assertFalse(stream.read(4096).hasRemaining());
            }
        }
    }

    @Test void emptyTextAndInvalidOggAreUnavailableWithoutThrowingFromProbe() throws Exception {
        for (String name : new String[]{"bad.mp3", "bad.ogg", "empty.mp3"}) {
            Path file = directory.resolve(name);
            Files.writeString(file, name.startsWith("empty") ? "" : "not audio");
            var song = new LocalSong(new MusicFolder.Song(file));
            assertDoesNotThrow(song::probe);
            assertFalse(song.playable());
        }
        assertThrows(IOException.class, () -> new Mp3AudioStream(new ByteArrayInputStream(new byte[0])));
    }

    @Test void midpointMatchesDecodedHalfAndFadesToZeroForBothLocalFormats() throws Exception {
        for (String name : new String[]{"stereo.mp3", "mono.ogg"}) {
            Path file = directory.resolve(name);
            try (var input = getClass().getResourceAsStream("/audio/" + name)) { Files.copy(input, file); }
            var song = new LocalSong(new MusicFolder.Song(file));
            var original = new java.io.ByteArrayOutputStream();
            int frameSize;
            float rate;
            try (var stream = song.open()) {
                frameSize = stream.getFormat().getFrameSize();
                rate = stream.getFormat().getFrameRate();
                for (var buffer = stream.read(8192); buffer.hasRemaining(); buffer = stream.read(8192)) {
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    original.write(bytes);
                }
            }
            byte[] full = original.toByteArray();
            int half = (full.length / frameSize / 2) * frameSize;
            try (var preview = xyz.pyrehaven.whostoppedthemusic.music.MidpointAudioStream.open(
                    () -> Files.newInputStream(file), song::open, name.endsWith("ogg"))) {
                var excerpt = new java.io.ByteArrayOutputStream();
                for (var buffer = preview.read(4096); buffer.hasRemaining(); buffer = preview.read(4096)) {
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    excerpt.write(bytes);
                }
                byte[] audio = excerpt.toByteArray();
                assertEquals(Math.min(full.length - half, (int) rate * 3 * frameSize), audio.length, name);
                assertArrayEquals(java.util.Arrays.copyOfRange(full, half, half + 4096), java.util.Arrays.copyOf(audio, 4096), name);
                for (int i = audio.length - frameSize; i < audio.length; i++) assertEquals(0, audio[i], "fade ends at zero");
                assertFalse(preview.read(4096).hasRemaining());
            }
        }
    }
}
