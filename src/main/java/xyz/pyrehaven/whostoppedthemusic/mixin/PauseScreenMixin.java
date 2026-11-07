package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    // The compact player owns the live title. Keep the vanilla toast option untouched.
    @Inject(method = "rendersNowPlayingToast", at = @At("HEAD"), cancellable = true)
    private void whostoppedthemusic$singleTitle(CallbackInfoReturnable<Boolean> cir) {
        if (((PauseScreen) (Object) this).showsPauseMenu()) {
            cir.setReturnValue(false);
        }
    }
}
