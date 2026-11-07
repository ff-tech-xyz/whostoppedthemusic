package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.pyrehaven.whostoppedthemusic.music.TrackSoundInstance;

/** Preview ignores background environment/jukebox fades while retaining category and Master mute. */
@Mixin(SoundEngine.class)
public abstract class PreviewVolumeMixin {
    @Shadow @Final private Options options;

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void whostoppedthemusic$previewGain(SoundInstance sound, CallbackInfoReturnable<Float> ci) {
        if (sound instanceof TrackSoundInstance track && track.preview()) {
            ci.setReturnValue(net.minecraft.util.Mth.clamp(sound.getVolume(), 0, 1)
                    * net.minecraft.util.Mth.clamp(options.getFinalSoundSourceVolume(sound.getSource()), 0, 1));
        }
    }
}
