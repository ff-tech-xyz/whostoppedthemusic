package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

public final class EventMusicSoundInstance extends AbstractSoundInstance {
    public EventMusicSoundInstance(Identifier id) {
        super(id, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }
}
