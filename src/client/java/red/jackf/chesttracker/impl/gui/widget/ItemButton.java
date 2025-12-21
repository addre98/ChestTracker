package red.jackf.chesttracker.impl.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import red.jackf.chesttracker.impl.util.GuiUtil;

public class ItemButton extends Button {
    public static final int SIZE = 20;
    private static final WidgetSprites TEXTURE = GuiUtil.twoSprite("memory_key_background/background");
    private final ItemStack stack;
    private final Background background;
    private boolean highlighted = false;

    public ItemButton(ItemStack stack, int x, int y, OnPress onPress, Background background) {
        super(x, y, SIZE, SIZE, CommonComponents.EMPTY, onPress, Button.DEFAULT_NARRATION);
        this.stack = stack;
        this.background = background;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    @Override
    protected void renderContents(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        switch (background) {
            case VANILLA -> super.renderWidget(graphics, mouseX, mouseY, partialTick);
            case CUSTOM -> {
                Identifier texture = this.highlighted || this.isHovered() ? TEXTURE.enabledFocused() : TEXTURE.enabled();
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, getX(), getY(), SIZE, SIZE);
            }
        }
        graphics.renderItem(stack, this.getX() + 2, this.getY() + 2);
    }

    public enum Background {
        NONE,
        VANILLA,
        CUSTOM
    }
}
