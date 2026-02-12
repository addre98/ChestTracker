package red.jackf.chesttracker.impl.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import red.jackf.chesttracker.api.memory.Memory;
import red.jackf.chesttracker.api.memory.MemoryKey;
import red.jackf.chesttracker.api.providers.ProviderUtils;
import red.jackf.chesttracker.impl.config.ChestTrackerConfig;
import red.jackf.chesttracker.impl.memory.MemoryBankAccessImpl;
import red.jackf.chesttracker.impl.memory.MemoryBankImpl;
import red.jackf.whereisit.client.api.RenderUtils;
import red.jackf.whereisit.client.render.WhereIsItPipelines;
import red.jackf.whereisit.config.WhereIsItConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NameRenderer {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final List<ScheduledLabel> scheduledLabels = new ArrayList<>();

    private record ScheduledLabel(Vec3 position, Component text, boolean focused) {}

    public static void scheduleLabels() {
        scheduledLabels.clear();

        if (ChestTrackerConfig.INSTANCE.instance().debug.disableContainerNames) return;

        MemoryBankAccessImpl.INSTANCE.getLoadedInternal().ifPresent(bank -> {
            if (bank.getMetadata().getCompatibilitySettings().nameRenderMode == NameRenderMode.DISABLED)
                return;
            bank.getKey(ProviderUtils.getPlayersCurrentKey()).ifPresent(key -> {
                HitResult hitResult = MC.hitResult;
                collectLabels(key, bank, hitResult);
            });
        });
    }

    private static void collectLabels(MemoryKey key, MemoryBankImpl bank, @Nullable HitResult hitResult) {
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
                        Vec3 pos = entry.getValue().getCenterPosition().add(0, 1, 0);
                        scheduledLabels.add(new ScheduledLabel(pos, name, false));
                    }
                }
            }
        }

        if (focused != null) {
            Component name = focused.renderName();
            if (name != null) {
                Vec3 pos = focused.getCenterPosition().add(0, 1, 0);
                scheduledLabels.add(new ScheduledLabel(pos, name, true));
            }
        }
    }

    public static boolean hasScheduledLabels() {
        return !scheduledLabels.isEmpty();
    }

    public static void clearScheduledLabels() {
        scheduledLabels.clear();
    }

    public static void renderLabels(PoseStack ignoredPoseStack, Camera camera, MultiBufferSource consumers) {
        if (scheduledLabels.isEmpty()) return;

        Vec3 camPos = camera.position();

        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        pose.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180f));

        scheduledLabels.stream()
                .sorted(Comparator.comparingDouble(label -> -camPos.distanceToSqr(label.position)))
                .forEach(label -> renderLabel(label, pose, camera, camPos, consumers));

        scheduledLabels.clear();
    }

    private static void renderLabel(ScheduledLabel label, PoseStack pose, Camera camera, Vec3 camPos, MultiBufferSource consumers) {
        pose.pushPose();

        // Offset from the camera
        final double xOffset = label.position.x - camPos.x;
        final double yOffset = label.position.y + WhereIsItConfig.INSTANCE.instance().getClient().Ypositiontext - camPos.y;
        final double zOffset = label.position.z - camPos.z;
        pose.translate(xOffset, yOffset, zOffset);

        // Additional rotation for billboard
        pose.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        pose.mulPose(Axis.XP.rotationDegrees(camera.xRot()));

        // Scale
        float scale = 0.025f * WhereIsItConfig.INSTANCE.instance().getClient().containerNameLabelScale;
        pose.scale(-scale, -scale, scale);

        Matrix4f matrix = pose.last().pose();
        Font font = MC.font;
        int width = font.width(label.text);
        float x = -width / 2f;

        // Background
        VertexConsumer bgBuffer = consumers.getBuffer(WhereIsItPipelines.TEXT_BACKGROUND_NO_DEPTH);
        int bgColour = ((int)(MC.options.getBackgroundOpacity(0.25F) * 255F)) << 24;
        bgBuffer.addVertex(matrix, x - 1, -1f, 0).setColor(bgColour).setLight(LightTexture.FULL_BRIGHT);
        bgBuffer.addVertex(matrix, x - 1, 10f, 0).setColor(bgColour).setLight(LightTexture.FULL_BRIGHT);
        bgBuffer.addVertex(matrix, x + width, 10f, 0).setColor(bgColour).setLight(LightTexture.FULL_BRIGHT);
        bgBuffer.addVertex(matrix, x + width, -1f, 0).setColor(bgColour).setLight(LightTexture.FULL_BRIGHT);

        // Text
        font.drawInBatch(label.text, x, 0, 0xFFFFFFFF, false, matrix, consumers,
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);

        pose.popPose();
    }
}