package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin implements MusicDirector.Access {
    @Shadow @Final private Minecraft minecraft;
    @Unique private MusicDirector whostoppedthemusic$director;

    @Override
    public MusicDirector whostoppedthemusic$director() {
        if (whostoppedthemusic$director == null) {
            whostoppedthemusic$director = new MusicDirector(minecraft, (MusicManager) (Object) this);
        }
        return whostoppedthemusic$director;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void whostoppedthemusic$holdPause(CallbackInfo ci) {
        if (whostoppedthemusic$director().holdTick()) {
            ci.cancel();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSituationalMusic()Lnet/minecraft/sounds/Music;"))
    private Music whostoppedthemusic$pool(Minecraft client) {
        return whostoppedthemusic$director().music(client.getSituationalMusic());
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getMusicVolume()F"))
    private float whostoppedthemusic$gain(Minecraft client) {
        return whostoppedthemusic$director().basic() ? 1 : client.getMusicVolume();
    }

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void whostoppedthemusic$empty(Music music, CallbackInfo ci) {
        if (!whostoppedthemusic$director().mayStart()) ci.cancel();
    }

    @Redirect(method = "startPlaying", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;forMusic(Lnet/minecraft/sounds/SoundEvent;)Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;"))
    private net.minecraft.client.resources.sounds.SimpleSoundInstance whostoppedthemusic$track(net.minecraft.sounds.SoundEvent event) {
        return whostoppedthemusic$director().choose(event);
    }

    @Redirect(method = {"startPlaying", "showNowPlayingToastIfNeeded"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;showNowPlayingToast()V"))
    private void whostoppedthemusic$toast(net.minecraft.client.gui.components.toasts.ToastManager toasts) {
        whostoppedthemusic$director().showToast(toasts);
    }

    @Inject(method = "startPlaying", at = @At("RETURN"))
    private void whostoppedthemusic$observe(Music music, CallbackInfo ci) {
        whostoppedthemusic$director().started(music);
    }

    @Inject(method = "stopPlaying()V", at = @At("HEAD"))
    private void whostoppedthemusic$stop(CallbackInfo ci) {
        whostoppedthemusic$director().stopped();
    }
}
