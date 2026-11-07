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
import xyz.pyrehaven.whostoppedthemusic.music.TrackNames;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteArtwork;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

public final class TrackList extends ContainerObjectSelectionList<TrackList.Row> {
    private final Settings settings;
    private String query = "";
    private int rowHeight = Theme.TRACK_HEIGHT;
    private double targetScroll;
    private long lastFrame;

    public TrackList(Minecraft client, Settings settings, int x, int y, int width, int height) {
        super(client, width, height, y, Theme.TRACK_HEIGHT);
        this.settings = settings;
        setX(x);
        filter("");
    }

    public void filter(String query) {
        this.query = query.trim();
        clearEntries();
        record Match(Track track, int rank) { }
        var matches = WhoStoppedTheMusic.LIBRARY.tracks().stream()
                .map(track -> new Match(track, TrackNames.searchRank(this.query, track.title().getString(), track.id())))
                .filter(match -> match.rank() != Integer.MAX_VALUE)
                .sorted(java.util.Comparator.comparingInt(Match::rank)).toList();
        Track.Kind previous = null;
        for (var match : matches) {
            var track = match.track();
            if (this.query.isEmpty() && previous != track.kind()) {
                addEntry(new Row(null, Component.translatable("whostoppedthemusic.section." + track.kind().name().toLowerCase(Locale.ROOT))), Theme.TRACK_HEADER_HEIGHT);
                previous = track.kind();
            }
            addEntry(new Row(track, track.title()), rowHeight);
        }
        setScrollAmount(0);
    }

    public void layout(int x, int y, int width, int height) {
        double position = scrollAmount() / rowHeight;
        int newRowHeight = height * 2 / 3;
        updateSizeAndPosition(width, height, x, y);
        if (rowHeight != newRowHeight) {
            rowHeight = newRowHeight;
            filter(query);
        }
        setScrollAmount(position * rowHeight);
    }

    @Override public void setScrollAmount(double amount) {
        super.setScrollAmount(amount);
        targetScroll = scrollAmount();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        targetScroll = Math.clamp(targetScroll - vertical * rowHeight / 2.0, 0, maxScrollAmount());
        return true;
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = net.minecraft.util.Util.getMillis();
        double blend = 1 - Math.exp(-Math.min(now - lastFrame, Theme.SCROLL_MAX_FRAME_MS) / Theme.SCROLL_RESPONSE_MS);
        lastFrame = now;
        double distance = targetScroll - scrollAmount();
        super.setScrollAmount(Math.abs(distance) < Theme.SCROLL_EPSILON ? targetScroll : scrollAmount() + distance * blend);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public int getRowWidth() { return getWidth() - Theme.TRACK_SCROLLBAR_SPACE; }
    @Override protected void extractListBackground(GuiGraphicsExtractor graphics) { }
    @Override protected void extractListSeparators(GuiGraphicsExtractor graphics) { }

    public final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Track track;
        private final Component title;
        private final Button tick;
        private final Button play;
        private final Button tape;

        private Row(Track track, Component title) {
            this.track = track;
            this.title = title;
            tick = Theme.button(0, 0, Theme.CONTROL_HEIGHT, Component.empty(), button -> {
                settings.setTicked(track.id(), !settings.ticked(track.id()));
                MusicDirector.get(minecraft).playlistChanged();
            });
            play = Theme.button(0, 0, Theme.TRACK_PLAY_WIDTH, Component.literal(">"), button -> MusicDirector.get(minecraft).play(track));
            tape = new Button(0, 0, Theme.CONTROL_HEIGHT, Theme.CONTROL_HEIGHT, title, button -> { }, supplier -> supplier.get()) {
                @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                    CassetteArtwork.draw(graphics, this, title, false);
                }
            };
            if (track != null) {
                tick.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("whostoppedthemusic.select_track", title)));
                play.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("whostoppedthemusic.play_track", title)));
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            if (track == null) {
                graphics.text(minecraft.font, title, getContentX(), getContentY() + Theme.GAP / 2, Theme.ORANGE);
                return;
            }
            boolean narrow = getContentWidth() < Theme.TRACK_SIDE_CONTROLS_MIN;
            int tapeX = narrow ? getContentX() : getContentX() + Theme.CONTROL_HEIGHT + Theme.TRACK_TEXT_GAP;
            int tapeWidth = narrow ? getContentWidth() : getContentWidth() - Theme.CONTROL_HEIGHT - Theme.TRACK_PLAY_WIDTH - Theme.TRACK_TEXT_GAP * 2;
            int tapeHeight = getContentHeight() - Theme.GAP - (narrow ? Theme.ROW : 0);
            tape.setRectangle(tapeWidth, tapeHeight, tapeX, getContentY());
            int controlY = narrow ? getContentY() + tapeHeight + Theme.GAP : getContentY() + (tapeHeight - Theme.CONTROL_HEIGHT) / 2;
            tick.setPosition(getContentX(), controlY);
            play.setPosition(getContentRight() - Theme.TRACK_PLAY_WIDTH, controlY);
            tick.setMessage(Component.literal(settings.ticked(track.id()) ? "x" : ""));
            tick.extractRenderState(graphics, mouseX, mouseY, delta);
            play.extractRenderState(graphics, mouseX, mouseY, delta);
            tape.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        @Override public List<? extends GuiEventListener> children() { return track == null ? List.of() : List.of(tick, play); }
        @Override public List<? extends NarratableEntry> narratables() { return track == null ? List.of() : List.of(tick, play); }
    }
}
