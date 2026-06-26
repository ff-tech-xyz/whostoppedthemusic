package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.client.gui.MusicSelectionScreen;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void whostm_addMusicPanelButton(CallbackInfo ci) {
        PauseScreen self = (PauseScreen) (Object) this;
        if (!self.showsPauseMenu()) {
            return;
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.whostoppedthemusic.music"),
                        button -> this.minecraft.setScreenAndShow(new MusicSelectionScreen((Screen) (Object) this)))
                .bounds(this.width / 2 - 102, Math.max(8, this.height - 54), 204, 20)
                .build());
    }
}
