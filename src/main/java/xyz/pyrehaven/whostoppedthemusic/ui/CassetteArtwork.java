package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.music.Track;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import net.minecraft.client.gui.components.Tooltip;

/** One shell, spindle geometry and render-time reel clock for every player and library tape. */
public final class CassetteArtwork {
    private static Object reelSource;
    private static long sampledAt;
    private static long elapsed;
    private static boolean wasAdvancing;
    private final OverflowText title = new OverflowText();
    private final OverflowText artist = new OverflowText();
    private final OverflowText status = new OverflowText();
    private Component description;

    public void drawPlayer(GuiGraphicsExtractor graphics, AbstractWidget widget, MusicDirector director, boolean compact) {
        draw(graphics, widget, director.metadata(), director.playbackStatus(), compact,
                true, director.active(), director.active(), reelAngle(director));
    }

    public void drawTrack(GuiGraphicsExtractor graphics, AbstractWidget widget, Track track, MusicDirector director) {
        draw(graphics, widget, track.metadata(), track.playable() ? Component.empty()
                        : Component.translatable("whostoppedthemusic.cant_play_short"), false,
                false, true, director.playing(track), reelAngle(director));
    }

    /** Shared across widgets and screen rebuilds; transport remains owned by the director. */
    public static double reelAngle(MusicDirector director) {
        long now = System.nanoTime();
        Object source = director.active() ? director.current() : null;
        boolean advancing = director.advancing();
        if (source != reelSource) elapsed = 0;
        else if (advancing && wasAdvancing) elapsed = (elapsed + now - sampledAt) % Theme.REEL_PERIOD_NANOS;
        reelSource = source;
        sampledAt = now;
        wasAdvancing = advancing;
        return elapsed * (Math.PI * 2 / Theme.REEL_PERIOD_NANOS);
    }

    public record Geometry(int x, int y, int width, int height, int labelX, int labelY,
                           int labelWidth, int labelHeight, int left, int right, int reelY, int reelSize) {
        public int spindleY() { return reelY + reelSize / 2 - 1; }
        public int leftSpindleX() { return left + reelSize / 2 - 1; }
        public int rightSpindleX() { return right + reelSize / 2 - 1; }
    }

    public static Geometry geometry(int x, int y, int w, int h, boolean compact, int lineHeight) {
        if (compact) {
            x += 8;
            y += Theme.ACCENT;
            w -= 16;
            h -= Theme.ACCENT * 2;
        }
        int labelX = x + Math.max(4, w / 20);
        int labelY = y + Theme.LABEL_PADDING;
        int labelW = w - (labelX - x) * 2;
        int textHeight = lineHeight * 2 + Theme.LABEL_LINE_GAP;
        int labelH = Math.min(Math.max(textHeight + Theme.LABEL_PADDING * 2, h * 2 / 3), h - Theme.LABEL_PADDING * 2);
        int reelSize = w < Theme.TRACK_SIDE_CONTROLS_MIN ? Theme.SMALL_REEL
                : compact ? 10 : Math.clamp(Math.min(w / 7, labelH - 4), 6, 36);
        int reelY = labelY + (labelH - reelSize) / 2 + 1;
        int inset = Math.max(3, labelW / 16);
        return new Geometry(x, y, w, h, labelX, labelY, labelW, labelH,
                labelX + inset, labelX + labelW - inset - reelSize, reelY, reelSize);
    }

    private void draw(GuiGraphicsExtractor graphics, AbstractWidget widget, Track.Metadata metadata, Component state,
                      boolean compact, boolean player, boolean loaded, boolean current, double angle) {
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
        var geometry = geometry(x, y, w, h, compact, font.lineHeight);
        if (!loaded) {
            graphics.outline(x, y, w, h, widget.isHoveredOrFocused() ? Theme.GLOW : Theme.BLUE);
            spindles(graphics, geometry, Theme.SPINDLE);
            return;
        }
        if (compact) {
            graphics.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
            graphics.fill(x, y, x + w, y + h, Theme.CARD);
            graphics.outline(x, y, w, h, widget.isHoveredOrFocused() ? Theme.GLOW : Theme.BLUE);
        }
        x = geometry.x();
        y = geometry.y();
        w = geometry.width();
        h = geometry.height();
        graphics.fill(x, y, x + w, y + h, Theme.BLUE);
        graphics.outline(x, y, w, h, widget.isHoveredOrFocused() ? Theme.GLOW : current ? Theme.ORANGE : Theme.BLUE);
        graphics.outline(x + 2, y + 2, w - 4, h - 4, Theme.BACKGROUND);
        int labelX = geometry.labelX();
        int labelY = geometry.labelY();
        int labelW = geometry.labelWidth();
        int textHeight = font.lineHeight * 2 + Theme.LABEL_LINE_GAP;
        int labelH = geometry.labelHeight();
        graphics.fill(labelX, labelY, labelX + labelW, labelY + labelH, Theme.CARD);
        graphics.fill(labelX, labelY, labelX + labelW, labelY + 2, Theme.ORANGE);
        int reelSize = geometry.reelSize();
        int left = geometry.left();
        int right = geometry.right();
        reel(graphics, left, geometry.reelY(), reelSize, current ? angle : 0);
        reel(graphics, right, geometry.reelY(), reelSize, current ? angle : 0);
        spindles(graphics, geometry, player ? Theme.SPINDLE : Theme.BACKGROUND);
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

    private static void spindles(GuiGraphicsExtractor graphics, Geometry geometry, int color) {
        int y = geometry.spindleY();
        int left = geometry.leftSpindleX();
        int right = geometry.rightSpindleX();
        graphics.fill(left, y, left + 2, y + 2, color);
        graphics.fill(right, y, right + 2, y + 2, color);
    }

    private static void reel(GuiGraphicsExtractor graphics, int x, int y, int size, double angle) {
        graphics.fill(x, y, x + size, y + size, Theme.BACKGROUND);
        graphics.outline(x, y, size, size, Theme.GLOW);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + size / 2, y + size / 2).rotate((float) angle);
        int radius = Math.max(2, size / 2 - 1);
        for (int spoke = 0; spoke < 3; spoke++) {
            graphics.fill(0, -radius, 1, -1, Theme.GLOW);
            graphics.pose().rotate((float) (Math.PI * 2 / 3));
        }
        graphics.pose().popMatrix();
    }
}
