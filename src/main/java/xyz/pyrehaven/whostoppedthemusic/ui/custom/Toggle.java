package xyz.pyrehaven.whostoppedthemusic.ui.custom;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/** One live pill toggle for scenario rows and editor settings. */
public final class Toggle extends Button {
    private final BooleanSupplier value;
    private final Component label;
    private final xyz.pyrehaven.whostoppedthemusic.ui.OverflowText line = new xyz.pyrehaven.whostoppedthemusic.ui.OverflowText();

    public Toggle(Component label, BooleanSupplier value, Runnable change) {
        super(0, 0, Theme.TOGGLE_WIDTH, Theme.CONTROL_HEIGHT, label, button -> change.run(), supplier -> supplier.get());
        this.label = label;
        this.value = value;
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean on = value.getAsBoolean();
        var description = Component.translatable("whostoppedthemusic.scenario.active", label,
                Component.translatable(on ? "options.on" : "options.off"));
        setMessage(description);
        setTooltip(Tooltip.create(description));
        if (getWidth() > Theme.TOGGLE_WIDTH) {
            Theme.scenarioContents(graphics, this, label, line, getX() + Theme.GAP, getRight() - Theme.TOGGLE_WIDTH - Theme.GAP);
        }
        Theme.toggle(graphics, getRight() - Theme.TOGGLE_WIDTH, getY() + (getHeight() - Theme.TOGGLE_HEIGHT) / 2, on, isHoveredOrFocused());
    }
}
