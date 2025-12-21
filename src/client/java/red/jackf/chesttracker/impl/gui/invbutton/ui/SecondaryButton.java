package red.jackf.chesttracker.impl.gui.invbutton.ui;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class SecondaryButton extends AbstractWidget {
    private static final long TWEEN_TIME = 60;
    private final WidgetSprites sprites;
    protected Runnable onClick;
    public long startTweenTime = -1;
    public int startX = 0;
    public int startY = 0;
    public int buttonIndex = 0;

    public SecondaryButton(WidgetSprites sprites, Component message, Runnable onClick) {
        super(0, 0, InventoryButton.SIZE, InventoryButton.SIZE, message);
        this.onClick = onClick;
        this.setTooltip(Tooltip.create(message));
        this.sprites = sprites;
        this.visible = false;
    }

    protected void setButtonIndex(int index) {
        this.buttonIndex = index;
    }

    protected WidgetSprites getSprites() {
        return this.sprites;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Identifier texture = getSprites().get(this.isActive(), this.isHoveredOrFocused());

        long tweenTime = this.buttonIndex * TWEEN_TIME;
        float factor = Mth.clamp((float) (Util.getMillis() - startTweenTime) / tweenTime, 0, 1);
        int x = Mth.lerpDiscrete(factor, this.startX - 1, this.getX() - 1);
        int y = Mth.lerpDiscrete(factor, this.startY - 1, this.getY() - 1);

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, InventoryButton.IMAGE_SIZE, InventoryButton.IMAGE_SIZE);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isDoubleClick) {
        onClick.run();
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        // noop
    }

    public void setVisible(boolean shouldShow, int startX, int startY) {
        this.visible = shouldShow;
        if (shouldShow) {
            if (this.startTweenTime == -1) {
                this.startTweenTime = Util.getMillis();
                this.startX = startX;
                this.startY = startY;
            }
        } else {
            this.startTweenTime = -1;
        }
    }
}
