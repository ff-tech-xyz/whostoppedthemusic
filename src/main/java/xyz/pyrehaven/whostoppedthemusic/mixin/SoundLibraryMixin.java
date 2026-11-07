package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

@Mixin(SoundManager.class)
public abstract class SoundLibraryMixin {
    @Inject(method = "apply(Lnet/minecraft/client/sounds/SoundManager$Preparations;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("RETURN"))
    private void whostoppedthemusic$reload(CallbackInfo ci) {
        WhoStoppedTheMusic.LIBRARY.rebuild((SoundManager) (Object) this);
        MusicDirector.get(Minecraft.getInstance()).libraryChanged();
        WhoStoppedTheMusic.LIBRARY.refreshSongs();
    }
}
