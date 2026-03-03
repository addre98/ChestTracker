package red.jackf.chesttracker.impl.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import red.jackf.chesttracker.impl.util.GuiUtil;

import java.util.function.Consumer;

public class VerticalScrollWidget extends AbstractWidget {
    private static final Identifier BACKGROUND = GuiUtil.sprite("nine_patch/scroll_bar");
    private static final WidgetSprites HANDLE_TEXTURE = new WidgetSprites(GuiUtil.sprite("widgets/scroll_bar/handle"),
                                                                          GuiUtil.sprite("widgets/scroll_bar/handle_disabled"),
                                                                          GuiUtil.sprite("widgets/scroll_bar/handle"),
                                                                          GuiUtil.sprite("widgets/scroll_bar/handle_disabled"));
    private static final int HANDLE_WIDTH = 10;
    private static final int HANDLE_HEIGHT = 11;
    private static final int INSET = 1;

    public static final int BAR_WIDTH = 2 * INSET + HANDLE_WIDTH;

    private float progress = 0f;
    private boolean scrolling = false;
    private boolean disabled = false;
    @Nullable
    private Consumer<Float> responder = null;

    public boolean isScrolling() {
        return this.scrolling;
    }

    public VerticalScrollWidget(int x, int y, int height, Component message) {
        super(x, y, BAR_WIDTH, height, message);
    }

    public void setDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            this.disabled = disabled;
            this.scrolling = false;
        }
    }

    public void setResponder(@Nullable Consumer<Float> responder) {
        this.responder = responder;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, getX(), getY(), width, height);

        int handleY = (int) ((this.height - HANDLE_HEIGHT - 2 * INSET) * progress);
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                disabled ? HANDLE_TEXTURE.disabled() : HANDLE_TEXTURE.enabled(),
                this.getX() + INSET,
                this.getY() + INSET + handleY,
                HANDLE_WIDTH,
                HANDLE_HEIGHT);
    }


    @Override
    public void onClick(MouseButtonEvent event, boolean isDoubleClick) {
        super.onClick(event, isDoubleClick);
        if (this.visible && !this.disabled && event.button() == 0) {
            double progress = (event.y() - this.getY() - INSET - HANDLE_HEIGHT / 2.0)
                    / (this.getHeight() - 2 * INSET - HANDLE_HEIGHT);
            setProgress((float) progress);
            this.scrolling = true;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.visible && !this.disabled) {
            setProgress((float) (this.progress - deltaY));
            return true;
        } else {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.scrolling = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.visible && !this.disabled && this.scrolling) {
            double handleCenter = this.progress * (this.getHeight() - 2 * INSET - HANDLE_HEIGHT)
                    + INSET + HANDLE_HEIGHT / 2.0;
            handleCenter += deltaY;
            double progress = (handleCenter - INSET - HANDLE_HEIGHT / 2.0)
                    / (this.getHeight() - 2 * INSET - HANDLE_HEIGHT);
            setProgress((float) progress);
        } else {
            super.onDrag(event, deltaX, deltaY);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (this.visible && !this.disabled && this.scrolling) {
            double progress = (event.y() - this.getY() - INSET - HANDLE_HEIGHT / 2.0)
                    / (this.getHeight() - 2 * INSET - HANDLE_HEIGHT);
            setProgress((float) progress);
            return true;
        }
        return super.mouseDragged(event, mouseX, mouseY);
    }

    public void setProgress(float value) {
        this.progress = Mth.clamp(value, 0F, 1F);
        if (this.responder != null) responder.accept(progress);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {

    }
}
