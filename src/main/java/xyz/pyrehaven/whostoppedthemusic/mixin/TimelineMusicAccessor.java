package xyz.pyrehaven.whostoppedthemusic.mixin;

import java.util.Map;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.timeline.AttributeTrack;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Timeline.class)
public interface TimelineMusicAccessor {
    @Accessor("tracks")
    Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>> whostoppedthemusic$tracks();
}
