package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

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

    /**
     * The music panel stores exact vanilla resource paths such as music/game/sweden,
     * not weighted sound-event pools such as music.game. Resolve directly to the file
     * so the player gets the song they selected.
     */
    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(
                this.identifier,
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                1,
                Sound.Type.FILE,
                true,
                false,
                16
        );
        WeighedSoundEvents event = new WeighedSoundEvents(this.identifier, null);
        event.addSound(this.sound);
        return event;
    }
}
