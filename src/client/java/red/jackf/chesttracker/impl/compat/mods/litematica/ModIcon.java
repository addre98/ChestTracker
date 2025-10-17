package red.jackf.chesttracker.impl.compat.mods.litematica;

import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import red.jackf.chesttracker.impl.ChestTracker;

public enum ModIcon implements IGuiIcon {
    INSTANCE;

    @Override
    public int getWidth() {
        return 11;
    }

    @Override
    public int getHeight() {
        return 11;
    }

    @Override
    public int getU() {
        return 0;
    }

    @Override
    public int getV() {
        return 0;
    }

    @Override
    public void renderAt(GuiGraphics graphics, int x, int y, float zLevel, boolean enabled, boolean selected) {
        RenderUtils.drawTexturedRect(graphics, this.getTexture(), x, y, this.getU(), this.getV(), this.getWidth(), this.getHeight(), zLevel);
    }

    @Override
    public ResourceLocation getTexture() {
        return ChestTracker.id("textures/gui/litematica_icon.png");
    }
}
