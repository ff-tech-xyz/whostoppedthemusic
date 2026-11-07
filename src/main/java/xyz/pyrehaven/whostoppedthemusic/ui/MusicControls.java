package xyz.pyrehaven.whostoppedthemusic.ui;

import java.util.function.Consumer;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioId;
import xyz.pyrehaven.whostoppedthemusic.music.custom.ScenarioConfig;
import xyz.pyrehaven.whostoppedthemusic.JukeboxSetting;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.ApplyToAllPopup;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.ScenarioLabels;
import xyz.pyrehaven.whostoppedthemusic.ui.widget.SettingButton;

/** Shared controls observe the director; panels own their placement. */
public final class MusicControls {
    private final Minecraft client;
    private final Settings settings;
    private TransportButton primary;
    private TransportButton next;
    private TransportButton previous;
    private Button stop;
    private SettingButton frequency;
    private SettingButton jukebox;
    private final Supplier<List<ScenarioId>> scenarios;
    private boolean conciseSettings;

    public MusicControls(Minecraft client, Settings settings) {
        this(client, settings, () -> {
            var resolution = MusicDirector.get(client).customPlayback().resolution();
            return resolution == null ? List.of() : resolution.winners();
        });
    }

    public MusicControls(Minecraft client, Settings settings, Supplier<List<ScenarioId>> scenarios) {
        this.client = client;
        this.settings = settings;
        this.scenarios = scenarios;
    }

    public void settings(int x, int y, int width, Consumer<AbstractWidget> add, Runnable changeMode) {
        add.accept(Theme.button(x, y, width, text("mode." + settings.mode().name().toLowerCase(java.util.Locale.ROOT)),
                button -> changeMode.run()));
        scenarioSettings(x, y + Theme.ROW, width, add);
    }

    public void scenarioSettings(int x, int y, int width, Consumer<AbstractWidget> add) {
        scenarioSettings(x, y, width, add, false);
    }

    public void scenarioSettings(int x, int y, int width, Consumer<AbstractWidget> add, boolean concise) {
        conciseSettings = concise;
        frequency = new SettingButton(x, y, width, frequencyLabel(), button -> {
            if (custom()) {
                var values = ScenarioConfig.Frequency.values();
                var value = values[(currentFrequency().ordinal() + 1) % values.length];
                editScenarios(c -> c.withFrequency(value));
            } else {
                var option = client.options.musicFrequency();
                var values = net.minecraft.client.sounds.MusicManager.MusicFrequency.values();
                option.set(values[(option.get().ordinal() + 1) % values.length]);
            }
            updateSettings();
        }, () -> {
            if (!custom()) return;
            var value = currentFrequency();
            client.gui.setScreen(new ApplyToAllPopup(client.gui.screen(), frequency, () -> settings.applyFrequencyToAll(value)));
        });
        jukebox = new SettingButton(x, y + Theme.ROW, width, jukeboxLabel(), button -> {
            if (custom()) {
                var value = currentJukebox().next();
                editScenarios(c -> c.withJukebox(value));
            } else settings.setJukebox(settings.jukebox().next());
            updateSettings();
        }, () -> {
            if (!custom()) return;
            var value = currentJukebox();
            client.gui.setScreen(new ApplyToAllPopup(client.gui.screen(), jukebox, () -> settings.applyJukeboxToAll(value)));
        });
        if (concise) {
            frequency.overflow();
            jukebox.overflow();
        }
        add.accept(frequency);
        add.accept(jukebox);
    }

    private boolean custom() { return settings.mode() == xyz.pyrehaven.whostoppedthemusic.Mode.CUSTOM; }

    private ScenarioConfig config(ScenarioId id) {
        return settings.scenario(id, MusicDirector.get(client).customPlayback().defaults(id).config());
    }

    private void editScenarios(java.util.function.UnaryOperator<ScenarioConfig> edit) {
        for (var id : List.copyOf(scenarios.get())) {
            settings.setScenario(id, edit.apply(config(id)), MusicDirector.get(client).customPlayback().defaults(id).config());
        }
    }

    private ScenarioConfig.Frequency currentFrequency() {
        return scenarios.get().stream().map(this::config).map(ScenarioConfig::frequency)
                .max(java.util.Comparator.naturalOrder()).orElse(ScenarioConfig.Frequency.CONSTANT);
    }

    private JukeboxSetting currentJukebox() {
        return scenarios.get().stream().map(this::config).anyMatch(c -> c.jukebox() == JukeboxSetting.PAUSE_MUSIC)
                ? JukeboxSetting.PAUSE_MUSIC : JukeboxSetting.PLAY_OVER;
    }

    public void updateSettings() {
        if (frequency == null) return;
        frequency.label(frequencyLabel());
        jukebox.label(jukeboxLabel());
        frequency.active = jukebox.active = !custom() || !scenarios.get().isEmpty();
    }

    /** The same right-hand player on Basic and Custom, not two look-alike layouts. */
    public void player(int x, int y, int width, int bottom, Consumer<AbstractWidget> add, Runnable changeMode) {
        var director = MusicDirector.get(client);
        settings(x, y, width, add, changeMode);
        int third = (width - Theme.GAP * 2) / 3;
        int transportY = y + Theme.ROW * 3;
        previous = new TransportButton(x, transportY, third, TransportButton.Action.PREVIOUS, director::previous);
        add.accept(previous);
        transport(x + third + Theme.GAP, transportY, third, add);
        int tapeY = transportY + Theme.ROW;
        if (!custom()) {
            stop = Theme.button(x, tapeY, Theme.BASIC_STOP_WIDTH, text("stop"), button -> {
                director.stop();
                update();
            });
            stop.setTooltip(Tooltip.create(text("stop")));
            add.accept(stop);
            add.accept(Theme.button(x + Theme.BASIC_STOP_WIDTH + Theme.GAP, tapeY,
                    width - Theme.BASIC_STOP_WIDTH - Theme.GAP, shuffleLabel(), button -> {
                settings.setShuffle(!settings.shuffle());
                director.playlistChanged();
                button.setMessage(shuffleLabel());
            }));
            tapeY += Theme.ROW - Theme.GAP + Theme.ACCENT;
        } else {
            var caption = new Button(x, tapeY, width, Theme.LINE, Component.empty(), button -> {}, supplier -> supplier.get()) {
                private final OverflowText line = new OverflowText();
                @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                    var ids = scenarios.get();
                    var label = text("scenario.now", ScenarioLabels.names(ids));
                    if (!ids.isEmpty() && !ScenarioLabels.fixed(ids.getFirst())) label.append(text("scenario.priority", config(ids.getFirst()).priority()));
                    setMessage(label);
                    setTooltip(Tooltip.create(label));
                    line.draw(graphics, client.font, label, getX(), getRight(), getY(), Theme.TEXT, true);
                }
            };
            add.accept(caption);
            tapeY += Theme.LINE + Theme.GAP;
        }
        int tapeHeight = Math.min(Math.min(Theme.BASIC_TAPE_MAX_HEIGHT, width * 3 / 5), bottom - tapeY);
        var tape = new Button(x, tapeY, width, tapeHeight, director.status(), button -> {}, supplier -> supplier.get()) {
            private final CassetteArtwork artwork = new CassetteArtwork();
            @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                artwork.drawPlayer(graphics, this, director, false);
            }
        };
        add.accept(tape);
        add.accept(new MusicVolumeSlider(client, x + width + Theme.GAP, y, bottom - y));
        update();
    }

    private Component shuffleLabel() {
        return Component.translatable("whostoppedthemusic.shuffle", Component.translatable(settings.shuffle() ? "options.on" : "options.off"));
    }

    public void transport(int x, int y, int width, Consumer<AbstractWidget> add) {
        var director = MusicDirector.get(client);
        primary = new TransportButton(x, y, width, TransportButton.Action.PLAY, () -> {
            director.togglePlayback();
            update();
        });
        next = new TransportButton(x + width + Theme.GAP, y, width, TransportButton.Action.NEXT, () -> {
            director.next();
            update();
        });
        add.accept(primary);
        add.accept(next);
        update();
    }

    public void update() {
        updateSettings();
        var director = MusicDirector.get(client);
        if (previous != null) previous.active = director.canPrevious();
        if (stop != null) stop.active = director.active();
        primary.active = director.active() || director.canNext();
        primary.action(!director.active() ? TransportButton.Action.PLAY
                : director.paused() ? TransportButton.Action.RESUME : TransportButton.Action.PAUSE);
        next.active = director.canNext();
    }

    private Component frequencyLabel() {
        if (custom() && conciseSettings) return text("frequency", net.minecraft.client.sounds.MusicManager.MusicFrequency.valueOf(currentFrequency().name()).caption());
        if (custom()) return text("frequency.scenario", ScenarioLabels.names(scenarios.get()),
                net.minecraft.client.sounds.MusicManager.MusicFrequency.valueOf(currentFrequency().name()).caption());
        return Component.translatable("whostoppedthemusic.frequency", client.options.musicFrequency().get().caption());
    }

    private Component jukeboxLabel() {
        if (custom() && conciseSettings) return text("jukebox", text("jukebox." + currentJukebox().name().toLowerCase(java.util.Locale.ROOT)));
        if (custom()) return text("jukebox.scenario", ScenarioLabels.names(scenarios.get()),
                text("jukebox." + currentJukebox().name().toLowerCase(java.util.Locale.ROOT)));
        return Component.translatable("whostoppedthemusic.jukebox",
                text("jukebox." + settings.jukebox().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) { return Component.translatable("whostoppedthemusic." + key, args); }
}
