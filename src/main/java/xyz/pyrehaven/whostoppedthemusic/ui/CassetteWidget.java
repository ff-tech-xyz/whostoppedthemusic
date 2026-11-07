package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.Settings;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;

public final class CassetteWidget extends Button {
    private final Minecraft client;
    private final CassetteArtwork artwork = new CassetteArtwork();

    public CassetteWidget(Minecraft client, PauseScreen parent, Settings settings) {
        super(parent.width / 2 - cassetteWidth(parent.width) / 2, 6,
                cassetteWidth(parent.width), Theme.PAUSE_TAPE_HEIGHT,
                Component.translatable("whostoppedthemusic.cassette.open"),
                button -> client.gui.setScreen(new MusicScreen(parent, settings)), DEFAULT_NARRATION);
        this.client = client;
        setTooltip(Tooltip.create(getMessage()));
    }

    private static int cassetteWidth(int width) {
        return Math.min(300, Math.max(180, width - 144));
    }

    public Component status() {
        return MusicDirector.get(client).status();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var director = MusicDirector.get(client);
        artwork.draw(graphics, this, director.metadata(), director.playbackStatus(), true);
    }
}
