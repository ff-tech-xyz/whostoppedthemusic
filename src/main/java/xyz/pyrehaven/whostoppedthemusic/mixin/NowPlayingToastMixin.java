package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

/** Keep vanilla's left-side notification, layout, timing and options; supply resolved metadata. */
@Mixin(NowPlayingToast.class)
public abstract class NowPlayingToastMixin {
    @Inject(method = "getNowPlayingString", at = @At("HEAD"), cancellable = true)
    private static void whostoppedthemusic$metadata(String song, CallbackInfoReturnable<Component> ci) {
        if (song == null) return;
        var metadata = MusicDirector.get(Minecraft.getInstance()).metadata();
        ci.setReturnValue(metadata.artist().getString().isEmpty() ? metadata.title()
                : metadata.artist().copy().append(" - ").append(metadata.title()));
    }
}
