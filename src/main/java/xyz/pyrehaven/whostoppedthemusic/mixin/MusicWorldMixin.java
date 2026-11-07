package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

@Mixin(Minecraft.class)
public abstract class MusicWorldMixin {
    @Redirect(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;stop()V"))
    private void whostoppedthemusic$worldSounds(SoundManager sounds) {
        var director = MusicDirector.get((Minecraft) (Object) this);
        if (director.basic() && director.active()) {
            var engine = ((SoundManagerAccessor) sounds).whostoppedthemusic$engine();
            ((MusicDirector.LevelSoundControl) engine).whostoppedthemusic$stopLevelSounds(director.current());
        } else sounds.stop();
    }
}
