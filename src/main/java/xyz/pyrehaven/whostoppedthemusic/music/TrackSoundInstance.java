package xyz.pyrehaven.whostoppedthemusic.music;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

public final class TrackSoundInstance extends SimpleSoundInstance {
    private final Track track;

    public TrackSoundInstance(Identifier pool, Track track) {
        super(pool, SoundSource.MUSIC, 1, 1, SoundInstance.createUnseededRandom(), false, 0, Attenuation.NONE, 0, 0, 0, true);
        this.track = track;
        relative = true;
        attenuation = Attenuation.NONE;
    }

    public Track track() { return track; }

    @Override public boolean canPlaySound() { return track.playable(); }

    @Override
    public java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getAudioStream(
            net.minecraft.client.sounds.SoundBufferLibrary library, Identifier id, boolean looping) {
        if (track.local() == null) return library.getStream(id, looping);
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return track.local().open(); }
            catch (java.io.IOException | RuntimeException error) {
                track.local().failed(error);
                var client = net.minecraft.client.Minecraft.getInstance();
                client.execute(() -> MusicDirector.get(client).failed(this));
                throw new java.util.concurrent.CompletionException(error);
            }
        }, net.minecraft.util.Util.nonCriticalIoPool());
    }

    @Override
    public WeighedSoundEvents getOrResolve(SoundManager manager) {
        sound = track.sound();
        soundEvent = new WeighedSoundEvents(identifier, null);
        soundEvent.addSound(sound);
        return soundEvent;
    }
}
