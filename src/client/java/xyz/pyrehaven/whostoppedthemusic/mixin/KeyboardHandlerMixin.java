package xyz.pyrehaven.whostoppedthemusic.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.client.gui.MusicSelectionScreen;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void whostm_openMusicPanel(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != InputConstants.PRESS || event.key() != InputConstants.KEY_M || !event.hasControlDown()) {
            return;
        }

        Screen current = this.minecraft.gui.screen();
        if (!(current instanceof MusicSelectionScreen)) {
            this.minecraft.setScreenAndShow(new MusicSelectionScreen(current));
        }
        ci.cancel();
    }
}
