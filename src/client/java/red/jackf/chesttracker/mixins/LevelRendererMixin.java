package red.jackf.chesttracker.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.jackf.chesttracker.impl.rendering.NameRenderer;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRenderLevelEnd(
            GraphicsResourceAllocator allocator,
            DeltaTracker tracker,
            boolean blockOutline,
            CameraRenderState cameraRenderState,
            Matrix4fc frustum,
            GpuBufferSlice fog,
            Vector4f fogCol,
            boolean sky,
            ChunkSectionsToRender chunkSections,
            CallbackInfo ci) {

        // Planning the tags
        NameRenderer.scheduleLabels();

        // Rendering the labels
        if (NameRenderer.hasScheduledLabels()) {
            //GL11.glDisable(GL11.GL_DEPTH_TEST);
            //GL11.glDepthFunc(GL11.GL_ALWAYS);

            Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

            NameRenderer.renderLabels(null, cam, bufferSource);
            bufferSource.endBatch();

            //GL11.glDepthFunc(GL11.GL_LEQUAL);
            //GL11.glEnable(GL11.GL_DEPTH_TEST);

            NameRenderer.clearScheduledLabels();
        }
    }
}