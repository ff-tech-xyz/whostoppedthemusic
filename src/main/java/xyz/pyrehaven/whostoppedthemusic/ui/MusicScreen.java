package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.Mode;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.CustomPanel;
import xyz.pyrehaven.whostoppedthemusic.ui.custom.ScenarioScreen;

public final class MusicScreen extends Screen {
    private final Screen parent;
    private final Settings settings;
    private int left;
    private int contentWidth;
    private MusicControls controls;
    private BasicPanel basicPanel;
    private CustomPanel customPanel;

    public MusicScreen(Screen parent, Settings settings) {
        super(Component.translatable("whostoppedthemusic.screen.title"));
        this.parent = parent;
        this.settings = settings;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(Theme.MAX_WIDTH, width - Theme.MARGIN * 2);
        left = (width - contentWidth) / 2;
        if (settings.mode() == Mode.CUSTOM) {
            if (customPanel == null) customPanel = new CustomPanel(minecraft, settings,
                    id -> minecraft.gui.setScreen(new ScenarioScreen(this, settings, id)));
            customPanel.layout(left, contentWidth, height, this::addRenderableWidget, this::changeMode);
            doneButton();
            return;
        }
        if (settings.mode() == xyz.pyrehaven.whostoppedthemusic.Mode.BASIC) {
            initBasic();
            doneButton();
            return;
        }
        int x = left + Theme.PADDING;
        int available = contentWidth - Theme.PADDING * 2;
        int controlsWidth = available < Theme.VANILLA_NARROW_THRESHOLD ? Theme.BASIC_PLAYER_MIN : Theme.VANILLA_CONTROLS_WIDTH;
        int tapeWidth = available - controlsWidth - Theme.BASIC_VOLUME_SPACE - Theme.GAP * 3;
        int controlsX = x + tapeWidth + Theme.GAP * 2;
        int y = Math.max(Theme.VANILLA_TOP, (height - Theme.VOLUME_HEIGHT) / 2);
        var director = MusicDirector.get(minecraft);
        int tapeHeight = Math.min(Theme.VANILLA_TAPE_MAX_HEIGHT, Math.max(Theme.VANILLA_TAPE_MIN_HEIGHT, tapeWidth * 3 / 5));
        var tape = new Button(x, y + (Theme.VOLUME_HEIGHT - tapeHeight) / 2, tapeWidth, tapeHeight,
                director.status(), button -> { }, supplier -> supplier.get()) {
            private final CassetteArtwork artwork = new CassetteArtwork();
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                artwork.drawPlayer(graphics, this, director, false);
            }
        };
        addRenderableWidget(tape);
        controls = new MusicControls(minecraft, settings);
        controls.settings(controlsX, y, controlsWidth, this::addRenderableWidget, this::changeMode);
        int transportWidth = (controlsWidth - Theme.GAP) / 2;
        int transportY = y + Theme.ROW * 3 + Theme.GAP;
        controls.transport(controlsX, transportY, transportWidth, this::addRenderableWidget);
        addRenderableWidget(new MusicVolumeSlider(minecraft, controlsX + controlsWidth + Theme.GAP, y, Theme.VOLUME_HEIGHT));
        updateTransport();
        doneButton();
    }

    private void initBasic() {
        if (basicPanel == null) {
            basicPanel = new BasicPanel(minecraft, settings);
            xyz.pyrehaven.whostoppedthemusic.WhoStoppedTheMusic.LIBRARY.refreshSongs().thenRun(() -> {
                if (minecraft.gui.screen() == this) basicPanel.refreshSongs();
            });
        }
        basicPanel.layout(left, contentWidth, height, this::addRenderableWidget, this::rebuildWidgets, this::changeMode);
    }

    private void changeMode() {
        var modes = Mode.values();
        settings.setMode(modes[(settings.mode().ordinal() + 1) % modes.length]);
        MusicDirector.get(minecraft).syncMode();
        rebuildWidgets();
    }

    private void doneButton() {
        int y = height - Theme.MARGIN - Theme.CONTROL_HEIGHT;
        boolean playlist = settings.mode() != Mode.VANILLA;
        int buttonWidth = playlist ? Math.min(Theme.DONE_WIDTH, (contentWidth - Theme.PADDING * 2 - Theme.GAP) / 2) : Theme.DONE_WIDTH;
        if (playlist) addRenderableWidget(AddSongsScreen.button(this, settings, width / 2 - buttonWidth - Theme.GAP / 2, y, buttonWidth));
        addRenderableWidget(Theme.button(playlist ? width / 2 + Theme.GAP / 2 : width / 2 - buttonWidth / 2,
                y, buttonWidth,
                Component.translatable("gui.done"), button -> onClose()));
    }

    @Override public void added() {
        if (basicPanel != null) basicPanel.refreshSongs();
    }

    private void updateTransport() {
        var director = MusicDirector.get(minecraft);
        if (settings.mode() == Mode.CUSTOM) {
            customPanel.update();
            return;
        }
        if (settings.mode() == xyz.pyrehaven.whostoppedthemusic.Mode.BASIC) {
            basicPanel.update(director);
            return;
        }
        controls.update();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, Theme.BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(left, Theme.MARGIN, left + contentWidth, height - Theme.MARGIN, Theme.CARD);
        graphics.fill(left, Theme.MARGIN, left + contentWidth, Theme.MARGIN + Theme.ACCENT, Theme.ORANGE);
        graphics.text(font, title, left + Theme.PADDING, Theme.MARGIN + Theme.GAP, Theme.TEXT);
        updateTransport();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public void tick() {
        if (settings.mode() == Mode.CUSTOM && customPanel != null) customPanel.refresh();
    }

    @Override
    public void removed() {
        minecraft.options.save();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
