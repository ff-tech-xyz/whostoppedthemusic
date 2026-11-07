package xyz.pyrehaven.whostoppedthemusic.music;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import xyz.pyrehaven.whostoppedthemusic.mixin.ClientJukeboxAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.ClientLevelAccessor;

public final class JukeboxWatcher {
    private JukeboxWatcher() { }

    public static boolean nearby(Minecraft client) {
        if (client.level == null) return false;
        var events = ((ClientLevelAccessor) client.level).whostoppedthemusic$events();
        var songs = ((ClientJukeboxAccessor) events).whostoppedthemusic$jukeboxes();
        if (songs.isEmpty()) return false;
        var channels = SoundChannels.all(client);
        for (var entry : songs.entrySet()) {
            var channel = channels.get(entry.getValue());
            if (channel != null && !channel.isStopped()
                    && client.gameRenderer.mainCamera().position().distanceToSqr(Vec3.atCenterOf(entry.getKey())) <= 64 * 64) return true;
        }
        return false;
    }
}
