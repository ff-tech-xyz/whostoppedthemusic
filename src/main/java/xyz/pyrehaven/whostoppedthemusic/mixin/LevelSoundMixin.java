package xyz.pyrehaven.whostoppedthemusic.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

@Mixin(SoundEngine.class)
public abstract class LevelSoundMixin implements MusicDirector.LevelSoundControl {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow @Final private Map<SoundInstance, Integer> queuedSounds;
    @Shadow @Final private List<TickableSoundInstance> tickingSounds;
    @Shadow @Final private List<TickableSoundInstance> queuedTickableSounds;

    @Override
    public void whostoppedthemusic$stopLevelSounds(SoundInstance keep) {
        // Retain only the manager's non-positional Basic source across a world swap.
        // Normal engine cleanup releases the stopped handles and their bookkeeping.
        instanceToChannel.forEach((sound, handle) -> {
            if (sound != keep) handle.execute(channel -> {
                ((MusicDirector.ChannelControl) channel).whostoppedthemusic$manage();
                channel.stop();
            });
        });
        queuedSounds.clear();
        tickingSounds.clear();
        queuedTickableSounds.clear();
    }
}
