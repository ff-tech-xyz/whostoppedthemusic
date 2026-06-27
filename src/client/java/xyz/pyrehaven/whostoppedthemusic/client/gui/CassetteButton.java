package xyz.pyrehaven.whostoppedthemusic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.client.WhostmMusicHud;

import java.util.function.Supplier;

public final class CassetteButton extends AbstractWidget {
    private final Supplier<Component> subtitle;
    private final Runnable onPress;

    public CassetteButton(int x, int y, int width, int height, Component title, Supplier<Component> subtitle, Runnable onPress) {
        super(x, y, width, height, title);
        this.subtitle = subtitle;
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int alpha = this.active ? (this.isHoveredOrFocused() ? 255 : 235) : 150;
        WhostmMusicHud.drawCassette(
                graphics,
                Minecraft.getInstance().font,
                this.getX(),
                this.getY(),
                this.getWidth(),
                this.getHeight(),
                this.getMessage(),
                this.subtitle.get(),
                alpha
        );
        if (this.isHoveredOrFocused() && this.active) {
            graphics.outline(this.getX() - 1, this.getY() - 1, this.getWidth() + 2, this.getHeight() + 2, 0xFFE6C16F);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) {
            return;
        }
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        this.onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
        narrationElementOutput.add(NarratedElementType.HINT, this.subtitle.get());
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
