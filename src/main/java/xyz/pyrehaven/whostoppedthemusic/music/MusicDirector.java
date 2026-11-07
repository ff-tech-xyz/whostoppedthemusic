package xyz.pyrehaven.whostoppedthemusic.music;

import com.mojang.blaze3d.audio.Channel;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.MusicManager.MusicFrequency;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import xyz.pyrehaven.whostoppedthemusic.JukeboxSetting;
import xyz.pyrehaven.whostoppedthemusic.Mode;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.mixin.MusicManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.music.custom.CustomPlayback;
import xyz.pyrehaven.whostoppedthemusic.music.custom.Preview;
import xyz.pyrehaven.whostoppedthemusic.music.custom.SituationReader;

/** Selection and transport inside the vanilla manager's existing playback loop. */
public final class MusicDirector {
    public static final Music BASIC = new Music(Holder.direct(SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath("whostoppedthemusic", "basic"))), Musics.GAME.minDelay(), Musics.GAME.maxDelay(), false);

    private final Minecraft client;
    private final MusicManager manager;
    private SoundInstance paused;
    private final Deck deck = new Deck(new Random());
    private Mode mode = Mode.VANILLA;
    private boolean stopped;
    private boolean jukeboxHeld;
    private Track requested;
    private volatile SoundInstance advancing;
    private final CustomPlayback custom = new CustomPlayback(WhoStoppedTheMusic.settings(), WhoStoppedTheMusic.LIBRARY);
    private final Preview preview;

    public MusicDirector(Minecraft client, MusicManager manager) {
        this.client = client;
        this.manager = manager;
        this.preview = new Preview(client, this);
    }

    public Preview preview() { return preview; }
    public boolean jukeboxHolding() { return jukebox() == JukeboxSetting.PAUSE_MUSIC && JukeboxWatcher.nearby(client); }

    public boolean basic() { return WhoStoppedTheMusic.settings().mode() == Mode.BASIC; }

    public boolean custom() { return WhoStoppedTheMusic.settings().mode() == Mode.CUSTOM; }
    private boolean playlist() { return basic() || custom(); }
    public CustomPlayback customPlayback() { return custom; }

    public Music music(Music vanilla) { return custom() ? custom.music() : basic() ? BASIC : vanilla; }

    public MusicFrequency frequency(MusicFrequency vanilla) {
        return custom() && custom.resolution() != null ? MusicFrequency.valueOf(custom.resolution().frequency().name()) : vanilla;
    }

    public float volume(float vanilla) { return basic() || custom() && custom.edited() ? 1 : vanilla; }

    private JukeboxSetting jukebox() {
        return custom() && custom.resolution() != null ? custom.resolution().jukebox() : WhoStoppedTheMusic.settings().jukebox();
    }

    public void syncMode() {
        Mode selected = WhoStoppedTheMusic.settings().mode();
        if (mode != selected) {
            mode = selected;
            custom.resetWinners();
            if (custom()) resolveCustom();
            stopped = false;
            requested = null;
            manager.stopPlaying();
            ((MusicManagerAccessor) manager).whostoppedthemusic$nextSongDelay(0);
        }
    }

    public List<Track> selected() {
        var settings = WhoStoppedTheMusic.settings();
        return WhoStoppedTheMusic.LIBRARY.tracks().stream().filter(Track::playable).filter(t -> settings.ticked(t.id())).toList();
    }

    private void configureDeck() {
        if (custom()) return;
        deck.configure(selected().stream().map(t -> new Deck.Entry(t.id(), 1)).toList(),
                WhoStoppedTheMusic.settings().shuffle());
    }

    public void playlistChanged() {
        configureDeck();
        if (basic() && selected().isEmpty()) manager.stopPlaying();
    }

    public void libraryChanged() {
        preview.libraryChanged();
        custom.reload();
        configureDeck();
        if (current() instanceof TrackSoundInstance chosen && chosen.track().local() != null
                && WhoStoppedTheMusic.LIBRARY.tracks().stream().noneMatch(t -> t.id().equals(chosen.track().id()) && t.playable())) {
            manager.stopPlaying();
        }
    }

    public boolean holdTick() {
        boolean changed = mode != WhoStoppedTheMusic.settings().mode();
        syncMode();
        if (custom() && !changed) resolveCustom();
        observeChannel();
        if (preview.tick()) return true;
        if (paused()) return true;
        boolean nearby = jukeboxHolding();
        if (nearby) {
            jukeboxHeld = true;
            ((MusicManagerAccessor) manager).whostoppedthemusic$fadePlaying(0);
            return true;
        }
        if (jukeboxHeld) {
            jukeboxHeld = false;
            var handle = channel();
            if (handle != null) handle.execute(channel -> ((MusicChannelControl) channel).whostoppedthemusic$pause(false));
            ((MusicManagerAccessor) manager).whostoppedthemusic$nextSongDelay(0);
        }
        return playlist() && stopped;
    }

    public void worldChanged(ClientLevel level) {
        preview.cancel();
        custom.catalog().rebuild(level == null ? RegistryAccess.EMPTY : level.registryAccess(),
                level == null ? null : level.dimensionType(), WhoStoppedTheMusic.settings());
        custom.reload();
    }

    private void resolveCustom() {
        custom.tick(SituationReader.read(client), client.level == null ? null : client.level.dimensionType());
    }

    private Deck activeDeck() { return custom() ? custom.deck() : deck; }

    public boolean mayStart() {
        return !preview.active() && !jukeboxHolding()
                && (!playlist() || !stopped && (requested != null || (custom()
                ? custom.resolution() != null && !custom.resolution().silent() : !selected().isEmpty())));
    }

    public SimpleSoundInstance choose(SoundEvent vanilla) {
        if (!playlist()) return SimpleSoundInstance.forMusic(vanilla);
        configureDeck();
        Track track = requested;
        requested = null;
        if (track == null) {
            String id = activeDeck().next();
            track = WhoStoppedTheMusic.LIBRARY.find(id).orElseThrow();
        }
        return new TrackSoundInstance(music(BASIC).sound().value().location(), track);
    }

    public boolean canPrevious() { return playlist() && !stopped && activeDeck() != null && activeDeck().canPrevious(); }

    public void previous() {
        if (!canPrevious()) return;
        configureDeck();
        String id = activeDeck().previous();
        if (id != null) playSelected(id);
    }

    public void play(Track track) {
        if (!track.playable()) return;
        configureDeck();
        activeDeck().played(track.id());
        playSelected(track.id());
    }

    private void playSelected(String id) {
        requested = WhoStoppedTheMusic.LIBRARY.find(id).orElseThrow();
        stopped = false;
        manager.stopPlaying();
        manager.startPlaying(music(BASIC));
        requested = null;
    }

    public static MusicDirector get(Minecraft client) {
        return ((DirectorAccess) client.getMusicManager()).whostoppedthemusic$director();
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
        var source = displaySource();
        var handle = channel(source);
        if (handle == null || handle.isStopped()) advancing = null;
        else handle.execute(channel -> advancing = channel.playing() ? source : null);
    }

    public boolean advancing() {
        return advancing != null && advancing == current() && active() && !paused();
    }

    /** The editor temporarily displays its preview instead of the held background source. */
    public SoundInstance displaySource() {
        return preview.active() ? preview.source() : current();
    }

    public boolean displayAdvancing() {
        var source = displaySource();
        var handle = channel(source);
        return source != null && source == advancing && handle != null && !handle.isStopped()
                && (source != current() || !paused());
    }

    public boolean playing(Track track) {
        var source = displaySource();
        var handle = channel(source);
        if (handle == null || handle.isStopped()) return false;
        if (source instanceof TrackSoundInstance chosen) return chosen.track().id().equals(track.id());
        return source.getSound() != null && source.getSound().getLocation().toString().equals(track.id());
    }

    public boolean canNext() {
        if (custom()) return custom.resolution() != null && !custom.resolution().silent();
        return basic() ? !selected().isEmpty() : client.getSituationalMusic() != null;
    }

    public Track.Metadata metadata() {
        if (!active() || manager.getCurrentMusicTranslationKey() == null)
            return new Track.Metadata(Component.empty(), Component.empty());
        if (current() instanceof TrackSoundInstance track) return track.track().metadata();
        String id = current().getSound().getLocation().toString();
        return WhoStoppedTheMusic.LIBRARY.find(id).map(Track::metadata).orElseGet(() -> Track.metadata(Component.translatableWithFallback(
                        manager.getCurrentMusicTranslationKey().replace('/', '.'), TrackNames.prettify(id))));
    }

    public Component playbackStatus() {
        if (!active()) return Component.translatable("whostoppedthemusic.cassette.silence");
        if (paused()) return Component.translatable("whostoppedthemusic.state.paused");
        return client.options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0 ? Component.empty()
                : Component.translatable("whostoppedthemusic.state.muted");
    }

    /** Vanilla has resolved the sound and allocated its channel; stream loading may still be pending. */
    public void started() {
        paused = null;
        var handle = channel();
        if (handle != null) {
            handle.execute(channel -> ((MusicChannelControl) channel).whostoppedthemusic$manage());
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
        preview.cancel();
        if (!active()) {
            next();
            return;
        }
        boolean resume = paused();
        paused = resume ? null : current();
        var handle = channel();
        if (handle != null) {
            handle.execute(channel -> ((MusicChannelControl) channel).whostoppedthemusic$pause(!resume));
        }
    }

    public void stop() {
        if (!playlist()) return;
        stopped = true;
        manager.stopPlaying();
    }

    public void stopped() {
        preview.cancel();
        paused = null;
    }

    /** An asynchronously failed open must release its INITIAL native source as well. */
    public void failed(TrackSoundInstance source) {
        var handle = SoundChannels.get(client, source);
        if (handle != null) handle.execute(Channel::destroy);
        preview.failed(source);
        if (current() == source) manager.stopPlaying();
    }

    private ChannelAccess.ChannelHandle channel() {
        return channel(current());
    }

    private ChannelAccess.ChannelHandle channel(SoundInstance source) {
        return SoundChannels.get(client, source);
    }

}
