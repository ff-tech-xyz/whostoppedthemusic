package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MusicManager.class)
public interface MusicManagerAccessor {
    @Accessor("currentMusic")
    SoundInstance whostoppedthemusic$currentMusic();

    @Accessor("nextSongDelay")
    void whostoppedthemusic$nextSongDelay(int delay);

    @Invoker("fadePlaying")
    boolean whostoppedthemusic$fadePlaying(float gain);
}
