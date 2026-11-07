package xyz.pyrehaven.whostoppedthemusic.ui.widget;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

public final class TrackList extends ContainerObjectSelectionList<TrackList.Row> {
    private final Settings settings;

    public TrackList(Minecraft client, Settings settings, int x, int y, int width, int height) {
        super(client, width, height, y, Theme.TRACK_HEIGHT);
        this.settings = settings;
        setX(x);
        filter("");
    }

    public void filter(String query) {
        clearEntries();
        Track.Kind previous = null;
        for (var track : WhoStoppedTheMusic.LIBRARY.tracks()) {
            if (!track.title().getString().toLowerCase(Locale.ROOT).contains(query) && !track.id().contains(query)) continue;
            if (previous != track.kind()) {
                addEntry(new Row(null, Component.translatable("whostoppedthemusic.section." + track.kind().name().toLowerCase(Locale.ROOT))), Theme.TRACK_HEADER_HEIGHT);
                previous = track.kind();
            }
            addEntry(new Row(track, track.title()));
        }
        setScrollAmount(0);
    }

    @Override public int getRowWidth() { return getWidth() - Theme.TRACK_SCROLLBAR_SPACE; }
    @Override protected void extractListBackground(GuiGraphicsExtractor graphics) { }
    @Override protected void extractListSeparators(GuiGraphicsExtractor graphics) { }

    public final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Track track;
        private final Component title;
        private final Button tick;
        private final Button play;

        private Row(Track track, Component title) {
            this.track = track;
            this.title = title;
            tick = Theme.button(0, 0, Theme.CONTROL_HEIGHT, Component.empty(), button -> {
                settings.setTicked(track.id(), !settings.ticked(track.id()));
                MusicDirector.get(minecraft).playlistChanged();
            });
            play = Theme.button(0, 0, Theme.TRACK_PLAY_WIDTH, Component.literal(">"), button -> MusicDirector.get(minecraft).play(track));
            if (track != null) {
                tick.setTooltip(net.minecraft.client.gui.components.Tooltip.create(title));
                play.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("whostoppedthemusic.play_track", title)));
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            if (track == null) {
                graphics.text(minecraft.font, title, getContentX(), getContentY() + Theme.GAP / 2, Theme.ORANGE);
                return;
            }
            tick.setPosition(getContentX(), getContentY());
            play.setPosition(getContentRight() - Theme.TRACK_PLAY_WIDTH, getContentY());
            tick.setMessage(Component.literal(settings.ticked(track.id()) ? "x" : ""));
            tick.extractRenderState(graphics, mouseX, mouseY, delta);
            play.extractRenderState(graphics, mouseX, mouseY, delta);
            int left = getContentX() + Theme.CONTROL_HEIGHT + Theme.TRACK_TEXT_GAP;
            int right = getContentRight() - Theme.TRACK_PLAY_WIDTH - Theme.TRACK_TEXT_GAP;
            graphics.enableScissor(left, getContentY(), right, getContentBottom());
            int overflow = Math.max(0, minecraft.font.width(title) - (right - left));
            double phase = (net.minecraft.util.Util.getMillis() % 10000L) / 10000.0;
            int scroll = (int) (overflow * (1 - Math.cos(phase * Math.PI * 2)) / 2);
            graphics.text(minecraft.font, title, left - scroll, getContentY() + Theme.GAP, Theme.TEXT);
            graphics.disableScissor();
        }

        @Override public List<? extends GuiEventListener> children() { return track == null ? List.of() : List.of(tick, play); }
        @Override public List<? extends NarratableEntry> narratables() { return track == null ? List.of() : List.of(tick, play); }
    }
}
