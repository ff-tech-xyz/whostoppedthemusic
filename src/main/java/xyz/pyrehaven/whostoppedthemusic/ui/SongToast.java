package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;

public final class SongToast implements Toast {
    private SoundInstance sound;
    private Track.Metadata metadata;
    private final OverflowText title = new OverflowText();
    private final OverflowText artist = new OverflowText();
    private long started = -1;
    private Visibility visibility = Visibility.SHOW;

    public SongToast(SoundInstance sound, Track.Metadata metadata) { replace(sound, metadata); }

    public void replace(SoundInstance sound, Track.Metadata metadata) {
        if (this.sound == sound) return;
        this.sound = sound;
        this.metadata = metadata;
        started = -1;
        visibility = Visibility.SHOW;
    }

    @Override public Visibility getWantedVisibility() { return visibility; }

    @Override
    public void update(ToastManager manager, long time) {
        if (started < 0) started = time;
        var client = manager.getMinecraft();
        var director = MusicDirector.get(client);
        visibility = director.current() == sound && director.active()
                && client.options.musicToast().get().renderToast()
                && time - started < 5000 * manager.getNotificationDisplayTimeMultiplier() ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
        var director = MusicDirector.get(net.minecraft.client.Minecraft.getInstance());
        if (director.current() != sound || !director.active()
                || !net.minecraft.client.Minecraft.getInstance().options.musicToast().get().renderToast()) return;
        graphics.fill(0, 0, width(), height(), Theme.CARD);
        graphics.fill(0, 0, Theme.ACCENT, height(), Theme.ORANGE);
        title.draw(graphics, font, metadata.title(), Theme.MARGIN, width() - Theme.GAP, Theme.TOAST_TITLE_Y, Theme.TEXT, false);
        artist.draw(graphics, font, metadata.artist(), Theme.MARGIN, width() - Theme.GAP, Theme.TOAST_SONG_Y, Theme.ARTIST, false);
    }
}
