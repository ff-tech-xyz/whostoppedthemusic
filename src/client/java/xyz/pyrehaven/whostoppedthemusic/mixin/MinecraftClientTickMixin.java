package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicController;

@Mixin(Minecraft.class)
public class MinecraftClientTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void whostm_tickMusicController(CallbackInfo ci) {
        WhostmMusicController.tick((Minecraft) (Object) this);
    }
}
