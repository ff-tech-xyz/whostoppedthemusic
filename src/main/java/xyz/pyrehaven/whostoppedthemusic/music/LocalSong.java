package xyz.pyrehaven.whostoppedthemusic.music;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.MusicFolder;

/** One scanned file and its decode availability, shared by library, playback and future previews. */
public final class LocalSong {
    private final MusicFolder.Song song;
    private volatile boolean playable = true;

    public LocalSong(MusicFolder.Song song) { this.song = song; }
    public MusicFolder.Song song() { return song; }
    public boolean playable() { return playable; }

    public void failed(Exception error) {
        if (playable) {
            playable = false;
            WhoStoppedTheMusic.LOGGER.warn("Can't play {}: {}", song.file().getFileName(), error.toString());
        }
    }

    public void probe() {
        try (var stream = open()) {
            if (!stream.read(4096).hasRemaining()) throw new IOException("File contains no audio samples");
        } catch (IOException | RuntimeException error) { failed(error); }
    }

    public AudioStream open() throws IOException {
        var input = Files.newInputStream(song.file());
        final AudioStream decoded;
        try {
            decoded = song.file().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")
                    ? new Mp3AudioStream(input) : new JOrbisAudioStream(input);
        } catch (IOException | RuntimeException error) {
            try { input.close(); } catch (IOException closing) { error.addSuppressed(closing); }
            throw error;
        }
        if (decoded.getFormat().getChannels() < 1 || decoded.getFormat().getChannels() > 2) {
            decoded.close();
            throw new IOException("Only mono and stereo audio are supported");
        }
        return new AudioStream() {
            @Override public AudioFormat getFormat() { return decoded.getFormat(); }
            @Override public ByteBuffer read(int bytes) {
                try { return decoded.read(bytes); }
                catch (IOException | RuntimeException error) {
                    failed(error);
                    return ByteBuffer.allocateDirect(0);
                }
            }
            @Override public void close() throws IOException { decoded.close(); }
        };
    }
}
