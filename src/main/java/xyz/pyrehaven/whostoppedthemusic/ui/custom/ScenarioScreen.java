package xyz.pyrehaven.whostoppedthemusic.ui.custom;

import java.util.List;
import java.util.ArrayList;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic;
import xyz.pyrehaven.whostoppedthemusic.music.Deck;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig;
import xyz.pyrehaven.whostoppedthemusic.ui.MusicControls;
import xyz.pyrehaven.whostoppedthemusic.ui.OverflowText;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.TrackList;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.WeightSlider;

/** One editor for biomes and specials; all mutations go through Settings. */
public final class ScenarioScreen extends Screen {
    private final Screen parent;
    private final Settings settings;
    private final ScenarioId scenario;
    private MusicControls controls;
    private int left;
    private int contentWidth;
    private EditBox search;
    private TrackList library;
    private TrackList songs;
    private WeightSlider weight;
    private Button remove;
    private Button preview;
    private Button priority;
    private Track dragging;
    private List<Track> libraryRevision;
    private List<Deck.Entry> songRevision;
    private int rightX;
    private int columnWidth;
    private int bodyY;
    private final OverflowText heading = new OverflowText();
    private final OverflowText count = new OverflowText();
    private final OverflowText probability = new OverflowText();

    public ScenarioScreen(Screen parent, Settings settings, ScenarioId scenario) {
        super(ScenarioLabels.name(scenario));
        this.parent = parent;
        this.settings = settings;
        this.scenario = scenario;
    }

    @Override protected void init() {
        boolean first = library == null;
        if (first) {
            search = Theme.search(font);
            library = new TrackList(minecraft, settings, 0, 0, Theme.TRACK_HEIGHT, Theme.TRACK_HEIGHT);
            library.library(track -> dragging = track, id -> config().songs().stream().anyMatch(e -> e.id().equals(id)));
            songs = new TrackList(minecraft, settings, 0, 0, Theme.TRACK_HEIGHT, Theme.TRACK_HEIGHT);
            songs.songs(() -> config().songs());
            search.setResponder(library::filter);
        }
        contentWidth = Math.min(Theme.MAX_WIDTH, width - Theme.MARGIN * 2);
        left = (width - contentWidth) / 2;
        int x = left + Theme.PADDING;
        int available = contentWidth - Theme.PADDING * 2;
        addRenderableWidget(Theme.scenarioButton(x, Theme.MARGIN, Theme.EDITOR_BACK_WIDTH, Component.translatable("gui.back"), button -> onClose()));
        settingsGrid(x, Theme.BASIC_TOP, available);
        bodyY = Theme.BASIC_TOP + Theme.EDITOR_CONTROLS;
        columnWidth = (available - Theme.GAP * 2) / 2;
        rightX = x + available - columnWidth;
        search.setRectangle(columnWidth, Theme.CONTROL_HEIGHT, x, bodyY + Theme.LINE);
        addRenderableWidget(search);
        int bottom = height - Theme.MARGIN;
        library.layout(x, search.getBottom() + Theme.GAP, columnWidth, bottom - search.getBottom() - Theme.GAP);
        addRenderableWidget(library);
        int listY = bodyY + Theme.LINE;
        int detailY = bottom - Theme.EDITOR_DETAIL;
        songs.layout(rightX, listY, columnWidth, detailY - listY - Theme.GAP);
        addRenderableWidget(songs);
        weight = addRenderableWidget(new WeightSlider(rightX, detailY, columnWidth, this::focusedWeight,
                () -> config().songs().stream().mapToInt(Deck.Entry::weight).sum(), this::changeWeight));
        int half = (columnWidth - Theme.GAP) / 2;
        preview = addRenderableWidget(Theme.scenarioButton(rightX, bottom - Theme.CONTROL_HEIGHT, half, text("preview"), button -> {}));
        // Function 16 supplies the existing-channel preview, never row Play as a substitute.
        preview.active = false;
        preview.setTooltip(Tooltip.create(text("preview_pending")));
        remove = addRenderableWidget(Theme.scenarioButton(rightX + half + Theme.GAP, bottom - Theme.CONTROL_HEIGHT,
                columnWidth - half - Theme.GAP, text("remove"), button -> removeFocused()));
        // A retained editor may return from a picker after its settings or library changed.
        library.refresh();
        songs.refresh();
        libraryRevision = WhoStoppedTheMusic.LIBRARY.tracks();
        songRevision = config().songs();
        if (first) WhoStoppedTheMusic.LIBRARY.refreshSongs();
    }

    private void settingsGrid(int x, int y, int available) {
        boolean special = scenario.kind() == ScenarioId.Kind.SPECIAL;
        boolean fixed = ScenarioLabels.fixed(scenario);
        int columns = special && !fixed ? 4 : 3;
        int cell = (available - Theme.GAP * (columns - 1)) / columns;
        int last = x + (columns - 1) * (cell + Theme.GAP);
        controls = new MusicControls(minecraft, settings, () -> List.of(scenario));
        controls.scenarioSettings(last, y, available - (last - x), this::addRenderableWidget, true);
        if (!fixed) {
            if (special) toggle(x, y, cell, "active", () -> config().active(), () -> edit(c -> c.withActive(!c.active())));
            int priorityX = special ? x + cell + Theme.GAP : x;
            int step = Theme.CONTROL_HEIGHT;
            addRenderableWidget(Theme.scenarioButton(priorityX, y, step, Component.literal("−"), button -> edit(c -> c.withPriority(Math.max(1, c.priority() - 1)))));
            priority = addRenderableWidget(Theme.scenarioButton(priorityX + step, y, cell - step * 2, text("priority", config().priority()), button -> {}));
            addRenderableWidget(Theme.scenarioButton(priorityX + cell - step, y, step, Component.literal("+"), button -> edit(c -> c.withPriority(Math.min(10, c.priority() + 1)))));
            toggle(last - cell - Theme.GAP, y, cell, "interrupt", () -> config().interrupt(), () -> edit(c -> c.withInterrupt(!c.interrupt())));
        }
        int second = y + Theme.ROW;
        if (special && !fixed) {
            if (List.of("thunder", "rain", "snow").contains(scenario.id())) {
                toggle(x, second, cell, "outside", () -> config().mustBeOutside(), () -> edit(c -> c.withOutside(!c.mustBeOutside())));
            } else if (scenario.id().equals("underground")) {
                var level = Theme.search(font, text("y_level"));
                level.setRectangle(cell, Theme.CONTROL_HEIGHT, x, second);
                level.setMaxLength(Theme.INTEGER_LENGTH);
                level.setValue(Integer.toString(config().yLevel()));
                level.setTooltip(Tooltip.create(text("y_level")));
                level.setResponder(value -> {
                    level.setTextColor(validInteger(value) ? Theme.TEXT : Theme.ORANGE);
                    if (validInteger(value)) edit(c -> c.withYLevel(Integer.parseInt(value)));
                });
                addRenderableWidget(level);
            }
        }
        int resetX = special && !fixed ? x + cell + Theme.GAP : x;
        var reset = addRenderableWidget(Theme.scenarioButton(resetX, second, cell, text(special ? "reset_default" : "reset_vanilla"), button -> edit(c -> c.withSongs(defaults().songs()))));
        reset.setTooltip(Tooltip.create(reset.getMessage()));
        var copy = addRenderableWidget(Theme.scenarioButton(resetX + cell + Theme.GAP, second, cell, text("copy_songs"), button -> minecraft.gui.setScreen(new CopyPicker())));
        copy.setTooltip(Tooltip.create(copy.getMessage()));
    }

    private void toggle(int x, int y, int width, String key, java.util.function.BooleanSupplier value, Runnable change) {
        var toggle = new Toggle(text(key), value, change);
        toggle.setRectangle(width, Theme.CONTROL_HEIGHT, x, y);
        addRenderableWidget(toggle);
    }

    private static boolean validInteger(String value) {
        try { Integer.parseInt(value); return true; }
        catch (NumberFormatException error) { return false; }
    }

    private ScenarioConfig defaults() { return MusicDirector.get(minecraft).customPlayback().defaults(scenario).config(); }
    private ScenarioConfig config() { return settings.scenario(scenario, defaults()); }
    private void edit(UnaryOperator<ScenarioConfig> edit) { settings.setScenario(scenario, edit.apply(config()), defaults()); }
    private Deck.Entry focused() { return config().songs().stream().filter(e -> e.id().equals(songs.focusedId())).findFirst().orElse(null); }
    private int focusedWeight() { var entry = focused(); return entry == null ? 1 : entry.weight(); }

    private void changeWeight(int value) {
        String id = songs.focusedId();
        edit(c -> c.withSongs(c.songs().stream().map(e -> e.id().equals(id) ? new Deck.Entry(id, value) : e).toList()));
    }

    private void removeFocused() {
        String id = songs.focusedId();
        edit(c -> c.withSongs(c.songs().stream().filter(e -> !e.id().equals(id)).toList()));
    }

    private void add(Track track) {
        edit(c -> {
            if (c.songs().stream().anyMatch(e -> e.id().equals(track.id()))) return c;
            var counts = new java.util.TreeMap<Integer, Integer>();
            c.songs().forEach(e -> counts.merge(e.weight(), 1, Integer::sum));
            int modal = counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(10);
            var entries = new ArrayList<>(c.songs());
            entries.add(new Deck.Entry(track.id(), modal));
            return c.withSongs(entries);
        });
        songs.refresh();
        songs.selectSong(track.id());
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            var track = dragging;
            dragging = null;
            if (songs.isMouseOver(event.x(), event.y())) add(track);
            super.mouseReleased(event);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, Theme.BACKGROUND);
        graphics.fill(left, Theme.MARGIN, left + contentWidth, height - Theme.MARGIN, Theme.SCENARIO_PANEL);
        var label = title.copy().append("  (").append(description()).append(")");
        if (settings.songsEdited(scenario, defaults())) label.append(text("scenario.edited"));
        heading.draw(graphics, font, label, left + Theme.PADDING + Theme.EDITOR_BACK_WIDTH + Theme.GAP,
                left + contentWidth - Theme.PADDING, Theme.MARGIN + Theme.GAP, Theme.SCENARIO_TEXT, false, false);
        graphics.text(font, text("song_library"), left + Theme.PADDING, bodyY, Theme.SCENARIO_TEXT, false);
        count.draw(graphics, font, text("songs_in", title, config().songs().size()), rightX, rightX + columnWidth, bodyY, Theme.SCENARIO_TEXT, false, false);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        controls.updateSettings();
        if (priority != null) {
            priority.setMessage(text(priority.getWidth() < Theme.PRIORITY_LABEL_WIDTH ? "priority_short" : "priority", config().priority()));
            priority.setTooltip(Tooltip.create(text("priority", config().priority())));
        }
        if (libraryRevision != WhoStoppedTheMusic.LIBRARY.tracks()) {
            libraryRevision = WhoStoppedTheMusic.LIBRARY.tracks();
            library.refresh();
            songs.refresh();
        }
        if (!config().songs().equals(songRevision)) {
            songRevision = config().songs();
            songs.refresh();
        }
        weight.active = remove.active = focused() != null;
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        var selected = focused();
        if (selected != null) {
            probability.draw(graphics, font, weight.getMessage(),
                    rightX, rightX + columnWidth, weight.getBottom() + Theme.BORDER, Theme.SCENARIO_TEXT, false, false);
        }
        if (dragging != null) graphics.text(font, dragging.title(), mouseX + Theme.GAP, mouseY + Theme.GAP, Theme.SCENARIO_TEXT, false);
    }

    private Component description() {
        if (scenario.kind() == ScenarioId.Kind.BIOME) {
            var entry = MusicDirector.get(minecraft).customPlayback().catalog().get(scenario);
            return text("scenario.biome_kind", Component.translatableWithFallback("whostoppedthemusic.scenario.group." + (entry == null ? scenario.id() : entry.group()), scenario.id()));
        }
        return text(List.of("thunder", "rain", "snow").contains(scenario.id()) ? "scenario.weather_kind" : "scenario.special_kind");
    }

    @Override public void onClose() { dragging = null; minecraft.gui.setScreen(parent); }

    private final class CopyPicker extends Screen {
        private ScenarioList list;
        private EditBox query;
        private CopyPicker() { super(text("copy_songs")); }
        @Override protected void init() {
            int available = Math.min(Theme.MAX_WIDTH, width - Theme.MARGIN * 2);
            int x = (width - available) / 2;
            if (list == null) {
                list = new ScenarioList(minecraft, settings, id -> {
                    var source = settings.scenario(id, MusicDirector.get(minecraft).customPlayback().defaults(id).config());
                    edit(c -> c.withSongs(source.songs()));
                    onClose();
                }, true);
                query = Theme.search(font, text("scenario.search"));
                query.setResponder(list::filter);
            }
            query.setRectangle(available, Theme.CONTROL_HEIGHT, x, Theme.BASIC_TOP);
            addRenderableWidget(query);
            list.updateSizeAndPosition(available, height - Theme.BASIC_FOOTER_SPACE - Theme.BASIC_TOP - Theme.ROW, x, Theme.BASIC_TOP + Theme.ROW);
            addRenderableWidget(list);
            addRenderableWidget(Theme.scenarioButton(width / 2 - Theme.DONE_WIDTH / 2, height - Theme.MARGIN - Theme.CONTROL_HEIGHT,
                    Theme.DONE_WIDTH, Component.translatable("gui.cancel"), button -> onClose()));
        }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, Theme.SCENARIO_PANEL);
            graphics.text(font, title, Theme.MARGIN, Theme.MARGIN, Theme.SCENARIO_TEXT, false);
        }
        @Override public void onClose() { minecraft.gui.setScreen(ScenarioScreen.this); }
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable("whostoppedthemusic." + key, args);
    }
}
