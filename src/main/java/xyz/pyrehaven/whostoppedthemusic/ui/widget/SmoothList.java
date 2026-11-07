package xyz.pyrehaven.whostoppedthemusic.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/** Wheel targets and frame-time motion for every clipped music/scenario list. */
public abstract class SmoothList<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E> {
    private double targetScroll;
    private long lastFrame;

    protected SmoothList(Minecraft client, int width, int height, int y, int rowHeight) {
        super(client, width, height, y, rowHeight);
    }

    protected abstract int wheelRowHeight();

    @Override public void setScrollAmount(double amount) {
        super.setScrollAmount(amount);
        targetScroll = scrollAmount();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        targetScroll = Math.clamp(targetScroll - vertical * wheelRowHeight() / 2.0, 0, maxScrollAmount());
        return true;
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = net.minecraft.util.Util.getMillis();
        double blend = 1 - Math.exp(-Math.min(now - lastFrame, Theme.SCROLL_MAX_FRAME_MS) / Theme.SCROLL_RESPONSE_MS);
        lastFrame = now;
        double distance = targetScroll - scrollAmount();
        super.setScrollAmount(Math.abs(distance) < Theme.SCROLL_EPSILON ? targetScroll : scrollAmount() + distance * blend);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
    }
}
