package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import xyz.pyrehaven.whostoppedthemusic.music.OverflowMotion;

public final class OverflowText {
    private final OverflowMotion motion = new OverflowMotion();

    public void draw(GuiGraphicsExtractor graphics, Font font, Component text,
                     int left, int right, int y, int color, boolean center, boolean shadow, boolean hovered) {
        if (right <= left) return;
        int width = font.width(text);
        double offset = motion.offset(text.getString(), width - (right - left), Util.getMillis(), hovered);
        float x = left + (center && width <= right - left ? (right - left - width) / 2f : 0) - (float) offset;
        graphics.enableScissor(left, y - Theme.BORDER, right, y + font.lineHeight + Theme.BORDER);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.text(font, text, 0, 0, color, shadow);
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }
}
