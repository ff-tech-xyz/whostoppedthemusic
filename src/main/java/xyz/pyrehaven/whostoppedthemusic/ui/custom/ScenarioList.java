package xyz.pyrehaven.whostoppedthemusic.ui.custom;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.BiomeCatalog;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;
import xyz.pyrehaven.whostoppedthemusic.music.custom.Special;
import xyz.pyrehaven.whostoppedthemusic.ui.OverflowText;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/** Registry rows only: absent mods remain saved but never appear as invented UI rows. */
public final class ScenarioList extends ContainerObjectSelectionList<ScenarioList.Row> {
    private final Settings settings;
    private final Consumer<ScenarioId> open;
    private List<BiomeCatalog.Entry> biomes = List.of();
    private String query = "";

    public ScenarioList(Minecraft client, Settings settings, Consumer<ScenarioId> open) {
        super(client, Theme.CONTROL_HEIGHT, Theme.CONTROL_HEIGHT, 0, Theme.SCENARIO_ROW);
        this.settings = settings;
        this.open = open;
        refresh();
        filter("");
    }

    public void refresh() {
        var loaded = MusicDirector.get(minecraft).customPlayback().catalog().entries();
        if (!loaded.equals(biomes)) {
            biomes = loaded;
            filter(query);
        }
    }

    public void filter(String value) {
        query = value.strip().toLowerCase(Locale.ROOT);
        clearEntries();
        var specials = java.util.Arrays.stream(Special.values()).map(ScenarioId::special).filter(this::matches).toList();
        if (!specials.isEmpty()) {
            addEntry(new Row(null, text("scenario.specials")), Theme.TRACK_HEADER_HEIGHT);
            specials.forEach(id -> addEntry(new Row(id, ScenarioLabels.name(id)), Theme.SCENARIO_ROW));
        }
        var groups = new java.util.TreeMap<String, List<BiomeCatalog.Entry>>(java.util.Comparator
                .comparingInt(ScenarioList::groupOrder).thenComparing(java.util.Comparator.naturalOrder()));
        for (var entry : biomes) {
            if (matches(entry.id()) || entry.group().toLowerCase(Locale.ROOT).contains(query)) {
                groups.computeIfAbsent(entry.group(), ignored -> new java.util.ArrayList<>()).add(entry);
            }
        }
        groups.forEach((group, entries) -> {
            var groupName = Component.translatableWithFallback("whostoppedthemusic.scenario.group." + group, group);
            addEntry(new Row(null, text("scenario.biomes", groupName)), Theme.TRACK_HEADER_HEIGHT);
            entries.stream().sorted(java.util.Comparator.comparing(e -> ScenarioLabels.name(e.id()).getString()))
                    .forEach(e -> addEntry(new Row(e.id(), ScenarioLabels.name(e.id())), Theme.SCENARIO_ROW));
        });
        setScrollAmount(0);
    }

    private boolean matches(ScenarioId id) {
        return id.toString().contains(query) || ScenarioLabels.name(id).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private static int groupOrder(String group) {
        return switch (group) { case "overworld" -> 0; case "nether" -> 1; case "end" -> 2; default -> 3; };
    }

    private ScenarioConfig defaults(ScenarioId id) { return MusicDirector.get(minecraft).customPlayback().defaults(id).config(); }
    private ScenarioConfig config(ScenarioId id) { return settings.scenario(id, defaults(id)); }
    @Override public int getRowWidth() { return getWidth() - Theme.TRACK_SCROLLBAR_SPACE; }
    @Override protected void extractListBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), Theme.SCENARIO_PANEL);
    }
    @Override protected void extractListSeparators(GuiGraphicsExtractor graphics) {}

    public final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final ScenarioId id;
        private final Component title;
        private final Button edit;
        private final Toggle toggle;
        private final OverflowText groupLabel = new OverflowText();

        private Row(ScenarioId id, Component title) {
            this.id = id;
            this.title = title;
            toggle = id != null && id.kind() == ScenarioId.Kind.SPECIAL && !ScenarioLabels.fixed(id)
                    ? new Toggle(title, () -> config(id).active(), () -> settings.setScenario(id,
                            config(id).withActive(!config(id).active()), defaults(id))) : null;
            edit = new Button(0, 0, Theme.CONTROL_HEIGHT, Theme.SCENARIO_ROW - Theme.GAP, title,
                    button -> open.accept(id), supplier -> supplier.get()) {
                private final OverflowText name = new OverflowText();
                private final OverflowText detail = new OverflowText();
                @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                    var resolution = MusicDirector.get(minecraft).customPlayback().resolution();
                    boolean playing = resolution != null && resolution.winners().contains(id);
                    var c = config(id);
                    var heading = Component.empty();
                    if (playing) heading.append(text("scenario.playing_prefix"));
                    heading.append(title);
                    if (settings.songsEdited(id, defaults(id))) heading.append(text("scenario.edited"));
                    var summary = ScenarioLabels.fixed(id) ? text("scenario.always", c.songs().size())
                            : text("scenario.summary", c.priority(), c.songs().size());
                    if (playing) summary.append(text("scenario.playing"));
                    setMessage(heading.copy().append(". ").append(summary));
                    setTooltip(Tooltip.create(getMessage()));
                    graphics.fill(getX(), getY(), getRight(), getBottom(), Theme.SCENARIO_BOX);
                    if (playing || isHoveredOrFocused()) graphics.outline(getX(), getY(), getWidth(), getHeight(), Theme.SCENARIO_TEXT);
                    int right = getRight() - Theme.GAP - (toggle == null ? 0 : Theme.TOGGLE_WIDTH + Theme.GAP);
                    name.draw(graphics, minecraft.font, heading.withStyle(style -> style.withBold(playing)),
                            getX() + Theme.GAP, right, getY() + Theme.BORDER, Theme.SCENARIO_TEXT, false, false);
                    detail.draw(graphics, minecraft.font, summary, getX() + Theme.GAP, right,
                            getBottom() - minecraft.font.lineHeight - Theme.BORDER, Theme.SCENARIO_TEXT, false, false);
                }
            };
        }

        @Override public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            if (id == null) {
                groupLabel.draw(graphics, minecraft.font, title, getContentX(), getContentRight(),
                        getContentY() + Theme.BORDER, Theme.SCENARIO_TEXT, false, false);
                return;
            }
            edit.setRectangle(getContentWidth(), getContentHeight() - Theme.GAP, getContentX(), getContentY());
            // The toggle is a separate focus/click target, never a row-click side effect.
            if (toggle != null) toggle.setPosition(edit.getRight() - Theme.TOGGLE_WIDTH - Theme.GAP,
                    edit.getY() + (edit.getHeight() - Theme.CONTROL_HEIGHT) / 2);
            edit.extractRenderState(graphics, mouseX, mouseY, delta);
            if (toggle != null) toggle.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        @Override public List<? extends GuiEventListener> children() {
            return id == null ? List.of() : toggle == null ? List.of(edit) : List.of(toggle, edit);
        }
        @Override public List<? extends NarratableEntry> narratables() {
            return id == null ? List.of() : toggle == null ? List.of(edit) : List.of(toggle, edit);
        }
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable("whostoppedthemusic." + key, args);
    }
}
