package xyz.pyrehaven.whostoppedthemusic.music.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.resources.Identifier;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundEngineAccessor;
import xyz.pyrehaven.whostoppedthemusic.mixin.SoundManagerAccessor;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.TrackSoundInstance;

/** One temporary source, ticked only by the existing director; never selects or records a song. */
public final class Preview {
    private final Minecraft client;
    private final MusicDirector director;
    private TrackSoundInstance source;
    private SoundInstance background;

    public Preview(Minecraft client, MusicDirector director) {
        this.client = client;
        this.director = director;
    }

    public boolean active() { return source != null; }
    public boolean playing(String id) { return source != null && source.track().id().equals(id); }

    public void toggle(Track track) {
        boolean same = playing(track.id());
        cancel();
        if (same || !track.playable()) return;
        background = director.current();
        pause(background, true);
        source = new TrackSoundInstance(Identifier.fromNamespaceAndPath("whostoppedthemusic", "preview"), track, true);
        client.getSoundManager().play(source);
        var handle = channel(source);
        if (handle == null) cancel();
        else handle.execute(channel -> ((MusicDirector.ChannelControl) channel).whostoppedthemusic$manage());
    }

    public boolean tick() {
        if (source == null) return false;
        var handle = channel(source);
        if (handle == null || handle.isStopped()) cancel();
        return source != null;
    }

    public void libraryChanged() {
        if (source != null && WhoStoppedTheMusic.LIBRARY.tracks().stream()
                .noneMatch(t -> t.id().equals(source.track().id()) && t.playable())) cancel();
    }

    public void failed(TrackSoundInstance failed) {
        if (source == failed) cancel();
    }

    public void cancel() {
        if (source == null) return;
        client.getSoundManager().stop(source);
        source = null;
        if (background == director.current()) pause(background, director.paused() || director.jukeboxHolding());
        background = null;
    }

    private void pause(SoundInstance sound, boolean paused) {
        var handle = channel(sound);
        if (handle != null) handle.execute(channel -> ((MusicDirector.ChannelControl) channel).whostoppedthemusic$pause(paused));
    }

    private ChannelAccess.ChannelHandle channel(SoundInstance sound) {
        var engine = ((SoundManagerAccessor) client.getSoundManager()).whostoppedthemusic$engine();
        return ((SoundEngineAccessor) engine).whostoppedthemusic$channels().get(sound);
    }
}
