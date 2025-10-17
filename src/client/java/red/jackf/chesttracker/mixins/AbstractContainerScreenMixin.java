package red.jackf.chesttracker.mixins;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import red.jackf.chesttracker.impl.gui.invbutton.CTButtonScreenDuck;
import red.jackf.chesttracker.impl.gui.invbutton.ui.InventoryButton;
import red.jackf.chesttracker.impl.providers.ScreenOpenContextImpl;

/**
 * Mixin does a few things:
 * <ul>
 *     <li>Adds early mouse dragging and released callbacks used to drag around the CT button</li>
 *     <li>Adds dimension grabbing for positioning the CT button</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements CTButtonScreenDuck {
    @Unique
    @Nullable
    private InventoryButton ctButton = null;

    @Unique
    @Nullable
    private ScreenOpenContextImpl openContext = null;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tryClickResize(MouseButtonEvent event, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.ctButton != null && this.ctButton.isMouseOver(event.x(), event.y())) {
            boolean handled = this.ctButton.mouseClicked(event, isDoubleClick);
            if (handled) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void tryDragResize(MouseButtonEvent event, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (this.ctButton != null) {
            boolean handled = this.ctButton.mouseDragged(event, mouseX, mouseY);
            if (handled) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void tryReleaseResize(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.ctButton != null) {
            boolean handled = this.ctButton.mouseReleased(event);
            if (handled) {
                cir.setReturnValue(true);
            }
        }
    }

    @Override
    @Accessor("leftPos")
    public abstract int chesttracker$getLeft();

    @Override
    @Accessor("topPos")
    public abstract int chesttracker$getTop();

    @Override
    @Accessor("imageWidth")
    public abstract int chesttracker$getWidth();

    @Override
    @Accessor("imageHeight")
    public abstract int chesttracker$getHeight();

    @Override
    public void chesttracker$setButton(InventoryButton button) {
        this.ctButton = button;
    }

    @Override
    public void chesttracker$setContext(ScreenOpenContextImpl openContext) {
        this.openContext = openContext;
    }

    @Override
    public @Nullable ScreenOpenContextImpl chesttracker$getContext() {
        return this.openContext;
    }
}
