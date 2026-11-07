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

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var director = MusicDirector.get(client);
        artwork.drawPlayer(graphics, this, director);
    }
}
