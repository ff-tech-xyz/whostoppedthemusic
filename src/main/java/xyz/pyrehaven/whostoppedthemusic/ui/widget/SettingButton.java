package xyz.pyrehaven.whostoppedthemusic.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/** Right-click offers a captured value without first invoking the left-click mutation. */
public final class SettingButton extends Button {
    private final Runnable applyAll;
    private Component description;
    private boolean overflow;
    private final xyz.pyrehaven.whostoppedthemusic.ui.OverflowText line = new xyz.pyrehaven.whostoppedthemusic.ui.OverflowText();

    public void overflow() { overflow = true; }

    public SettingButton(int x, int y, int width, Component label, OnPress cycle, Runnable applyAll) {
        super(x, y, width, Theme.CONTROL_HEIGHT, label, cycle, supplier -> supplier.get());
        this.applyAll = applyAll;
        label(label);
    }

    public void label(Component value) {
        if (!value.equals(description)) {
            description = value;
            setMessage(value);
            setTooltip(Tooltip.create(value));
        }
    }

    @Override protected boolean isValidClickButton(MouseButtonInfo button) {
        return button.button() == InputConstants.MOUSE_BUTTON_LEFT || button.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) applyAll.run();
        else super.onClick(event, doubleClick);
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (overflow) Theme.scenarioContents(graphics, this, getMessage(), line, getX() + Theme.GAP, getRight() - Theme.GAP);
        else Theme.settingContents(graphics, this);
    }
}
