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
import xyz.pyrehaven.whostoppedthemusic.Mode;
import xyz.pyrehaven.whostoppedthemusic.JukeboxSetting;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.ui.SongToast;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.Musics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Selection and transport inside the vanilla manager's existing playback loop. */
public final class MusicDirector {
    public interface Access {
        MusicDirector whostoppedthemusic$director();
    }

    public interface ChannelControl {
        void whostoppedthemusic$manage();
        void whostoppedthemusic$pause(boolean paused);
    }

    public interface LevelSoundControl {
        void whostoppedthemusic$stopLevelSounds(SoundInstance keep);
    }

    private final Minecraft client;
    private final MusicManager manager;
    private SoundInstance paused;
    public static final Music BASIC = new Music(Holder.direct(SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath("whostoppedthemusic", "basic"))), Musics.GAME.minDelay(), Musics.GAME.maxDelay(), false);
    private final Deck deck = new Deck(new java.util.Random());
    private Mode mode = Mode.VANILLA;
    private boolean stopped;
    private boolean jukeboxHeld;
    private Track requested;
    private volatile SoundInstance advancing;

    public boolean basic() { return WhoStoppedTheMusic.settings().mode() == Mode.BASIC; }

    public Music music(Music vanilla) { return basic() ? BASIC : vanilla; }

    public void syncMode() {
        Mode selected = WhoStoppedTheMusic.settings().mode();
        if (mode != selected) {
            mode = selected;
            stopped = false;
            requested = null;
            manager.stopPlaying();
            ((MusicManagerAccessor) manager).whostoppedthemusic$nextSongDelay(0);
        }
    }

    public java.util.List<Track> selected() {
        var settings = WhoStoppedTheMusic.settings();
        return WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> settings.ticked(t.id())).toList();
    }

    private void configureDeck() {
        deck.configure(selected().stream().map(t -> new Deck.Entry(t.id(), 1)).toList(),
                WhoStoppedTheMusic.settings().shuffle());
    }

    public void playlistChanged() {
        configureDeck();
        if (basic() && selected().isEmpty()) manager.stopPlaying();
    }

    public boolean holdTick() {
        syncMode();
        observeChannel();
        if (paused()) return true;
        boolean nearby = WhoStoppedTheMusic.settings().jukebox() == JukeboxSetting.PAUSE_MUSIC && JukeboxWatcher.nearby(client);
        if (nearby) {
            jukeboxHeld = true;
            ((MusicManagerAccessor) manager).whostoppedthemusic$fadePlaying(0);
            return true;
        }
        if (jukeboxHeld) {
            jukeboxHeld = false;
            ((MusicManagerAccessor) manager).whostoppedthemusic$nextSongDelay(0);
        }
        return basic() && stopped;
    }

    public boolean mayStart() {
        return !(WhoStoppedTheMusic.settings().jukebox() == JukeboxSetting.PAUSE_MUSIC && JukeboxWatcher.nearby(client))
                && (!basic() || !stopped && (requested != null || !selected().isEmpty()));
    }

    public SimpleSoundInstance choose(SoundEvent vanilla) {
        if (!basic()) return SimpleSoundInstance.forMusic(vanilla);
        configureDeck();
        Track track = requested;
        requested = null;
        if (track == null) {
            String id = deck.next();
            track = WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
        }
        return new TrackSoundInstance(BASIC.sound().value().location(), track);
    }

    public boolean canPrevious() { return basic() && !stopped && deck.canPrevious(); }

    public void previous() {
        if (stopped) return;
        configureDeck();
        String id = deck.previous();
        if (id != null) playSelected(id);
    }

    public void play(Track track) {
        configureDeck();
        deck.played(track.id());
        playSelected(track.id());
    }

    private void playSelected(String id) {
        requested = WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
        stopped = false;
        manager.stopPlaying();
        manager.startPlaying(BASIC);
        requested = null;
    }

    public void showToast(net.minecraft.client.gui.components.toasts.ToastManager toasts) {
        if (current() == null || manager.getCurrentMusicTranslationKey() == null
                || !client.options.musicToast().get().renderToast()) return;
        var toast = toasts.getToast(SongToast.class, net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN);
        if (toast == null) toasts.addToast(new SongToast(current(), metadata()));
        else toast.replace(current(), metadata());
    }

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

    /** Sample native playback on the sound thread, including startup and jukebox fades. */
    private void observeChannel() {
        var source = current();
        var handle = channel();
        if (handle == null || handle.isStopped()) advancing = null;
        else handle.execute(channel -> advancing = channel.playing() ? source : null);
    }

    public boolean advancing() {
        return advancing != null && advancing == current() && active() && !paused();
    }

    public boolean playing(Track track) {
        return active() && current().getSound() != null
                && current().getSound().getLocation().toString().equals(track.id());
    }

    public boolean canNext() {
        return basic() ? !selected().isEmpty() : client.getSituationalMusic() != null;
    }

    public Component status() {
        String key = manager.getCurrentMusicTranslationKey();
        if (!active() || key == null) {
            return Component.translatable("whostoppedthemusic.cassette.silence");
        }
        Component title = title();
        if (paused()) {
            return Component.translatable("whostoppedthemusic.paused", title);
        }
        return client.options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0 ? title
                : Component.translatable("whostoppedthemusic.muted", title);
    }

    private Component title() {
        return current() instanceof TrackSoundInstance track ? track.track().title()
                : Component.translatable(manager.getCurrentMusicTranslationKey().replace('/', '.'));
    }

    public Track.Metadata metadata() {
        if (!active() || manager.getCurrentMusicTranslationKey() == null)
            return new Track.Metadata(Component.empty(), Component.empty());
        if (current() instanceof TrackSoundInstance track) return track.track().metadata();
        String id = current().getSound().getLocation().toString();
        return WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(track -> track.id().equals(id))
                .findFirst().map(Track::metadata).orElseGet(() -> Track.metadata(Component.translatableWithFallback(
                        manager.getCurrentMusicTranslationKey().replace('/', '.'), TrackNames.prettify(id))));
    }

    public Component playbackStatus() {
        if (!active()) return Component.translatable("whostoppedthemusic.cassette.silence");
        if (paused()) return Component.translatable("whostoppedthemusic.state.paused");
        return client.options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0 ? Component.empty()
                : Component.translatable("whostoppedthemusic.state.muted");
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
        syncMode();
        stopped = false;
        Music music = music(client.getSituationalMusic());
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

    public void stop() {
        if (!basic()) return;
        stopped = true;
        manager.stopPlaying();
    }

    public void stopped() {
        paused = null;
    }

    private ChannelAccess.ChannelHandle channel() {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        return ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(current());
    }

}
