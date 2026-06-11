package red.jackf.chesttracker.mixins.compat.minihud;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.malilib.network.IClientPayloadData;
import red.jackf.chesttracker.impl.network.ServuxContainerSync;

@Mixin(targets = "fi.dy.masa.minihud.network.ServuxEntitiesHandler", remap = false)
public abstract class ServuxEntitiesHandlerMixin {

    @Inject(method = "decodeClientData", at = @At("TAIL"), remap = false)
    public <P extends IClientPayloadData> void chesttracker$onDecodeClientData(Identifier channel, P data, CallbackInfo ci) {
        if (!channel.toString().equals("servux:entity_data") || data == null) {
            return;
        }

        try {
            java.lang.reflect.Method getTypeMethod = data.getClass().getMethod("getType");
            Object packetType = getTypeMethod.invoke(data);

            String typeName = packetType.toString();

            if ("PACKET_S2C_METADATA".equals(typeName)) {
                java.lang.reflect.Method getCompoundMethod = data.getClass().getMethod("getCompound");
                CompoundTag nbt = (CompoundTag) getCompoundMethod.invoke(data);
                ServuxContainerSync.getInstance().receiveServuxMetadata(nbt);
            } else if ("PACKET_S2C_BLOCK_NBT_RESPONSE_SIMPLE".equals(typeName)) {
                java.lang.reflect.Method getPosMethod = data.getClass().getMethod("getPos");
                BlockPos pos = (BlockPos) getPosMethod.invoke(data);
                java.lang.reflect.Method getCompoundMethod = data.getClass().getMethod("getCompound");
                CompoundTag nbt = (CompoundTag) getCompoundMethod.invoke(data);
                ServuxContainerSync.getInstance().handleBlockEntityData(pos, nbt);
            }
        } catch (Exception e) {
            // ignore non-ServuxEntitiesPacket data
        }
    }
}
