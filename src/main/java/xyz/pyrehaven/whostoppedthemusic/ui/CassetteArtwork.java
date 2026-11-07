package xyz.pyrehaven.whostoppedthemusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.music.MusicDirector;
import xyz.pyrehaven.whostoppedthemusic.music.Track;

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

    public void drawPlayer(GuiGraphicsExtractor graphics, AbstractWidget widget, MusicDirector director) {
        draw(graphics, widget, director.metadata(), director.playbackStatus(),
                true, director.active(), director.active(), reelAngle(director));
        if (widget instanceof CassetteWidget && director.advancing()) {
            notes(graphics, widget.getX(), widget.getY() - Theme.GAP,
                    widget.getWidth() - Theme.PAUSE_NEXT_WIDTH - Theme.GAP, Theme.NOTE_RISE);
        }
    }

    public void drawTrack(GuiGraphicsExtractor graphics, AbstractWidget widget, Track track, MusicDirector director) {
        boolean current = director.playing(track);
        draw(graphics, widget, track.metadata(), track.playable() ? Component.empty()
                        : Component.translatable("whostoppedthemusic.cant_play_short"),
                current, true, current, reelAngle(director));
        if (current && director.displayAdvancing()) {
            var geometry = geometry(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            graphics.enableScissor(geometry.x(), geometry.labelY() + geometry.labelHeight(),
                    geometry.x() + geometry.width(), geometry.y() + geometry.height());
            notes(graphics, geometry.x(), geometry.y() + geometry.height() - Theme.GAP,
                    geometry.width(), Math.max(1, geometry.height() / 6));
            graphics.disableScissor();
        }
    }

    public void drawMissing(GuiGraphicsExtractor graphics, AbstractWidget widget, Component title) {
        draw(graphics, widget, new Track.Metadata(title, Component.empty()),
                Component.translatable("whostoppedthemusic.missing"), false, true, false, 0);
        graphics.fill(widget.getX(), widget.getY(), widget.getRight(), widget.getBottom(), Theme.MISSING_OVERLAY);
    }

    /** Closed library cases retain a spine and label at the existing row height. */
    public void drawCase(GuiGraphicsExtractor graphics, AbstractWidget widget, Track track, boolean included) {
        var font = Minecraft.getInstance().font;
        int x = widget.getX();
        int y = widget.getY();
        int w = widget.getWidth();
        int h = widget.getHeight();
        var full = track.metadata().description();
        var director = MusicDirector.get(Minecraft.getInstance());
        boolean current = director.playing(track);
        double angle = reelAngle(director);
        if (included) full = full.copy().append(Component.translatable("whostoppedthemusic.in_list"));
        widget.setMessage(full);
        widget.setTooltip(Tooltip.create(full));
        graphics.fill(x, y, x + w, y + h, Theme.BLUE);
        graphics.outline(x, y, w, h, widget.isHoveredOrFocused() ? Theme.GLOW : Theme.BLUE);
        graphics.fill(x + Theme.CASE_SPINE, y + Theme.ACCENT, x + w - Theme.ACCENT, y + h - Theme.ACCENT, Theme.CARD);
        graphics.fill(x + Theme.ACCENT, y + Theme.ACCENT, x + Theme.CASE_SPINE - Theme.BORDER,
                y + h - Theme.ACCENT, included ? Theme.GLOW : Theme.ORANGE);
        int playbackWidth = Theme.SMALL_REEL * 2 + Theme.GAP * 5;
        if (current) {
            for (int offset : new int[]{Theme.SMALL_REEL + Theme.GAP, Theme.SMALL_REEL * 2 + Theme.GAP * 2}) {
                int reelX = x + w - offset;
                int reelY = y + (h - Theme.SMALL_REEL) / 2;
                reel(graphics, reelX, reelY, Theme.SMALL_REEL, angle);
                graphics.fill(reelX + Theme.SMALL_REEL / 2 - 1, reelY + Theme.SMALL_REEL / 2 - 1,
                        reelX + Theme.SMALL_REEL / 2 + 1, reelY + Theme.SMALL_REEL / 2 + 1, Theme.SPINDLE);
            }
            if (director.displayAdvancing()) notes(graphics, x + w - playbackWidth, y + h - Theme.ACCENT,
                    Theme.GAP * 2, Math.max(1, h / 3));
        } else graphics.fill(x + w - Theme.GAP, y + h / 3, x + w - Theme.ACCENT, y + h * 2 / 3, Theme.BLUE);
        title.draw(graphics, font, track.title(), x + Theme.CASE_SPINE + Theme.LABEL_PADDING,
                x + w - (current ? playbackWidth : Theme.GAP) - Theme.LABEL_PADDING, y + (h - font.lineHeight) / 2,
                included ? Theme.ARTIST : Theme.TEXT, false, false, widget.isHovered());
    }

    /** Held drag cassettes stay detached from playback. */
    public void drawLoose(GuiGraphicsExtractor graphics, AbstractWidget widget, Track track) {
        draw(graphics, widget, track.metadata(), Component.empty(), false, true, false, 0);
    }

    /** Shared across widgets and screen rebuilds; transport remains owned by the director. */
    public static double reelAngle(MusicDirector director) {
        long now = System.nanoTime();
        Object source = director.displaySource();
        boolean advancing = director.displayAdvancing();
        if (source != reelSource) elapsed = 0;
        else if (advancing && wasAdvancing) elapsed += now - sampledAt;
        reelSource = source;
        sampledAt = now;
        wasAdvancing = advancing;
        return (elapsed % Theme.REEL_PERIOD_NANOS) * (Math.PI * 2 / Theme.REEL_PERIOD_NANOS);
    }

    public record Geometry(int x, int y, int width, int height, int labelX, int labelY,
                           int labelWidth, int labelHeight, int left, int right, int reelY, int reelSize) {
        public int spindleY() { return reelY + reelSize / 2 - 1; }
        public int leftSpindleX() { return left + reelSize / 2 - 1; }
        public int rightSpindleX() { return right + reelSize / 2 - 1; }
    }

    public record Size(int width, int height) { }

    /** All open tapes use one native size, scaled down only when their slot cannot fit it. */
    public static Size size(int availableWidth, int availableHeight) {
        int height = Math.max(1, Math.min(Theme.CASSETTE_HEIGHT,
                Math.min(availableHeight, availableWidth * Theme.CASSETTE_HEIGHT / Theme.CASSETTE_WIDTH)));
        int width = Math.min(Math.max(1, availableWidth),
                (height * Theme.CASSETTE_WIDTH + Theme.CASSETTE_HEIGHT - 1) / Theme.CASSETTE_HEIGHT);
        return new Size(width, height);
    }

    public static Geometry geometry(int x, int y, int w, int h) {
        var size = size(w, h);
        x += (w - size.width()) / 2;
        y += (h - size.height()) / 2;
        w = size.width();
        h = size.height();
        int labelX = x + Math.max(4, w / 20);
        int labelY = y + Theme.LABEL_PADDING;
        int labelW = w - (labelX - x) * 2;
        int labelH = h * 2 / 3;
        int reelSize = Math.max(Theme.SMALL_REEL, w / Theme.REEL_WIDTH_DIVISOR);
        int reelY = labelY + (labelH - reelSize) / 2 + 1;
        int inset = Math.max(3, labelW / 16);
        return new Geometry(x, y, w, h, labelX, labelY, labelW, labelH,
                labelX + inset, labelX + labelW - inset - reelSize, reelY, reelSize);
    }

    private void draw(GuiGraphicsExtractor graphics, AbstractWidget widget, Track.Metadata metadata, Component state,
                      boolean player, boolean loaded, boolean current, double angle) {
        var font = Minecraft.getInstance().font;
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
        var geometry = geometry(x, y, w, h);
        if (!loaded) {
            graphics.outline(geometry.x(), geometry.y(), geometry.width(), geometry.height(),
                    widget.isHoveredOrFocused() ? Theme.GLOW : Theme.BLUE);
            spindles(graphics, geometry, Theme.SPINDLE);
            return;
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
        int labelH = geometry.labelHeight();
        boolean showArtist = labelH >= font.lineHeight * 2 + Theme.LABEL_LINE_GAP + Theme.LABEL_PADDING * 2;
        int textHeight = showArtist ? font.lineHeight * 2 + Theme.LABEL_LINE_GAP : font.lineHeight;
        graphics.fill(labelX, labelY, labelX + labelW, labelY + labelH, Theme.CARD);
        graphics.fill(labelX, labelY, labelX + labelW, labelY + 2, Theme.ORANGE);
        int reelSize = geometry.reelSize();
        int left = geometry.left();
        int right = geometry.right();
        reel(graphics, left, geometry.reelY(), reelSize, current ? angle : 0);
        reel(graphics, right, geometry.reelY(), reelSize, current ? angle : 0);
        spindles(graphics, geometry, player ? Theme.SPINDLE : Theme.BACKGROUND);
        int textY = labelY + Math.max(Theme.LABEL_PADDING, (labelH - textHeight) / 2);
        graphics.enableScissor(labelX, labelY + Theme.ACCENT, labelX + labelW, labelY + labelH);
        title.draw(graphics, font, metadata.title(), left + reelSize + Theme.LABEL_PADDING,
                right - Theme.LABEL_PADDING, textY, Theme.TEXT, true, false, widget.isHovered());
        if (showArtist) artist.draw(graphics, font, metadata.artist(), left + reelSize + Theme.LABEL_PADDING,
                right - Theme.LABEL_PADDING, textY + font.lineHeight + Theme.LABEL_LINE_GAP, Theme.ARTIST, true, false, widget.isHovered());
        graphics.disableScissor();
        if (!state.getString().isEmpty() && h - labelH >= font.lineHeight + Theme.LABEL_PADDING) {
            status.draw(graphics, font, state, x + Theme.GAP, x + w - Theme.GAP,
                    y + h - font.lineHeight - Theme.BORDER, Theme.ORANGE, true, false, widget.isHovered());
        }
        if (state.getString().isEmpty()) {
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

    /** Notes share the reel clock, so duplicate views never invent their own playback state. */
    private static void notes(GuiGraphicsExtractor graphics, int left, int bottom, int width, int rise) {
        double time = (elapsed % Theme.NOTE_PERIOD_NANOS) / (double) Theme.NOTE_PERIOD_NANOS;
        for (int i = 0; i < Theme.NOTE_COUNT; i++) {
            double phase = (time + i / (double) Theme.NOTE_COUNT) % 1;
            int x = left + width * (i + 1) / (Theme.NOTE_COUNT + 1)
                    + (int) (Math.sin(phase * Math.PI * 2) * Theme.ACCENT);
            int y = bottom - (int) (phase * rise);
            int color = i % 2 == 0 ? Theme.GLOW : Theme.ORANGE;
            graphics.fill(x, y, x + Theme.ACCENT, y + Theme.ACCENT, color);
            graphics.fill(x + Theme.ACCENT, y - Theme.GAP, x + Theme.ACCENT + Theme.BORDER, y + Theme.ACCENT, color);
            graphics.fill(x + Theme.ACCENT, y - Theme.GAP, x + Theme.GAP, y - Theme.GAP + Theme.ACCENT, color);
        }
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
