package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicController;
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicHud;
import xyz.pyrehaven.whostoppedthemusic.client.gui.CassetteButton;
import xyz.pyrehaven.whostoppedthemusic.client.gui.MusicSelectionScreen;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void whostm_addMusicCassette(CallbackInfo ci) {
        PauseScreen self = (PauseScreen) (Object) this;
        if (!self.showsPauseMenu()) {
            return;
        }

        int cassetteWidth = Math.min(360, Math.max(220, this.width - 112));
        int cassetteX = this.width / 2 - cassetteWidth / 2;
        int cassetteY = 8;
        int cassetteHeight = 44;
        int arrowY = cassetteY + cassetteHeight / 2 - 10;

        Button previous = Button.builder(Component.translatable("button.whostoppedthemusic.previous"), button -> WhostmMusicController.skipPrevious(this.minecraft))
                .bounds(Math.max(8, cassetteX - 32), arrowY, 28, 20)
                .build();
        Button next = Button.builder(Component.translatable("button.whostoppedthemusic.next"), button -> WhostmMusicController.skipNext(this.minecraft))
                .bounds(Math.min(this.width - 36, cassetteX + cassetteWidth + 4), arrowY, 28, 20)
                .build();
        boolean canSkip = WhostmMusicController.controlsEnabled(this.minecraft) && WhostmMusicController.hasEnabledTracks(this.minecraft);
        previous.active = canSkip;
        next.active = canSkip;

        this.addRenderableWidget(previous);
        this.addRenderableWidget(new CassetteButton(
                cassetteX,
                cassetteY,
                cassetteWidth,
                cassetteHeight,
                Component.translatable("text.whostoppedthemusic.cassette_title"),
                WhostmMusicHud::nowPlayingMessage,
                () -> this.minecraft.setScreenAndShow(new MusicSelectionScreen((Screen) (Object) this))
        ));
        this.addRenderableWidget(next);
    }
}
