package xyz.pyrehaven.whostoppedthemusic.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

/** Transport intent lives on the source, including while vanilla is loading its stream. */
@Mixin(Channel.class)
public abstract class MusicChannelMixin implements MusicDirector.ChannelControl {
    @Unique private boolean whostoppedthemusic$managed;
    @Unique private boolean whostoppedthemusic$paused;
    @Unique private boolean whostoppedthemusic$stopped;

    @Override
    public void whostoppedthemusic$manage() {
        whostoppedthemusic$managed = true;
    }

    @Override
    public void whostoppedthemusic$pause(boolean paused) {
        whostoppedthemusic$paused = paused;
        var channel = (Channel) (Object) this;
        if (paused) {
            channel.pause();
        } else {
            channel.unpause();
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void whostoppedthemusic$stop(CallbackInfo ci) {
        if (whostoppedthemusic$managed) {
            whostoppedthemusic$stopped = true;
        }
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void whostoppedthemusic$preventLateStart(CallbackInfo ci) {
        if (whostoppedthemusic$stopped) {
            // OpenAL stop on an INITIAL source is a no-op. Once the late stream
            // is attached, dispose it without ever issuing alSourcePlay.
            ((Channel) (Object) this).destroy();
            ci.cancel();
        }
    }

    @Inject(method = "play", at = @At("RETURN"))
    private void whostoppedthemusic$holdStartup(CallbackInfo ci) {
        if (whostoppedthemusic$paused) {
            ((Channel) (Object) this).pause();
        }
    }

    @Inject(method = "unpause", at = @At("HEAD"), cancellable = true)
    private void whostoppedthemusic$holdMenuResume(CallbackInfo ci) {
        if (whostoppedthemusic$paused || whostoppedthemusic$stopped) {
            ci.cancel();
        }
    }
}
