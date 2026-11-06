package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "resume", at = @At("RETURN"))
    private void whostoppedthemusic$keepMusicPaused(CallbackInfo ci) {
        MusicDirector.get(Minecraft.getInstance()).reapplyPause();
    }
}
