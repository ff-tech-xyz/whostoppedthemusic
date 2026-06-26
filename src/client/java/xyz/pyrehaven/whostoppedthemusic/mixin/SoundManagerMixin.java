package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicHud;

@Mixin(SoundManager.class)
public class SoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("RETURN"))
    private void whostm_announceMusic(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        SoundEngine.PlayResult result = cir.getReturnValue();
        if (result == SoundEngine.PlayResult.NOT_STARTED) {
            return;
        }
        if (instance.getSource() != SoundSource.MUSIC && instance.getSource() != SoundSource.RECORDS) {
            return;
        }
        WhostmMusicHud.announce(instance.getIdentifier(), instance.getSound());
    }
}
