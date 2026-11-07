package xyz.pyrehaven.whostoppedthemusic.ui.custom;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/** Modal confirmation shared by main and editor settings; only Yes runs the captured action. */
public final class ApplyToAllPopup extends Screen {
    private final Screen parent;
    private final int anchorX;
    private final int anchorY;
    private final Runnable apply;
    private int x;
    private int y;
    private int popupWidth;

    public ApplyToAllPopup(Screen parent, AbstractWidget anchor, Runnable apply) {
        super(Component.translatable("whostoppedthemusic.apply_all"));
        this.parent = parent;
        anchorX = anchor.getX();
        anchorY = anchor.getBottom() + Theme.GAP;
        this.apply = apply;
    }

    @Override protected void init() {
        popupWidth = Math.min(Theme.POPUP_WIDTH, width - Theme.MARGIN * 2);
        x = Math.clamp(anchorX, Theme.MARGIN, width - popupWidth - Theme.MARGIN);
        y = Math.clamp(anchorY, Theme.MARGIN, height - Theme.POPUP_HEIGHT - Theme.MARGIN);
        int half = (popupWidth - Theme.GAP * 3) / 2;
        int buttonsY = y + Theme.POPUP_HEIGHT - Theme.GAP - Theme.CONTROL_HEIGHT;
        addRenderableWidget(Theme.button(x + Theme.GAP, buttonsY, half, Component.translatable("gui.yes"), button -> {
            apply.run();
            onClose();
        }));
        addRenderableWidget(Theme.button(x + Theme.GAP * 2 + half, buttonsY, half,
                Component.translatable("gui.no"), button -> onClose()));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        parent.extractRenderState(graphics, -1, -1, delta);
        graphics.fill(x, y, x + popupWidth, y + Theme.POPUP_HEIGHT, Theme.SCENARIO_PANEL);
        graphics.outline(x, y, popupWidth, Theme.POPUP_HEIGHT, Theme.SCENARIO_TEXT);
        graphics.text(font, title, x + Theme.GAP, y + Theme.GAP, Theme.SCENARIO_TEXT, false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() < x || event.x() >= x + popupWidth || event.y() < y || event.y() >= y + Theme.POPUP_HEIGHT) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
