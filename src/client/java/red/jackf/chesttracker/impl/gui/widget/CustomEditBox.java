package red.jackf.chesttracker.impl.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import red.jackf.chesttracker.impl.util.GuiUtil;

public class CustomEditBox extends EditBox {
    public static final Component SEARCH_MESSAGE = Component.translatable("gui.recipebook.search_hint");

    public CustomEditBox(Font font, int x, int y, int width, int height, @Nullable EditBox editBox, Component message) {
        super(font, x, y, width, height, editBox, message);
        this.setBordered(false);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GuiUtil.SEARCH_BAR_SPRITE, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        graphics.pose().translate(2.0f, 2.0f);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().translate(-2.0f, -2.0f);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        if (isMouseOver(event.x(), event.y()) && event.button() == GLFW.GLFW_MOUSE_BUTTON_2) {
            this.setValue("");
            return true;
        }
        return super.mouseClicked(event, consumed);
    }
}
