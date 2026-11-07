package xyz.pyrehaven.whostoppedthemusic.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.ui.OverflowText;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/** Right-click offers a captured value without first invoking the left-click mutation. */
public final class SettingButton extends Button {
    private final Runnable applyAll;
    private Component description;
    private final Component caption;
    private final Component help;
    private final OverflowText line = new OverflowText();

    public SettingButton(int x, int y, int width, Component caption, Component help, Component label, OnPress cycle, Runnable applyAll) {
        super(x, y, width, Theme.CONTROL_HEIGHT, label, cycle, supplier -> supplier.get());
        this.applyAll = applyAll;
        this.caption = caption;
        this.help = help;
        label(label);
    }

    public void label(Component value) {
        if (!value.equals(description)) {
            description = value;
            setMessage(value);
            var tooltip = value.copy();
            if (!help.getString().isEmpty()) tooltip.append("\n").append(help);
            setTooltip(Tooltip.create(tooltip));
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
        Theme.scenarioContents(graphics, this, caption, line, getX() + Theme.GAP, getRight() - Theme.GAP);
    }
}
