package xyz.pyrehaven.whostoppedthemusic.test.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fail at the native owner rather than letting a later Stop consume stale errors. */
@Mixin(Channel.class)
public abstract class NativeBoundary {
    @Shadow private int source;

    @Inject(method = {"play", "pause", "unpause", "stop", "destroy", "attachBufferStream", "updateStream", "setVolume", "setPitch", "setSelfPosition", "setLooping", "setRelative", "linearAttenuation", "disableAttenuation", "attachStaticBuffer"}, at = @At("HEAD"))
    private void before(CallbackInfo ci) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) throw new AssertionError("native error before source=" + source + " error=" + error);
    }

    @Inject(method = {"play", "pause", "unpause", "stop", "destroy", "attachBufferStream", "updateStream", "setVolume", "setPitch", "setSelfPosition", "setLooping", "setRelative", "linearAttenuation", "disableAttenuation", "attachStaticBuffer"}, at = @At("RETURN"))
    private void after(CallbackInfo ci) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) throw new AssertionError("native error after source=" + source + " error=" + error);
    }
}
