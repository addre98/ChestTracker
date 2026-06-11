package red.jackf.chesttracker.impl.network;

import fi.dy.masa.malilib.network.ClientPlayHandler;
import fi.dy.masa.malilib.network.IPluginClientPlayHandler;
import fi.dy.masa.malilib.network.PacketSplitter;
import fi.dy.masa.malilib.network.IClientPayloadData;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.NonNull;
import red.jackf.chesttracker.impl.ChestTracker;
import red.jackf.chesttracker.impl.network.ServuxContainerSync;

import org.jetbrains.annotations.Nullable;

public abstract class ServuxEntitiesHandler<T extends CustomPacketPayload> implements IPluginClientPlayHandler<T> {
    private static final ServuxEntitiesHandler<ServuxEntitiesPacket.Payload> INSTANCE = new ServuxEntitiesHandler<>() {
        @Override
        public void receive(ServuxEntitiesPacket.Payload payload, ClientPlayNetworking.@NonNull Context context) {
            ServuxEntitiesHandler.INSTANCE.receivePlayPayload(payload, context);
        }
    };

    public static ServuxEntitiesHandler<ServuxEntitiesPacket.Payload> getInstance() {
        return INSTANCE;
    }

    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("servux", "entity_data");

    private boolean servuxRegistered;
    private boolean payloadRegistered = false;
    private int failures = 0;
    private static final int MAX_FAILURES = 4;
    private long readingSessionKey = -1;

    @Override
    public Identifier getPayloadChannel() {
        return CHANNEL_ID;
    }

    @Override
    public boolean isPlayRegistered(Identifier channel) {
        if (channel.equals(CHANNEL_ID)) {
            return this.payloadRegistered;
        }
        return false;
    }

    @Override
    public void setPlayRegistered(Identifier channel) {
        if (channel.equals(CHANNEL_ID)) {
            this.payloadRegistered = true;
        }
    }

    @Override
    public <P extends IClientPayloadData> void decodeClientData(Identifier channel, P data) {
        ServuxEntitiesPacket packet = (ServuxEntitiesPacket) data;

        if (!channel.equals(CHANNEL_ID) || packet == null) {
            return;
        }

        ChestTracker.LOGGER.info("ServuxEntitiesHandler#decodeClientData: received packetType={}", packet.getType());

        switch (packet.getType()) {
            case PACKET_S2C_METADATA -> {
                ChestTracker.LOGGER.info("ServuxEntitiesHandler: Received METADATA response");
                if (ServuxContainerSync.getInstance().receiveServuxMetadata(packet.getCompound())) {
                    this.servuxRegistered = true;
                }
            }
            case PACKET_S2C_BLOCK_NBT_RESPONSE_SIMPLE -> {
                ChestTracker.LOGGER.info("ServuxEntitiesHandler: Received Block NBT response for {}", packet.getPos());
                ServuxContainerSync.getInstance().handleBlockEntityData(packet.getPos(), packet.getCompound());
            }
            default ->
                ChestTracker.LOGGER.warn("ServuxEntitiesHandler#decodeClientData(): received unhandled packetType {} of size {} bytes.",
                    packet.getPacketType(), packet.getTotalSize());
        }
    }

    @Override
    public void reset(Identifier channel) {
        if (channel.equals(CHANNEL_ID) && this.servuxRegistered) {
            this.servuxRegistered = false;
            this.failures = 0;
            this.readingSessionKey = -1;
        }
    }

    public void resetFailures(Identifier channel) {
        if (channel.equals(CHANNEL_ID) && this.failures > 0) {
            this.failures = 0;
        }
    }

    @Override
    public void receivePlayPayload(T payload, ClientPlayNetworking.Context ctx) {
        if (payload.type().id().equals(CHANNEL_ID)) {
            ServuxEntitiesHandler.INSTANCE.decodeClientData(CHANNEL_ID, ((ServuxEntitiesPacket.Payload) payload).data());
        }
    }

    @Override
    public void encodeWithSplitter(FriendlyByteBuf buffer, ClientPacketListener handler) {
        ServuxEntitiesHandler.INSTANCE.sendPlayPayload(new ServuxEntitiesPacket.Payload(ServuxEntitiesPacket.ResponseC2SData(buffer)));
    }

    @Override
    public <P extends IClientPayloadData> void encodeClientData(P data) {
        ServuxEntitiesPacket packet = (ServuxEntitiesPacket) data;

        if (packet.getType().equals(ServuxEntitiesPacket.Type.PACKET_C2S_NBT_RESPONSE_START)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeVarInt(packet.getTransactionId());
            buffer.writeNbt(packet.getCompound());
            PacketSplitter.send(this, buffer, Minecraft.getInstance().getConnection());
        } else if (!ServuxEntitiesHandler.INSTANCE.sendPlayPayload(new ServuxEntitiesPacket.Payload(packet))) {
            ChestTracker.LOGGER.warn("ServuxEntitiesHandler#encodeClientData: sendPlayPayload failed");
            if (this.failures > MAX_FAILURES) {
                ChestTracker.LOGGER.error("ServuxEntitiesHandler#encodeClientData(): encountered [{}] sendPayload failures", MAX_FAILURES);
                this.servuxRegistered = false;
                ServuxEntitiesHandler.INSTANCE.unregisterPlayReceiver();
                ServuxContainerSync.getInstance().onPacketFailure();
            } else {
                this.failures++;
            }
        }
    }
}
