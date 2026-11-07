package xyz.pyrehaven.whostoppedthemusic.mixin;

import net.minecraft.client.resources.sounds.Sound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.sounds.SoundManager$Preparations$1")
public interface SoundEventReferenceAccessor {
    @Accessor("val$sound") Sound whostoppedthemusic$reference();
}
