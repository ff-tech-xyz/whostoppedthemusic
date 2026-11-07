package xyz.pyrehaven.whostoppedthemusic.music;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEngineAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundManagerAccessor;

/** The sound engine owns the map; callers schedule native operations through its handles. */
public final class SoundChannels {
    private SoundChannels() { }

    public static Map<SoundInstance, ChannelAccess.ChannelHandle> all(Minecraft client) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        return ((SoundEngineAccessor) engine).whostoppedthemusic$channels();
    }

    public static ChannelAccess.ChannelHandle get(Minecraft client, SoundInstance source) {
        return all(client).get(source);
    }
}
