package xyz.pyrehaven.whostoppedthemusic.mixin;

import java.util.Map;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's event-driven map is the sole owner of playing jukeboxes. */
@Mixin(LevelEventHandler.class)
public interface ClientJukeboxMixin {
    @Accessor("playingJukeboxSongs") Map<BlockPos, SoundInstance> whostoppedthemusic$jukeboxes();
}
