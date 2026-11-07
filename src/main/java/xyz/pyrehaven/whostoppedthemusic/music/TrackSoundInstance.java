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

    @Override
    public WeighedSoundEvents getOrResolve(SoundManager manager) {
        sound = track.sound();
        soundEvent = new WeighedSoundEvents(identifier, null);
        soundEvent.addSound(sound);
        return soundEvent;
    }
}
