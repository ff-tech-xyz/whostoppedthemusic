package xyz.pyrehaven.whostoppedthemusic.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xyz.pyrehaven.whostoppedthemusic.ui.Theme;

/** Integer view of the focused scenario entry; Settings remains the value owner. */
public final class WeightSlider extends AbstractWidget {
    private final IntSupplier weight;
    private final IntSupplier total;
    private final IntConsumer change;

    public WeightSlider(int x, int y, int width, IntSupplier weight, IntSupplier total, IntConsumer change) {
        super(x, y, width, Theme.CONTROL_HEIGHT, Component.empty());
        this.weight = weight;
        this.total = total;
        this.change = change;
    }

    private void setFromMouse(double x) {
        change.accept(Math.clamp(1 + (int) Math.round((x - getX() - Theme.GAP) * 99 / (getWidth() - Theme.GAP * 2)), 1, 100));
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { setFromMouse(event.x()); }
    @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { setFromMouse(event.x()); }
    @Override public boolean keyPressed(KeyEvent event) {
        int value = switch (event.key()) {
            case InputConstants.KEY_LEFT, InputConstants.KEY_DOWN -> weight.getAsInt() - 1;
            case InputConstants.KEY_RIGHT, InputConstants.KEY_UP -> weight.getAsInt() + 1;
            case InputConstants.KEY_HOME -> 1;
            case InputConstants.KEY_END -> 100;
            default -> -1;
        };
        if (value == -1) return false;
        change.accept(Math.clamp(value, 1, 100));
        return true;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        setMessage(Component.translatable("whostoppedthemusic.weight_chance", weight.getAsInt(), total.getAsInt(),
                String.format(Locale.ROOT, "%.1f", total.getAsInt() == 0 ? 0 : weight.getAsInt() * 100.0 / total.getAsInt())));
        setTooltip(Tooltip.create(getMessage()));
        int center = getY() + getHeight() / 2;
        int knob = getX() + Theme.GAP + (weight.getAsInt() - 1) * (getWidth() - Theme.GAP * 2) / 99;
        graphics.fill(getX() + Theme.GAP, center - Theme.BORDER, getRight() - Theme.GAP, center + Theme.BORDER, Theme.BLUE);
        graphics.fill(knob - Theme.ACCENT, getY() + Theme.BORDER, knob + Theme.ACCENT, getBottom() - Theme.BORDER,
                active ? isHoveredOrFocused() ? Theme.TEXT : Theme.ORANGE : Theme.BLUE);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
