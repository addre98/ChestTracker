package red.jackf.chesttracker.impl.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ChangeableImageButton extends Button {
    private WidgetSprites sprites;

    public ChangeableImageButton(
            int x,
            int y,
            int width,
            int height,
            WidgetSprites initialSprites,
            Component message,
            OnPress onPress) {
        super(x, y, width, height, message, b -> onPress.onPress((ChangeableImageButton) b), DEFAULT_NARRATION);
        this.sprites = initialSprites;
    }

    public void setSprites(WidgetSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Identifier resourceLocation = this.sprites.get(this.isActive(), this.isHoveredOrFocused());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourceLocation, this.getX(), this.getY(), this.width, this.height);
    }

    @Environment(EnvType.CLIENT)
    public interface OnPress {
        void onPress(ChangeableImageButton button);
    }
}
