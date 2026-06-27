package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class WhostmMusicHud {
    private static final int DISPLAY_TICKS = 100;
    private static final Component NO_SONG = Component.translatable("text.whostoppedthemusic.no_song");

    private static Component message;
    private static Component currentSong = NO_SONG;
    private static int ticksRemaining;
    private static String lastIdentity = "";

    private WhostmMusicHud() {
    }

    public static void announce(Identifier eventId, Sound resolvedSound) {
        Identifier resolvedId = resolvedSound == null ? null : resolvedSound.getLocation();
        Identifier identityId = resolvedId == null ? eventId : resolvedId;
        String identity = identityId == null ? "unknown" : identityId.toString();
        Component displayName = MusicCatalog.displayName(eventId, resolvedId);
        currentSong = displayName;
        if (identity.equals(lastIdentity) && ticksRemaining > 0) {
            return;
        }
        lastIdentity = identity;
        message = nowPlayingMessage();
        ticksRemaining = DISPLAY_TICKS;
    }

    public static Component currentSong() {
        return currentSong;
    }

    public static Component nowPlayingMessage() {
        return Component.translatable("text.whostoppedthemusic.now_playing", currentSong);
    }

    public static void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    public static void extractRenderState(GuiGraphicsExtractor graphics) {
        if (message == null || ticksRemaining <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int alpha = ticksRemaining < 20 ? Math.max(0, ticksRemaining * 235 / 20) : 235;
        int maxWidth = Math.min(graphics.guiWidth() - 18, 330);
        int boxWidth = Math.min(maxWidth, Math.max(128, minecraft.font.width(message) + 28));
        int x = graphics.guiWidth() / 2 - boxWidth / 2;
        int y = 8;

        graphics.nextStratum();
        drawNowPlayingToast(
                graphics,
                minecraft.font,
                x,
                y,
                boxWidth,
                24,
                message,
                alpha
        );
    }

    private static void drawNowPlayingToast(
            GuiGraphicsExtractor graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            Component text,
            int alpha
    ) {
        int a = Math.max(0, Math.min(255, alpha));
        int background = ARGB.color(a, 18, 17, 24);
        int border = ARGB.color(a, 178, 206, 232);
        int accent = ARGB.color(a, 231, 185, 102);
        int foreground = ARGB.color(Math.min(255, a + 20), 246, 241, 226);

        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, ARGB.color(a / 3, 0, 0, 0));
        graphics.fill(x, y, x + width, y + height, background);
        graphics.outline(x, y, width, height, border);
        graphics.fill(x + 4, y + 4, x + 7, y + height - 4, accent);
        graphics.centeredText(font, clipped(font, text.getString(), Math.max(10, width - 20)), x + width / 2 + 4, y + (height - font.lineHeight) / 2, foreground);
    }

    public static void drawCassette(
            GuiGraphicsExtractor graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            Component title,
            Component subtitle,
            int alpha
    ) {
        int a = Math.max(0, Math.min(255, alpha));
        int shell = ARGB.color(a, 30, 22, 36);
        int shellDark = ARGB.color(a, 10, 10, 16);
        int shellLight = ARGB.color(a, 76, 58, 94);
        int label = ARGB.color(a, 231, 185, 102);
        int labelDark = ARGB.color(a, 112, 78, 39);
        int reel = ARGB.color(a, 18, 17, 24);
        int reelAccent = ARGB.color(a, 178, 206, 232);
        int text = ARGB.color(Math.min(255, a + 20), 246, 241, 226);
        int muted = ARGB.color(Math.min(255, a + 10), 166, 188, 207);

        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, ARGB.color(a / 3, 0, 0, 0));
        graphics.fill(x, y, x + width, y + height, shell);
        graphics.outline(x, y, width, height, shellLight);
        graphics.outline(x + 2, y + 2, Math.max(1, width - 4), Math.max(1, height - 4), shellDark);

        int labelX = x + 10;
        int labelY = y + 6;
        int labelW = Math.max(24, width - 20);
        int labelH = Math.max(12, height / 3);
        graphics.fill(labelX, labelY, labelX + labelW, labelY + labelH, label);
        graphics.outline(labelX, labelY, labelW, labelH, labelDark);

        int titleMax = Math.max(10, labelW - 8);
        graphics.centeredText(font, clipped(font, title.getString(), titleMax), x + width / 2, labelY + 3, ARGB.color(a, 60, 38, 38));

        int centerY = y + height - 18;
        int reelSize = Math.max(12, Math.min(20, height - 22));
        int leftReelX = x + Math.max(14, width / 5 - reelSize / 2);
        int rightReelX = x + width - Math.max(14, width / 5) - reelSize / 2;
        drawReel(graphics, leftReelX, centerY - reelSize / 2, reelSize, reel, reelAccent, a);
        drawReel(graphics, rightReelX, centerY - reelSize / 2, reelSize, reel, reelAccent, a);

        int windowX = leftReelX + reelSize + 8;
        int windowW = Math.max(24, rightReelX - windowX - 8);
        graphics.fill(windowX, centerY - 8, windowX + windowW, centerY + 8, ARGB.color(a, 15, 14, 21));
        graphics.outline(windowX, centerY - 8, windowW, 16, ARGB.color(a, 94, 114, 136));
        graphics.centeredText(font, clipped(font, subtitle.getString(), Math.max(10, windowW - 8)), windowX + windowW / 2, centerY - 4, text);

        if (width >= 250 && height >= 40) {
            graphics.text(font, "MUSIC", x + 12, y + height - 11, muted);
            graphics.text(font, "PYREHAVEN", x + width - Math.min(70, width / 3), y + height - 11, muted);
        }
    }

    private static void drawReel(GuiGraphicsExtractor graphics, int x, int y, int size, int fill, int accent, int alpha) {
        graphics.fill(x, y, x + size, y + size, fill);
        graphics.outline(x, y, size, size, accent);
        int mid = size / 2;
        graphics.fill(x + mid - 1, y + 2, x + mid + 1, y + size - 2, ARGB.color(alpha, 70, 88, 108));
        graphics.fill(x + 2, y + mid - 1, x + size - 2, y + mid + 1, ARGB.color(alpha, 70, 88, 108));
        graphics.fill(x + mid - 2, y + mid - 2, x + mid + 2, y + mid + 2, ARGB.color(alpha, 220, 220, 210));
    }

    private static String clipped(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        if (width <= font.width("...")) {
            return font.plainSubstrByWidth(text, width);
        }
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
