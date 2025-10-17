package red.jackf.chesttracker.impl.rendering;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import red.jackf.chesttracker.api.memory.Memory;
import red.jackf.chesttracker.api.memory.MemoryKey;
import red.jackf.chesttracker.api.providers.ProviderUtils;
import red.jackf.chesttracker.impl.config.ChestTrackerConfig;
import red.jackf.chesttracker.impl.memory.MemoryBankAccessImpl;
import red.jackf.chesttracker.impl.memory.MemoryBankImpl;
import red.jackf.whereisit.client.api.RenderUtils;

import java.util.Map;
import java.util.Set;

public class NameRenderer {
    private static final Minecraft MC = Minecraft.getInstance();

    public static void setup() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            if (ChestTrackerConfig.INSTANCE.instance().debug.disableContainerNames) return;

            MemoryBankAccessImpl.INSTANCE.getLoadedInternal().ifPresent(bank -> {
                if (bank.getMetadata().getCompatibilitySettings().nameRenderMode == NameRenderMode.DISABLED)
                    return;
                bank.getKey(ProviderUtils.getPlayersCurrentKey()).ifPresent(key -> {
                    HitResult hitResult = MC.hitResult;
                    renderNames(key, bank, hitResult, guiGraphics);
                });
            });
        });
    }

    private static void renderNames(MemoryKey key, MemoryBankImpl bank, @Nullable HitResult hitResult,
                                    GuiGraphics graphics) {
        @Nullable Memory focused = null;

        if (hitResult instanceof BlockHitResult blockHit && blockHit.getType() != HitResult.Type.MISS) {
            focused = key.get(blockHit.getBlockPos())
                    .filter(Memory::hasCustomName)
                    .orElse(null);
        }

        if (bank.getMetadata().getCompatibilitySettings().nameRenderMode == NameRenderMode.FULL) {
            Map<BlockPos, Memory> named = key.getNamedMemories();
            int maxRangeSq = ChestTrackerConfig.INSTANCE.instance().rendering.nameRange *
                    ChestTrackerConfig.INSTANCE.instance().rendering.nameRange;
            Set<BlockPos> alreadyRendering = RenderUtils.getCurrentlyRenderedWithNames();

            for (var entry : named.entrySet()) {
                if (entry.getValue() == focused) continue;
                if (alreadyRendering.contains(entry.getKey())) continue;
                if (entry.getKey().distToCenterSqr(MC.player.position()) < maxRangeSq) {
                    Component name = entry.getValue().renderName();
                    if (name != null) {
                        RenderUtils.scheduleLabelRender(entry.getValue().getCenterPosition().add(0, 1, 0), name);
                    }
                }
            }
        }

        if (focused != null) {
            Component name = focused.renderName();
            if (name != null) {
                RenderUtils.scheduleLabelRender(focused.getCenterPosition().add(0, 1, 0), name, true);
            }
        }
    }
}
