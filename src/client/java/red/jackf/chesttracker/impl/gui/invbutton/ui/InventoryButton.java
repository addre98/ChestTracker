package red.jackf.chesttracker.impl.gui.invbutton.ui;

import com.mojang.datafixers.util.Pair;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import red.jackf.chesttracker.api.gui.ScreenBlacklist;
import red.jackf.chesttracker.api.providers.MemoryLocation;
import red.jackf.chesttracker.impl.ChestTracker;
import red.jackf.chesttracker.impl.config.ChestTrackerConfig;
import red.jackf.chesttracker.impl.gui.invbutton.ButtonPositionMap;
import red.jackf.chesttracker.impl.gui.invbutton.PositionExporter;
import red.jackf.chesttracker.impl.gui.invbutton.position.ButtonPosition;
import red.jackf.chesttracker.impl.gui.invbutton.position.PositionUtils;
import red.jackf.chesttracker.impl.gui.invbutton.position.RectangleUtils;
import red.jackf.chesttracker.impl.memory.MemoryBankAccessImpl;
import red.jackf.chesttracker.impl.memory.MemoryBankImpl;
import red.jackf.chesttracker.impl.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class InventoryButton extends AbstractWidget {
    private static final WidgetSprites TEXTURE = GuiUtil.twoSprite("inventory_button/button");
    private static final int MS_BEFORE_DRAG_START = 200;
    private static final int EXPANDED_HOVER_INFLATE = 5;
    private static final int EXTRA_BUTTON_SPACING = 3;
    public static final int SIZE = 9;
    public static final int IMAGE_SIZE = 11;

    private static @Nullable Pair<AbstractContainerScreen<?>, MemoryLocation> locationToRestore = null;

    private final AbstractContainerScreen<?> parent;
    private ButtonPosition lastPosition;
    private ButtonPosition position;
    private boolean lastRecipeBookVisible;

    private boolean canDrag = false;
    private long mouseDownStart = -1;
    private boolean isDragging = false;
    private boolean secondaryButtonClicked = false;
    private final List<SecondaryButton> secondaryButtons = new ArrayList<>();
    private ScreenRectangle expandedHoverArea = ScreenRectangle.empty();

    public InventoryButton(AbstractContainerScreen<?> parent, ButtonPosition position, Optional<MemoryLocation> target) {
        super(position.getX(parent), position.getY(parent), SIZE, SIZE, Component.translatable("chesttracker.title"));
        this.parent = parent;
        this.position = position;
        this.lastPosition = position;
        this.lastRecipeBookVisible = PositionUtils.isRecipeBookVisible(parent);
        this.setTooltip(Tooltip.create(Component.translatable("chesttracker.title")));

        if (locationToRestore != null && locationToRestore.getFirst() == parent) {
            target = Optional.of(locationToRestore.getSecond());
            locationToRestore = null;
        }

        if (!ScreenBlacklist.isBlacklisted(parent.getClass())) {
            MemoryBankImpl bank = MemoryBankAccessImpl.INSTANCE.getLoadedInternal().orElse(null);
            if (bank != null && ChestTrackerConfig.INSTANCE.instance().gui.inventoryButton.showExtra && target.isPresent()) {
                MemoryLocation location = target.get();
                this.secondaryButtons.add(new RememberContainerButton(bank, location));
                this.secondaryButtons.add(new RenameButton(parent, bank, location));
            }
        }

        if (ChestTrackerConfig.INSTANCE.instance().gui.inventoryButton.showExport) {
            this.secondaryButtons.add(new SecondaryButton(
                    GuiUtil.twoSprite("inventory_button/export"),
                    Component.translatable("chesttracker.inventoryButton.export"),
                    () -> PositionExporter.export(parent, position)
            ));
        }

        for (int i = 0; i < this.secondaryButtons.size(); i++) {
            this.secondaryButtons.get(i).setButtonIndex(i + 1);
        }

        this.applyPosition(true);
    }

    protected static void setRestoreLocation(AbstractContainerScreen<?> screen, MemoryLocation location) {
        locationToRestore = Pair.of(screen, location);
    }

    private static boolean alwaysShowExtra() {
        return ChestTrackerConfig.INSTANCE.instance().gui.inventoryButton.alwaysShowExtra;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isDragging) {
            this.applyPosition(false);
            this.showExtraButtons(alwaysShowExtra() || this.isHovered() || this.isExpandedHover(mouseX, mouseY));
        } else {
            this.showExtraButtons(false);
        }

        Identifier texture = TEXTURE.get(this.isActive(), this.isHoveredOrFocused());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, this.getX() - 1, this.getY() - 1, IMAGE_SIZE, IMAGE_SIZE, -1);

        for (AbstractWidget secondary : this.secondaryButtons) {
            secondary.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private boolean isExpandedHover(int mouseX, int mouseY) {
        return this.expandedHoverArea.overlaps(new ScreenRectangle(mouseX, mouseY, 1, 1));
    }

    private void applyPosition(boolean force) {
        boolean recipeVisible = PositionUtils.isRecipeBookVisible(parent);
        if (!force && position.equals(lastPosition) && recipeVisible == lastRecipeBookVisible) return;
        lastPosition = position;
        lastRecipeBookVisible = recipeVisible;
        this.setX(position.getX(parent));
        this.setY(position.getY(parent));

        var colliders = RectangleUtils.getCollidersFor(parent);
        ScreenDirection freeDir = ScreenDirection.RIGHT;
        for (var dir : List.of(ScreenDirection.RIGHT, ScreenDirection.LEFT, ScreenDirection.DOWN, ScreenDirection.UP)) {
            if (RectangleUtils.isFree(rectangleFor(dir), colliders, parent.getRectangle())) {
                freeDir = dir;
                break;
            }
        }

        for (int i = 1; i <= secondaryButtons.size(); i++) {
            ScreenRectangle pos = RectangleUtils.step(this.getRectangle(), freeDir, (SIZE + EXTRA_BUTTON_SPACING) * i);
            secondaryButtons.get(i - 1).setPosition(pos.left(), pos.top());
        }
    }

    private ScreenRectangle rectangleFor(ScreenDirection dir) {
        var boxes = new ArrayList<ScreenRectangle>();
        boxes.add(this.getRectangle());
        for (int i = 1; i <= secondaryButtons.size(); i++) {
            boxes.add(RectangleUtils.step(this.getRectangle(), dir, (SIZE + EXTRA_BUTTON_SPACING) * i));
        }
        return RectangleUtils.encompassing(boxes);
    }

    private void showExtraButtons(boolean shouldShow) {
        for (SecondaryButton btn : secondaryButtons) btn.setVisible(shouldShow, this.getX(), this.getY());
        if (shouldShow) {
            var all = Stream.concat(
                    Stream.of(this.getRectangle()),
                    secondaryButtons.stream().map(AbstractWidget::getRectangle)
            ).toList();
            this.expandedHoverArea = RectangleUtils.inflate(RectangleUtils.encompassing(all), EXPANDED_HOVER_INFLATE);
        } else this.expandedHoverArea = ScreenRectangle.empty();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (super.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        for (AbstractWidget secondary : this.secondaryButtons) {
            if (secondary.visible && secondary.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        for (int i = 0; i < this.secondaryButtons.size(); i++) {
            AbstractWidget secondary = this.secondaryButtons.get(i);
            if (secondary.mouseClicked(event, isDoubleClick)) {
                this.secondaryButtonClicked = true;
                return true;
            }
        }
        if (super.isMouseOver(event.x(), event.y())) {
            this.canDrag = true;
            this.mouseDownStart = Util.getMillis();
            this.secondaryButtonClicked = false;
            return super.mouseClicked(event, isDoubleClick);
        }

        return false;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isDoubleClick) {
        // Called via super.mouseClicked
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (AbstractWidget secondary : this.secondaryButtons) {
            if (secondary.visible && secondary.isMouseOver(event.x(), event.y()) && secondary.mouseReleased(event)) {
                this.canDrag = false;
                this.mouseDownStart = -1;
                this.secondaryButtonClicked = false;
                return true;
            }
        }

        boolean wasDragging = this.isDragging;
        boolean wasOverPrimary = super.isMouseOver(event.x(), event.y());

        this.canDrag = false;
        this.mouseDownStart = -1;

        if (wasDragging) {
            isDragging = false;

            int centerX = this.getX() + SIZE / 2;
            int centerY = this.getY() + SIZE / 2;

            var finalPos = PositionUtils.calculate(parent, centerX, centerY, false);
            if (finalPos.isPresent()) {
                position = finalPos.get();
                ButtonPositionMap.saveUserPosition(parent, position);
            }

            this.setTooltip(Tooltip.create(Component.translatable("chesttracker.title")));
            return true;
        } else if (wasOverPrimary && !wasDragging && !this.secondaryButtonClicked) {
            ChestTracker.openInGame(Minecraft.getInstance(), parent);
            this.secondaryButtonClicked = false;
            return true;
        }

        this.secondaryButtonClicked = false;
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (!canDrag) return false;

        if (this.secondaryButtonClicked) {
            return false;
        }

        for (AbstractWidget btn : secondaryButtons) {
            if (btn.visible && btn.isMouseOver(event.x(), event.y())) {
                return false;
            }
        }

        if (Util.getMillis() - mouseDownStart >= MS_BEFORE_DRAG_START) {
            isDragging = true;

            int newX = (int) event.x() - SIZE / 2;
            int newY = (int) event.y() - SIZE / 2;

            newX = Mth.clamp(newX, 0, parent.width - SIZE);
            newY = Mth.clamp(newY, 0, parent.height - SIZE);

            ScreenRectangle buttonRect = new ScreenRectangle(newX, newY, SIZE, SIZE);
            Set<ScreenRectangle> collisions = RectangleUtils.getCollidersFor(parent);

            boolean hasCollision = false;
            for (ScreenRectangle collision : collisions) {
                if (buttonRect.overlaps(collision)) {
                    hasCollision = true;
                    break;
                }
            }

            if (!hasCollision) {
                this.setX(newX);
                this.setY(newY);
            }

            setTooltip(null);
            return true;
        }

        return canDrag;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double mouseX, double mouseY) {
        // noop
    }

    @Override
    protected void updateWidgetNarration(@NotNull net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
    }
}
