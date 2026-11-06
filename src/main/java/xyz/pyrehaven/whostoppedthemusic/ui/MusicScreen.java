package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

public final class MusicScreen extends Screen {
    private final Screen parent;
    private final Settings settings;
    private int left;
    private int contentWidth;
    private Button previous;
    private Button pause;
    private Button next;

    public MusicScreen(Screen parent, Settings settings) {
        super(Component.translatable("whostoppedthemusic.screen.title"));
        this.parent = parent;
        this.settings = settings;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(Theme.MAX_WIDTH, width - Theme.MARGIN * 2);
        left = (width - contentWidth) / 2;
        int x = left + Theme.PADDING;
        int available = contentWidth - Theme.PADDING * 2;
        int controlsWidth = available < 360 ? 122 : 170;
        int tapeWidth = available - controlsWidth - 30 - Theme.GAP * 3;
        int controlsX = x + tapeWidth + Theme.GAP * 2;
        int y = Math.max(44, (height - 120) / 2);
        var director = MusicDirector.get(minecraft);
        int tapeHeight = Math.min(160, Math.max(80, tapeWidth * 3 / 5));
        var tape = new Button(x, y + (120 - tapeHeight) / 2, tapeWidth, tapeHeight,
                director.status(), button -> { }, supplier -> supplier.get()) {
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                setMessage(director.status());
                CassetteArtwork.draw(graphics, this, getMessage(), false);
            }
        };
        addRenderableWidget(tape);
        addRenderableWidget(Theme.button(controlsX, y, controlsWidth,
                Component.translatable("whostoppedthemusic.mode.vanilla"), button -> { }));
        addRenderableWidget(Theme.button(controlsX, y + Theme.ROW, controlsWidth, frequencyLabel(), button -> {
            var option = minecraft.options.musicFrequency();
            var values = net.minecraft.client.sounds.MusicManager.MusicFrequency.values();
            option.set(values[(option.get().ordinal() + 1) % values.length]);
            button.setMessage(frequencyLabel());
        }));
        var jukebox = Theme.button(controlsX, y + Theme.ROW * 2, controlsWidth, jukeboxLabel(), button -> {
            settings.setJukebox(settings.jukebox().next());
            button.setMessage(jukeboxLabel());
        });
        jukebox.setTooltip(Tooltip.create(Component.translatable("whostoppedthemusic.jukebox.saved_only")));
        addRenderableWidget(jukebox);
        int transportWidth = (controlsWidth - Theme.GAP * 2) / 3;
        int transportY = y + Theme.ROW * 3 + Theme.GAP;
        previous = transport(controlsX, transportY, transportWidth, "<", "previous", director::previous);
        pause = transport(controlsX + transportWidth + Theme.GAP, transportY, transportWidth,
                "||", "pause", director::togglePause);
        next = transport(controlsX + (transportWidth + Theme.GAP) * 2, transportY, transportWidth,
                ">", "next", director::next);
        addRenderableWidget(new MusicVolumeSlider(minecraft, controlsX + controlsWidth + Theme.GAP, y, 120));
        updateTransport();
        addRenderableWidget(Theme.button(width / 2 - Theme.DONE_WIDTH / 2,
                height - Theme.MARGIN - Theme.CONTROL_HEIGHT, Theme.DONE_WIDTH,
                Component.translatable("gui.done"), button -> onClose()));
    }

    private Button transport(int x, int y, int width, String icon, String action, Runnable press) {
        var button = Theme.button(x, y, width, Component.literal(icon), ignored -> {
            press.run();
            updateTransport();
        });
        button.setTooltip(Tooltip.create(Component.translatable("whostoppedthemusic." + action)));
        return addRenderableWidget(button);
    }

    private void updateTransport() {
        var director = MusicDirector.get(minecraft);
        previous.active = director.canPrevious();
        pause.active = director.active();
        pause.setMessage(Component.literal(director.paused() ? ">" : "||"));
        pause.setTooltip(Tooltip.create(Component.translatable(
                "whostoppedthemusic." + (director.paused() ? "resume" : "pause"))));
        next.active = director.canNext();
    }

    private Component frequencyLabel() {
        return Component.translatable("whostoppedthemusic.frequency", minecraft.options.musicFrequency().get().caption());
    }

    private Component jukeboxLabel() {
        return Component.translatable("whostoppedthemusic.jukebox",
                Component.translatable("whostoppedthemusic.jukebox." + settings.jukebox().name().toLowerCase(java.util.Locale.ROOT)));
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

    @Override
    public void removed() {
        minecraft.options.save();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
