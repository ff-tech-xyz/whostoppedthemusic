package xyz.pyrehaven.whostoppedthemusic.music;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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

    private record Played(Music music, Sound sound) { }
    private final Minecraft client;
    private final MusicManager manager;
    private final List<Played> history = new ArrayList<>();
    private int cursor = -1;
    private Played replay;
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
        return current() != null && client.getSoundManager().isActive(current());
    }

    public boolean paused() {
        return paused != null && paused == current() && active();
    }

    public boolean canPrevious() {
        return cursor > 0;
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
        if (replay != null || !active() || current().getSound() == null) {
            return;
        }
        history.subList(cursor + 1, history.size()).clear();
        history.add(new Played(music, current().getSound()));
        if (history.size() > 64) {
            history.removeFirst();
        }
        cursor = history.size() - 1;
    }

    public void next() {
        Music music = client.getSituationalMusic();
        if (music != null) {
            manager.stopPlaying();
            manager.startPlaying(music);
        }
    }

    public void previous() {
        if (!canPrevious()) {
            return;
        }
        manager.stopPlaying();
        replay = history.get(--cursor);
        try {
            manager.startPlaying(replay.music());
        } finally {
            replay = null;
        }
    }

    /** Keep the historical pool identity for vanilla's situational replacement checks. */
    public SimpleSoundInstance createSound(SoundEvent event) {
        return replay == null ? SimpleSoundInstance.forMusic(event) : new HistoricalSound(event, replay.sound());
    }

    public void togglePause() {
        if (!active()) {
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

    private static final class HistoricalSound extends SimpleSoundInstance {
        private final Sound selected;

        private HistoricalSound(SoundEvent event, Sound selected) {
            super(event.location(), SoundSource.MUSIC, 1, 1, RandomSource.create(), false, 0,
                    SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            this.selected = selected;
        }

        @Override
        public WeighedSoundEvents getOrResolve(SoundManager manager) {
            soundEvent = manager.getSoundEvent(getIdentifier());
            sound = selected;
            return soundEvent;
        }
    }
}
