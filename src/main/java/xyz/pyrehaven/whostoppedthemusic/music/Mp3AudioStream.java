package xyz.pyrehaven.whostoppedthemusic.music;

import dev.mccue.jlayer.decoder.Bitstream;
import dev.mccue.jlayer.decoder.Decoder;
import dev.mccue.jlayer.decoder.JavaLayerException;
import dev.mccue.jlayer.decoder.SampleBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;

/** Incremental interleaved signed 16-bit PCM; the vanilla channel owns stream lifetime. */
public final class Mp3AudioStream implements AudioStream {
    private final Bitstream bits;
    private final Decoder decoder = new Decoder();
    private final AudioFormat format;
    private ByteBuffer pending = ByteBuffer.allocate(0);
    private boolean ended;

    public Mp3AudioStream(InputStream input) throws IOException {
        bits = new Bitstream(input);
        var samples = frame();
        if (samples == null) throw new IOException("MP3 contains no audio frames");
        format = new AudioFormat(samples.getSampleFrequency(), 16, samples.getChannelCount(), true,
                ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
        buffer(samples);
    }

    private SampleBuffer frame() throws IOException {
        try {
            var header = bits.readFrame();
            if (header == null) return null;
            try {
                return (SampleBuffer) decoder.decodeFrame(header, bits);
            } finally {
                bits.closeFrame();
            }
        } catch (JavaLayerException | RuntimeException e) {
            throw new IOException("Invalid MP3 frame", e);
        }
    }

    private void buffer(SampleBuffer samples) throws IOException {
        if (samples.getSampleFrequency() != (int) format.getSampleRate()
                || samples.getChannelCount() != format.getChannels()) throw new IOException("MP3 format changed mid-stream");
        pending = ByteBuffer.allocate(samples.getBufferLength() * Short.BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < samples.getBufferLength(); i++) pending.putShort(samples.getBuffer()[i]);
        pending.flip();
    }

    @Override public AudioFormat getFormat() { return format; }

    @Override public ByteBuffer read(int bytes) throws IOException {
        int size = bytes - bytes % format.getFrameSize();
        var output = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        while (output.hasRemaining()) {
            if (!pending.hasRemaining()) {
                if (ended) break;
                var samples = frame();
                if (samples == null) { ended = true; break; }
                buffer(samples);
            }
            int length = Math.min(output.remaining(), pending.remaining());
            output.put(pending.slice(pending.position(), length));
            pending.position(pending.position() + length);
        }
        return output.flip();
    }

    @Override public void close() throws IOException {
        ended = true;
        pending = ByteBuffer.allocate(0);
        try { bits.close(); }
        catch (JavaLayerException e) { throw new IOException("Closing MP3", e); }
    }
}
