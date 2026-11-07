package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

/** Vanilla's previously visible toast must also retire when the mode changes. */
@Mixin(NowPlayingToast.class)
public abstract class NowPlayingToastMixin {
    @Inject(method = "getWantedVisibility", at = @At("HEAD"), cancellable = true)
    private void whostoppedthemusic$visibility(CallbackInfoReturnable<Toast.Visibility> ci) {
        if (MusicDirector.get(Minecraft.getInstance()).basic()) ci.setReturnValue(Toast.Visibility.HIDE);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void whostoppedthemusic$replacement(CallbackInfo ci) {
        if (MusicDirector.get(Minecraft.getInstance()).basic()) ci.cancel();
    }
}
