package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import xyz.pyrehaven.whostoppedthemusic.Settings;

public final class MusicScreen extends Screen {
    private final Screen parent;
    private final Settings settings;
    private int left;
    private int contentWidth;
    private int bodyY;

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
        int columns = available >= Theme.TWO_COLUMN_WIDTH ? 2 : 1;
        int columnWidth = (available - Theme.GAP * (columns - 1)) / columns;
        OptionInstance<?>[] options = {
            minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER),
            minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC),
            minecraft.options.musicFrequency(), minecraft.options.musicToast()
        };
        int y = Theme.HEADER_HEIGHT;
        for (int i = 0; i < options.length; i++) {
            addRenderableWidget(options[i].createButton(minecraft.options,
                    x + (i % columns) * (columnWidth + Theme.GAP), y + (i / columns) * Theme.ROW, columnWidth));
        }
        y += ((options.length + columns - 1) / columns) * Theme.ROW;
        var jukebox = Theme.button(x, y, available, jukeboxLabel(), button -> {
            settings.setJukebox(settings.jukebox().next());
            button.setMessage(jukeboxLabel());
        });
        jukebox.setTooltip(Tooltip.create(Component.translatable("whostoppedthemusic.jukebox.saved_only")));
        addRenderableWidget(jukebox);
        bodyY = y + Theme.ROW + Theme.GAP;
        addRenderableWidget(Theme.button(width / 2 - Theme.DONE_WIDTH / 2,
                height - Theme.MARGIN - Theme.CONTROL_HEIGHT, Theme.DONE_WIDTH,
                Component.translatable("gui.done"), button -> onClose()));
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
        graphics.text(font, Component.translatable("whostoppedthemusic.mode.vanilla"),
                left + Theme.PADDING, Theme.MARGIN + Theme.GAP + Theme.LINE, Theme.GLOW);
        graphics.textWithWordWrap(font, Component.translatable("whostoppedthemusic.vanilla.description"),
                left + Theme.PADDING, bodyY, contentWidth - Theme.PADDING * 2, Theme.TEXT);
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
