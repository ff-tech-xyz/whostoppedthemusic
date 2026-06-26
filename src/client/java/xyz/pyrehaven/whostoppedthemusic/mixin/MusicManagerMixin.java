package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicController;

@Mixin(MusicManager.class)
public class MusicManagerMixin {

    @Shadow
    private int nextSongDelay;

    @Shadow
    private SoundInstance currentMusic;

    @Shadow
    private Minecraft minecraft;

    /**
     * Zero out the inter-track delay so music resumes immediately
     * instead of waiting vanilla's random gap (up to ~2.75 min).
     * Only fires when no music is currently playing.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void whostm_zeroDelay(CallbackInfo ci) {
        if (WhostmMusicController.shouldSuppressVanillaMusic(this.minecraft)) {
            if (this.currentMusic != null) {
                this.minecraft.getSoundManager().stop(this.currentMusic);
                this.currentMusic = null;
            }
            this.nextSongDelay = 20;
            ci.cancel();
            return;
        }

        if (this.currentMusic == null || !this.minecraft.getSoundManager().isActive(this.currentMusic)) {
            if (this.nextSongDelay > 0) {
                this.nextSongDelay = 0;
            }
        }
    }

    /**
     * Prevent stop() from cutting songs short.
     * We cancel the no-arg stop() only if music is actively playing.
     * Typed stop(MusicSound) (for music type changes) is NOT cancelled —
     * that lets vanilla switch tracks correctly (e.g. entering a biome).
     */
    @Inject(method = "stopPlaying()V", at = @At("HEAD"), cancellable = true)
    private void whostm_preventStop(CallbackInfo ci) {
        if (this.currentMusic != null && this.minecraft.getSoundManager().isActive(this.currentMusic)) {
            ci.cancel();
        }
    }
}
