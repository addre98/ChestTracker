package red.jackf.chesttracker.impl.compat.mods.litematica;

import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.resources.Identifier;
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
    public void renderAt(GuiContext ctx, int x, int y, float zLevel, boolean enabled, boolean selected) {
        RenderUtils.drawTexturedRect(ctx, this.getTexture(), x, y, this.getU(), this.getV(), this.getWidth(), this.getHeight(), zLevel);
    }

    @Override
    public Identifier getTexture() {
        return ChestTracker.id("textures/gui/litematica_icon.png");
    }
}
