package xyz.pyrehaven.whostoppedthemusic.ui.widget;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.TrackNames;
import xyz.pyrehaven.whostoppedthemusic.ui.CassetteArtwork;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import xyz.pyrehaven.whostoppedthemusic.ui.TransportButton;

public final class TrackList extends SmoothList<TrackList.Row> {
    private final Settings settings;
    private final Consumer<Track> remove;
    private String query = "";
    private int rowHeight = Theme.TRACK_HEIGHT;
    private Consumer<Track> drag;
    private Predicate<String> contains;
    private Supplier<List<Deck.Entry>> songs;

    private String focusedId;
    private String insertedId;
    private long insertedAt;

    public void inserted(String id) {
        insertedId = id;
        insertedAt = Util.getMillis();
    }

    /** Compact drag rows and cassette rows share the same search, scroll and input owner. */
    public void library(Consumer<Track> drag, Predicate<String> contains) {
        this.drag = drag;
        this.contains = contains;
        rowHeight = Theme.LIBRARY_ROW;
        filter(query);
    }

    public void songs(Supplier<List<Deck.Entry>> songs) {
        this.songs = songs;
        refresh();
    }

    public void refresh() {
        double position = scrollAmount();
        filter(query);
        setScrollAmount(position);
    }

    public String focusedId() { return focusedId; }

    public void selectSong(String id) {
        focus(id);
        children().stream().filter(r -> id.equals(r.songId)).findFirst().ifPresent(this::centerScrollOn);
    }

    private void focus(String id) {
        focusedId = id;
    }

    public TrackList(Minecraft client, Settings settings, int x, int y, int width, int height) {
        this(client, settings, x, y, width, height, null);
    }

    public TrackList(Minecraft client, Settings settings, int x, int y, int width, int height,
                     Consumer<Track> remove) {
        super(client, width, height, y, Theme.TRACK_HEIGHT);
        this.settings = settings;
        this.remove = remove;
        setX(x);
        filter("");
    }

    public void filter(String query) {
        this.query = query.trim();
        clearEntries();
        if (songs != null) {
            for (var entry : songs.get()) {
                var track = WhoStoppedTheMusic.LIBRARY.find(entry.id()).orElse(null);
                addEntry(new Row(track, track == null ? Component.literal(entry.id()) : track.title(), entry.id()), rowHeight);
            }
            if (songs.get().stream().noneMatch(e -> e.id().equals(focusedId))) {
                focus(songs.get().isEmpty() ? null : songs.get().getFirst().id());
            }
            setScrollAmount(0);
            return;
        }
        record Match(Track track, int rank) { }
        var matches = WhoStoppedTheMusic.LIBRARY.tracks().stream()
                .filter(track -> remove == null || track.local() != null)
                .map(track -> {
                    var metadata = track.metadata();
                    return new Match(track, TrackNames.searchRank(this.query,
                            new TrackNames.Metadata(metadata.title().getString(), metadata.artist().getString()), track.id()));
                })
                .filter(match -> match.rank() != Integer.MAX_VALUE)
                .sorted(Comparator.comparingInt(Match::rank).thenComparingInt(match ->
                        drag != null && this.query.isEmpty() ? libraryKind(match.track()).ordinal() : 0)).toList();
        Track.Kind previous = null;
        for (var match : matches) {
            var track = match.track();
            var kind = drag != null ? libraryKind(track) : track.kind();
            if (this.query.isEmpty() && previous != kind) {
                addEntry(new Row(null, Component.translatable("whostoppedthemusic.section." + kind.name().toLowerCase(Locale.ROOT))), Theme.TRACK_HEADER_HEIGHT);
                previous = kind;
            }
            addEntry(new Row(track, track.title()), rowHeight);
        }
        setScrollAmount(0);
    }

    public void layout(int x, int y, int width, int height) {
        double position = scrollAmount() / rowHeight;
        int newRowHeight = drag != null ? Theme.LIBRARY_ROW : Math.max(height * 2 / 3, Theme.LABEL_MIN_TAPE_HEIGHT + Theme.GAP
                + (songs == null && width < Theme.TRACK_SIDE_CONTROLS_MIN ? Theme.ROW : 0));
        updateSizeAndPosition(width, height, x, y);
        if (rowHeight != newRowHeight) {
            rowHeight = newRowHeight;
            filter(query);
        }
        setScrollAmount(position * rowHeight);
    }

    private static Track.Kind libraryKind(Track track) {
        return !track.id().startsWith("minecraft:") ? Track.Kind.LOCAL : track.kind();
    }

    @Override protected int wheelRowHeight() { return rowHeight; }

    @Override public int getRowWidth() { return getWidth() - Theme.TRACK_SCROLLBAR_SPACE; }
    @Override protected void extractListBackground(GuiGraphicsExtractor graphics) { }
    @Override protected void extractListSeparators(GuiGraphicsExtractor graphics) { }

    public final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Track track;
        private final Component title;
        private final String songId;
        private final Button tick;
        private final Button play;
        private final Button tape;

        private Row(Track track, Component title) {
            this(track, title, track == null ? null : track.id());
        }

        private Row(Track track, Component title, String songId) {
            this.track = track;
            this.title = title;
            this.songId = songId;
            tick = Theme.button(0, 0, Theme.CONTROL_HEIGHT, Component.empty(), button -> {
                settings.setTicked(track.id(), !settings.ticked(track.id()));
                MusicDirector.get(minecraft).playlistChanged();
            });
            play = remove == null ? new TransportButton(0, 0, Theme.TRACK_PLAY_WIDTH, TransportButton.Action.PLAY,
                    () -> MusicDirector.get(minecraft).play(track))
                    : Theme.button(0, 0, Theme.TRACK_REMOVE_WIDTH, Component.translatable("whostoppedthemusic.remove"),
                            button -> remove.accept(track));
            tape = new Button(0, 0, Theme.CONTROL_HEIGHT, Theme.CONTROL_HEIGHT, title, button -> {
                if (songs != null) selectSong(songId);
                else if (drag != null && !contains.test(songId)) drag.accept(track);
            }, supplier -> supplier.get()) {
                private final CassetteArtwork artwork = new CassetteArtwork();
                @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                    if (drag != null) {
                        artwork.drawCase(graphics, this, track, contains.test(songId));
                    } else if (track == null) artwork.drawMissing(graphics, this, title);
                    else artwork.drawTrack(graphics, this, track, MusicDirector.get(minecraft));
                    if (songs != null && songId.equals(focusedId)) graphics.outline(getX(), getY(), getWidth(), getHeight(), Theme.ORANGE);
                }
            };
            if (track != null) {
                if (remove == null) play.setMessage(Component.translatable("whostoppedthemusic.play_track", title));
                tick.setTooltip(Tooltip.create(Component.translatable("whostoppedthemusic.select_track", title)));
                play.setTooltip(Tooltip.create(Component.translatable(
                        "whostoppedthemusic." + (remove == null ? "play_track" : "remove_track"), title)));
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            if (songId == null) {
                graphics.text(minecraft.font, title, getContentX(), getContentY() + Theme.GAP / 2,
                        drag != null ? Theme.TEXT : Theme.ORANGE, drag == null);
                return;
            }
            if (songs != null || drag != null) {
                tape.setRectangle(getContentWidth(), getContentHeight() - Theme.GAP, getContentX(), getContentY());
                graphics.pose().pushMatrix();
                if (songId.equals(insertedId)) {
                    double progress = Math.clamp((Util.getMillis() - insertedAt) / (double) Theme.INSERT_MILLIS, 0, 1);
                    graphics.pose().translate((float) ((1 - progress) * (1 - progress) * Theme.DRAG_WIDTH), 0);
                    if (progress == 1) insertedId = null;
                }
                tape.extractRenderState(graphics, mouseX, mouseY, delta);
                graphics.pose().popMatrix();
                return;
            }
            boolean narrow = getContentWidth() < Theme.TRACK_SIDE_CONTROLS_MIN;
            int leftSpace = remove == null ? Theme.CONTROL_HEIGHT + Theme.TRACK_TEXT_GAP : 0;
            int tapeX = narrow ? getContentX() : getContentX() + leftSpace;
            int tapeWidth = narrow ? getContentWidth() : getContentWidth() - leftSpace - play.getWidth() - Theme.TRACK_TEXT_GAP;
            int tapeHeight = getContentHeight() - Theme.GAP - (narrow ? Theme.ROW : 0);
            tape.setRectangle(tapeWidth, tapeHeight, tapeX, getContentY());
            int controlY = narrow ? getContentY() + tapeHeight + Theme.GAP : getContentY() + (tapeHeight - Theme.CONTROL_HEIGHT) / 2;
            tick.setPosition(getContentX(), controlY);
            play.setPosition(getContentRight() - play.getWidth(), controlY);
            tick.setMessage(Component.literal(settings.ticked(track.id()) ? "x" : ""));
            play.active = remove != null || track.playable();
            if (remove == null && !track.playable()) {
                var unavailable = Component.translatable("whostoppedthemusic.cant_play", track.title());
                play.setMessage(unavailable);
                play.setTooltip(Tooltip.create(unavailable));
            }
            if (remove == null) tick.extractRenderState(graphics, mouseX, mouseY, delta);
            play.extractRenderState(graphics, mouseX, mouseY, delta);
            tape.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        @Override public List<? extends GuiEventListener> children() { return songId == null ? List.of() : songs != null || drag != null ? List.of(tape) : remove == null ? List.of(tick, tape, play) : List.of(tape, play); }
        @Override public List<? extends NarratableEntry> narratables() { return songId == null ? List.of() : songs != null || drag != null ? List.of(tape) : remove == null ? List.of(tick, tape, play) : List.of(tape, play); }
    }
}
