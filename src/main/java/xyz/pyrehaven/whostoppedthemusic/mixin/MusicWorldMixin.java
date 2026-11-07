package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.LevelSoundControl;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

@Mixin(Minecraft.class)
public abstract class MusicWorldMixin {
    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("RETURN"))
    private void whostoppedthemusic$library(ClientLevel level, boolean reset, CallbackInfo ci) {
        var client = (Minecraft) (Object) this;
        WhoStoppedTheMusic.LIBRARY.rebuild(client.getSoundManager(), level == null ? RegistryAccess.EMPTY : level.registryAccess());
        MusicDirector.get(client).worldChanged(level);
    }

    @Redirect(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;stop()V"))
    private void whostoppedthemusic$worldSounds(SoundManager sounds) {
        var director = MusicDirector.get((Minecraft) (Object) this);
        if (director.basic() && director.active()) {
            var engine = ((SoundManagerAccessor) sounds).whostoppedthemusic$engine();
            ((LevelSoundControl) engine).whostoppedthemusic$stopLevelSounds(director.current());
        } else sounds.stop();
    }
}
