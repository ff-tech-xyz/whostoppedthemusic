package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

public final class CassetteWidget extends Button {
    private final Minecraft client;
    private final CassetteArtwork artwork = new CassetteArtwork();
    private long notesStarted;

    public CassetteWidget(Minecraft client, Screen parent, Settings settings) {
        super(Theme.MARGIN, parent.height - Theme.MENU_FOOTER - cassetteSize(parent.width).height(),
                cassetteSize(parent.width).width(), cassetteSize(parent.width).height(),
                Component.translatable("whostoppedthemusic.cassette.open"),
                button -> client.gui.setScreen(new MusicScreen(parent, settings)), DEFAULT_NARRATION);
        this.client = client;
        setTooltip(Tooltip.create(getMessage()));
    }

    private static CassetteArtwork.Size cassetteSize(int width) {
        return CassetteArtwork.size((width - Theme.MENU_BUTTON_WIDTH) / 2 - Theme.MARGIN * 2,
                Theme.CASSETTE_HEIGHT);
    }

    public Component status() {
        return MusicDirector.get(client).status();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var director = MusicDirector.get(client);
        artwork.drawPlayer(graphics, this, director);
        if (!director.advancing()) {
            notesStarted = 0;
            return;
        }
        long now = System.nanoTime();
        if (notesStarted == 0) notesStarted = now;
        double time = (now - notesStarted) / (double) Theme.NOTE_PERIOD_NANOS;
        for (int i = 0; i < Theme.NOTE_COUNT; i++) {
            double phase = (time + i / (double) Theme.NOTE_COUNT) % 1;
            int x = getX() + (getWidth() - Theme.PAUSE_NEXT_WIDTH - Theme.GAP) * (i + 1) / (Theme.NOTE_COUNT + 1)
                    + (int) (Math.sin(phase * Math.PI * 2) * Theme.ACCENT);
            int y = getY() - Theme.GAP - (int) (phase * Theme.NOTE_RISE);
            int color = i % 2 == 0 ? Theme.GLOW : Theme.ORANGE;
            graphics.fill(x, y, x + Theme.ACCENT, y + Theme.ACCENT, color);
            graphics.fill(x + Theme.ACCENT, y - Theme.GAP, x + Theme.ACCENT + Theme.BORDER, y + Theme.ACCENT, color);
            graphics.fill(x + Theme.ACCENT, y - Theme.GAP, x + Theme.GAP, y - Theme.GAP + Theme.ACCENT, color);
        }
    }
}
