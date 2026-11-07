package xyz.pyrehaven.whostoppedthemusic.music;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

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
    public java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getAudioStream(
            net.minecraft.client.sounds.SoundBufferLibrary library, Identifier id, boolean looping) {
        if (!preview && track.local() == null) return library.getStream(id, looping);
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                if (!preview) return track.local().open();
                var client = net.minecraft.client.Minecraft.getInstance();
                MidpointAudioStream.Input input = track.local() == null
                        ? () -> client.getResourceManager().getResourceOrThrow(id).open()
                        : () -> java.nio.file.Files.newInputStream(track.local().song().file());
                MidpointAudioStream.Decoder decoder = track.local() == null
                        ? () -> new net.minecraft.client.sounds.JOrbisAudioStream(input.open()) : track.local()::open;
                boolean ogg = track.local() == null || track.local().song().file().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg");
                return MidpointAudioStream.open(input, decoder, ogg);
            }
            catch (java.io.IOException | RuntimeException error) {
                if (track.local() != null) track.local().failed(error);
                var client = net.minecraft.client.Minecraft.getInstance();
                client.execute(() -> MusicDirector.get(client).failed(this));
                throw new java.util.concurrent.CompletionException(error);
            }
        }, net.minecraft.util.Util.nonCriticalIoPool());
    }

    @Override
    public WeighedSoundEvents getOrResolve(SoundManager manager) {
        sound = track.sound();
        if (preview) sound = new net.minecraft.client.resources.sounds.Sound(sound.getLocation(), sound.getVolume(),
                net.minecraft.util.valueproviders.ConstantFloat.of(1), sound.getWeight(), sound.getType(), true, false, sound.getAttenuationDistance());
        soundEvent = new WeighedSoundEvents(identifier, null);
        soundEvent.addSound(sound);
        return soundEvent;
    }
}
