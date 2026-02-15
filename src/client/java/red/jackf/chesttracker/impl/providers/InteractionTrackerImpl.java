package red.jackf.chesttracker.impl.providers;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import red.jackf.chesttracker.impl.ChestTracker;
import red.jackf.chesttracker.api.ClientBlockSource;
import red.jackf.chesttracker.api.providers.InteractionTracker;
import red.jackf.chesttracker.impl.util.CachedClientBlockSource;

import java.util.Optional;

public class InteractionTrackerImpl implements InteractionTracker {
    public static final InteractionTrackerImpl INSTANCE = new InteractionTrackerImpl();
    private static final Logger LOGGER = ChestTracker.getLogger("InteractionTracker");

    private @Nullable ClientBlockSource lastSource = null;
    private @Nullable EntityInteraction lastEntity = null;
    private @Nullable InteractionType lastType = null;

    public static void setup() {
        // Event Setup
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand == InteractionHand.MAIN_HAND && level instanceof ClientLevel clientLevel) {
                INSTANCE.setLastBlockSource(new CachedClientBlockSource(clientLevel, hitResult.getBlockPos()));
                LOGGER.debug("[BlockClick] pos={} dim={}", hitResult.getBlockPos().toShortString(), level.dimension().identifier());
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(world instanceof ClientLevel)) return InteractionResult.PASS;

            if (entity instanceof net.minecraft.world.Container) {
                INSTANCE.setLastEntity(new EntityInteraction(entity.getId(), entity.getUUID(), entity.blockPosition()));
                LOGGER.debug("[EntityClick] id={} type={} pos={} dim={}", entity.getId(), entity.getType().toShortString(), entity.blockPosition().toShortString(), world.dimension().identifier());
            } else {
                INSTANCE.clear();
            }
            return InteractionResult.PASS;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> INSTANCE.clear());
    }

    @Override
    public Optional<ClientLevel> getPlayerLevel() {
        if (Minecraft.getInstance().level == null) return Optional.empty();
        return Optional.of(Minecraft.getInstance().level);
    }

    @Override
    public Optional<ClientBlockSource> getLastBlockSource() {
        return Optional.ofNullable(lastSource);
    }

    @Override
    public Optional<EntityInteraction> getLastEntity() {
        return Optional.ofNullable(lastEntity);
    }

    @Override
    public Optional<InteractionType> getLastInteractionType() {
        return Optional.ofNullable(lastType);
    }

    public void clear() {
        this.lastSource = null;
        this.lastEntity = null;
        this.lastType = null;
        LOGGER.debug("[Clear] block & entity cleared");
    }

    private void clearLastBlockSource() {
        this.lastSource = null;
    }

    private void clearLastEntity() {
        this.lastEntity = null;
    }

    public void setLastBlockSource(ClientBlockSource source) {
        this.lastSource = source;
        this.lastEntity = null;
        this.lastType = InteractionType.BLOCK;
        LOGGER.debug("[SetBlock] pos={} dim={}", source.pos().toShortString(), source.level().dimension().identifier());
    }

    public void setLastEntity(EntityInteraction entity) {
        this.lastEntity = entity;
        this.lastSource = null;
        this.lastType = InteractionType.ENTITY;
        LOGGER.debug("[SetEntity] id={} uuid={} pos={}", entity.entityId(), entity.entityUuid(), entity.pos().toShortString());
    }
}
