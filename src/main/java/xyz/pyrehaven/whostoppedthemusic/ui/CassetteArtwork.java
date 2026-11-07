package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import net.minecraft.client.gui.components.Tooltip;

/** Main's shell, inset label and crossed reels, shared by the compact player and bare tape. */
public final class CassetteArtwork {
    private final OverflowText title = new OverflowText();
    private final OverflowText artist = new OverflowText();
    private final OverflowText status = new OverflowText();
    private Component description;

    public void draw(GuiGraphicsExtractor graphics, AbstractWidget widget, Track.Metadata metadata, Component state, boolean compact) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        Component full = metadata.description();
        if (!state.getString().isEmpty()) full = full.copy().append("\n").append(state);
        widget.setMessage(full);
        if (!full.equals(description)) {
            description = full;
            widget.setTooltip(Tooltip.create(full));
        }
        int x = widget.getX();
        int y = widget.getY();
        int w = widget.getWidth();
        int h = widget.getHeight();
        if (compact) {
            graphics.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
            graphics.fill(x, y, x + w, y + h, Theme.CARD);
            graphics.outline(x, y, w, h, widget.isHoveredOrFocused() ? Theme.GLOW : Theme.BLUE);
            x += 8;
            y += Theme.ACCENT;
            w -= 16;
            h -= Theme.ACCENT * 2;
        }
        graphics.fill(x, y, x + w, y + h, Theme.BLUE);
        graphics.outline(x, y, w, h, Theme.GLOW);
        graphics.outline(x + 2, y + 2, w - 4, h - 4, Theme.BACKGROUND);
        int labelX = x + Math.max(4, w / 20);
        int labelY = y + Theme.LABEL_PADDING;
        int labelW = w - (labelX - x) * 2;
        int textHeight = font.lineHeight * 2 + Theme.LABEL_LINE_GAP;
        int labelH = Math.max(textHeight + Theme.LABEL_PADDING * 2, h * 2 / 3);
        labelH = Math.min(labelH, h - Theme.LABEL_PADDING * 2);
        graphics.fill(labelX, labelY, labelX + labelW, labelY + labelH, Theme.CARD);
        graphics.fill(labelX, labelY, labelX + labelW, labelY + 2, Theme.ORANGE);
        int reelSize = w < Theme.TRACK_SIDE_CONTROLS_MIN ? Theme.SMALL_REEL
                : compact ? 10 : Math.clamp(Math.min(w / 7, labelH - 4), 6, 36);
        int reelY = labelY + (labelH - reelSize) / 2 + 1;
        int left = labelX + Math.max(3, labelW / 16);
        int right = labelX + labelW - Math.max(3, labelW / 16) - reelSize;
        reel(graphics, left, reelY, reelSize);
        reel(graphics, right, reelY, reelSize);
        int textY = labelY + Math.max(Theme.LABEL_PADDING, (labelH - textHeight) / 2);
        title.draw(graphics, font, metadata.title(), left + reelSize + Theme.LABEL_PADDING,
                right - Theme.LABEL_PADDING, textY, Theme.TEXT, true);
        artist.draw(graphics, font, metadata.artist(), left + reelSize + Theme.LABEL_PADDING,
                right - Theme.LABEL_PADDING, textY + font.lineHeight + Theme.LABEL_LINE_GAP, Theme.ARTIST, true);
        if (!state.getString().isEmpty()) {
            if (compact) status.draw(graphics, font, state, Theme.MARGIN, widget.getX() - Theme.GAP,
                    widget.getY() + (widget.getHeight() - font.lineHeight) / 2, Theme.ORANGE, true);
            else status.draw(graphics, font, state, x + Theme.GAP, x + w - Theme.GAP,
                    y + h - font.lineHeight - Theme.BORDER, Theme.ORANGE, true);
        }
        if (!compact && state.getString().isEmpty()) {
            int baseY = y + h - Math.max(8, h / 10);
            graphics.fill(x + w / 3, baseY, x + w * 2 / 3, baseY + 2, Theme.ORANGE);
            graphics.fill(x + 7, y + h - 7, x + 9, y + h - 5, Theme.TEXT);
            graphics.fill(x + w - 9, y + h - 7, x + w - 7, y + h - 5, Theme.TEXT);
        }
    }

    private static void reel(GuiGraphicsExtractor graphics, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, Theme.BACKGROUND);
        graphics.outline(x, y, size, size, Theme.GLOW);
        int middle = size / 2;
        graphics.fill(x + middle - 1, y + 2, x + middle + 1, y + size - 2, Theme.BLUE);
        graphics.fill(x + 2, y + middle - 1, x + size - 2, y + middle + 1, Theme.BLUE);
        graphics.fill(x + middle - 1, y + middle - 1, x + middle + 1, y + middle + 1, Theme.ORANGE);
    }
}
