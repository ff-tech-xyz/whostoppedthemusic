package xyz.pyrehaven.whostoppedthemusic.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import xyz.pyrehaven.whostoppedthemusic.mixin.MusicManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEngineAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundManagerAccessor;

/** Transport for the vanilla manager. No scheduler, library or second playback loop. */
public final class MusicDirector {
    public interface Access {
        MusicDirector whostoppedthemusic$director();
    }

    public interface ChannelControl {
        void whostoppedthemusic$manage();
        void whostoppedthemusic$pause(boolean paused);
    }

    private final Minecraft client;
    private final MusicManager manager;
    private SoundInstance paused;

    public MusicDirector(Minecraft client, MusicManager manager) {
        this.client = client;
        this.manager = manager;
    }

    public static MusicDirector get(Minecraft client) {
        return ((Access) client.getMusicManager()).whostoppedthemusic$director();
    }

    public SoundInstance current() {
        return ((MusicManagerAccessor) manager).whostoppedthemusic$currentMusic();
    }

    public boolean active() {
        var handle = channel();
        return handle != null && !handle.isStopped();
    }

    public boolean paused() {
        return paused != null && paused == current() && active();
    }

    public boolean canNext() {
        return client.getSituationalMusic() != null;
    }

    public Component status() {
        String key = manager.getCurrentMusicTranslationKey();
        if (!active() || key == null) {
            return Component.translatable("whostoppedthemusic.cassette.silence");
        }
        Component title = Component.translatable(key.replace('/', '.'));
        if (paused()) {
            return Component.translatable("whostoppedthemusic.paused", title);
        }
        return client.options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0 ? title
                : Component.translatable("whostoppedthemusic.muted", title);
    }

    /** Vanilla has resolved the sound and allocated its channel; stream loading may still be pending. */
    public void started(Music music) {
        paused = null;
        var handle = channel();
        if (handle != null) {
            handle.execute(channel -> ((ChannelControl) channel).whostoppedthemusic$manage());
        }
    }

    public void next() {
        Music music = client.getSituationalMusic();
        if (music != null) {
            manager.stopPlaying();
            manager.startPlaying(music);
        }
    }

    public void togglePlayback() {
        if (!active()) {
            next();
            return;
        }
        boolean resume = paused();
        paused = resume ? null : current();
        var handle = channel();
        if (handle != null) {
            handle.execute(channel -> ((ChannelControl) channel).whostoppedthemusic$pause(!resume));
        }
    }

    public void stopped() {
        paused = null;
    }

    private ChannelAccess.ChannelHandle channel() {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        return ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(current());
    }

}
