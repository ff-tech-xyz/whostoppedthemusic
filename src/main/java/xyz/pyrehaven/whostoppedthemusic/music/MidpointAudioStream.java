package xyz.pyrehaven.whostoppedthemusic.music;

import com.jcraft.jogg.Page;
import com.jcraft.jogg.SyncState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;

/** Bounded PCM excerpt: exact half-sample start, at most three seconds, final 150 ms fade. */
public final class MidpointAudioStream implements AudioStream {
    @FunctionalInterface public interface Input { InputStream open() throws IOException; }
    @FunctionalInterface public interface Decoder { AudioStream open() throws IOException; }

    private final AudioStream decoded;
    private final long startFrame;
    private final long totalFrames;
    private final long excerptFrames;
    private long position;
    private ByteBuffer pending = ByteBuffer.allocate(0);

    public static MidpointAudioStream open(Input input, Decoder decoder, boolean ogg) throws IOException {
        long frames;
        if (ogg) {
            frames = oggFrames(input);
        } else {
            // MP3 (including VBR) has no dependable container duration. Count decoded frames,
            // then reopen; neither pass buffers the song in memory.
            try (var stream = decoder.open()) {
                frames = 0;
                for (var buffer = stream.read(65536); buffer.hasRemaining(); buffer = stream.read(65536))
                    frames += buffer.remaining() / stream.getFormat().getFrameSize();
            }
        }
        if (frames <= 0) throw new IOException("Song contains no audio samples");
        var stream = decoder.open();
        try { return new MidpointAudioStream(stream, frames); }
        catch (IOException | RuntimeException error) {
            try { stream.close(); } catch (IOException closing) { error.addSuppressed(closing); }
            throw error;
        }
    }

    private static long oggFrames(Input input) throws IOException {
        var sync = new SyncState();
        sync.init();
        var page = new Page();
        long frames = -1;
        try (var stream = input.open()) {
            while (true) {
                int result = sync.pageout(page);
                if (result == 1) {
                    if (page.granulepos() >= 0) frames = page.granulepos();
                } else if (result < 0) {
                    throw new IOException("Invalid Ogg page");
                } else {
                    int offset = sync.buffer(8192);
                    int count = stream.read(sync.data, offset, 8192);
                    if (count < 0) break;
                    sync.wrote(count);
                }
            }
        } finally { sync.clear(); }
        return frames;
    }

    private MidpointAudioStream(AudioStream decoded, long frames) throws IOException {
        this.decoded = decoded;
        totalFrames = frames;
        startFrame = frames / 2;
        excerptFrames = Math.min(frames - startFrame, (long) (decoded.getFormat().getSampleRate() * 3));
        long skip = startFrame * decoded.getFormat().getFrameSize();
        while (skip > 0) {
            if (!fill()) throw new IOException("Song ended before its midpoint");
            int bytes = (int) Math.min(skip, pending.remaining());
            pending.position(pending.position() + bytes);
            skip -= bytes;
        }
    }

    private boolean fill() throws IOException {
        if (!pending.hasRemaining()) pending = decoded.read(65536).order(ByteOrder.nativeOrder());
        return pending.hasRemaining();
    }

    @Override public AudioFormat getFormat() { return decoded.getFormat(); }

    @Override public ByteBuffer read(int bytes) throws IOException {
        int frameSize = getFormat().getFrameSize();
        int size = (int) Math.min(bytes / frameSize, excerptFrames - position) * frameSize;
        var output = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        long fadeFrames = Math.min(excerptFrames, (long) (getFormat().getSampleRate() * 0.15));
        while (output.hasRemaining() && fill()) {
            float gain = Math.min(1, (float) (excerptFrames - position - 1) / Math.max(1, fadeFrames));
            for (int channel = 0; channel < getFormat().getChannels(); channel++)
                output.putShort((short) (pending.getShort() * gain));
            position++;
        }
        return output.flip();
    }

    @Override public void close() throws IOException { decoded.close(); }
}
