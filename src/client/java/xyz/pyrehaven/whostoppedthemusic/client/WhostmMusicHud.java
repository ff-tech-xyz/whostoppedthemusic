package xyz.pyrehaven.whostoppedthemusic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class WhostmMusicHud {
    private static final int DISPLAY_TICKS = 100;
    private static Component message;
    private static int ticksRemaining;
    private static String lastIdentity = "";

    private WhostmMusicHud() {
    }

    public static void announce(Identifier eventId, Sound resolvedSound) {
        Identifier resolvedId = resolvedSound == null ? null : resolvedSound.getLocation();
        Identifier identityId = resolvedId == null ? eventId : resolvedId;
        String identity = identityId == null ? "unknown" : identityId.toString();
        if (identity.equals(lastIdentity) && ticksRemaining > 0) {
            return;
        }
        lastIdentity = identity;
        message = Component.literal("Now playing: ").append(MusicCatalog.displayName(eventId, resolvedId));
        ticksRemaining = DISPLAY_TICKS;
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
        int alpha = ticksRemaining < 20 ? Math.max(0, ticksRemaining * 220 / 20) : 220;
        int centerX = graphics.guiWidth() / 2;
        int y = 8;
        int textWidth = minecraft.font.width(message);
        int boxWidth = Math.min(graphics.guiWidth() - 16, textWidth + 18);
        int boxHeight = minecraft.font.lineHeight + 8;
        int x1 = centerX - boxWidth / 2;
        int y1 = y - 4;

        graphics.nextStratum();
        graphics.fill(x1, y1, x1 + boxWidth, y1 + boxHeight, ARGB.color(alpha, 0, 0, 0));
        graphics.centeredText(minecraft.font, message, centerX, y, ARGB.color(Math.min(255, alpha + 35), 255, 255, 255));
    }
}
