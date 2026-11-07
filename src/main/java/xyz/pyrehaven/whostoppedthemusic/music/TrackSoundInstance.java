package xyz.pyrehaven.whostoppedthemusic.music;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantFloat;

public final class TrackSoundInstance extends SimpleSoundInstance {
    private final Track track;
    private final boolean preview;

    public TrackSoundInstance(Identifier pool, Track track) {
        this(pool, track, false);
    }

    public TrackSoundInstance(Identifier pool, Track track, boolean preview) {
        super(pool, SoundSource.MUSIC, 1, 1, SoundInstance.createUnseededRandom(), false, 0, Attenuation.NONE, 0, 0, 0, true);
        this.track = track;
        this.preview = preview;
        relative = true;
        attenuation = Attenuation.NONE;
    }

    public Track track() { return track; }
    public boolean preview() { return preview; }

    @Override public boolean canPlaySound() { return track.playable(); }
    @Override public boolean canStartSilent() { return preview; }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(
            SoundBufferLibrary library, Identifier id, boolean looping) {
        if (!preview && track.local() == null) return library.getStream(id, looping);
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!preview) return track.local().open();
                var client = Minecraft.getInstance();
                MidpointAudioStream.Input input = track.local() == null
                        ? () -> client.getResourceManager().getResourceOrThrow(id).open()
                        : () -> Files.newInputStream(track.local().song().file());
                MidpointAudioStream.Decoder decoder = track.local() == null
                        ? () -> new JOrbisAudioStream(input.open()) : track.local()::open;
                boolean ogg = track.local() == null || track.local().song().file().toString().toLowerCase(Locale.ROOT).endsWith(".ogg");
                return MidpointAudioStream.open(input, decoder, ogg);
            }
            catch (IOException | RuntimeException error) {
                if (track.local() != null) track.local().failed(error);
                var client = Minecraft.getInstance();
                client.execute(() -> MusicDirector.get(client).failed(this));
                throw new CompletionException(error);
            }
        }, Util.nonCriticalIoPool());
    }

    @Override
    public WeighedSoundEvents getOrResolve(SoundManager manager) {
        sound = track.sound();
        if (preview) sound = new Sound(sound.getLocation(), sound.getVolume(),
                ConstantFloat.of(1), sound.getWeight(), sound.getType(), true, false, sound.getAttenuationDistance());
        soundEvent = new WeighedSoundEvents(identifier, null);
        soundEvent.addSound(sound);
        return soundEvent;
    }
}
