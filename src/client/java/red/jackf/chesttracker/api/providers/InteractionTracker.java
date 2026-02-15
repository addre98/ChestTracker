package red.jackf.chesttracker.api.providers;

import net.minecraft.client.multiplayer.ClientLevel;
import red.jackf.chesttracker.api.ClientBlockSource;
import red.jackf.chesttracker.impl.providers.InteractionTrackerImpl;

import java.util.Optional;

/**
 * Helper class for checking the last interacts of the player.
 */
public interface InteractionTracker {
    InteractionTracker INSTANCE = InteractionTrackerImpl.INSTANCE;

    /**
     * Get the player's current level. Usually present, unless the player is not in-game. Used by the default provider to
     * get the correct Memory Key (<code>level.dimension().location()</code>).
     *
     * @return An optional containing the player's current level
     */
    Optional<ClientLevel> getPlayerLevel();

    /**
     * Get a block source containing details about the last interacted block, if the last interaction <i>was</i> a block.
     *
     * @return An optional containing information about the last interacted block, or an empty optional if not in-game or
     * the last interaction wasn't a block.
     */
    Optional<ClientBlockSource> getLastBlockSource();

    /**
     * Get details about the last entity the player interacted with that could provide a container.
     *
     * @return Optional containing the entity id and position, if any.
     */
    Optional<EntityInteraction> getLastEntity();

    /**
     * Get the type of the last interaction that produced stored context.
     */
    Optional<InteractionType> getLastInteractionType();

    /**
     * Clear the interaction tracker. This should generally be used after adding a memory in order to prevent desync -
     * think a player right-clicking a random block with no GUi then the server opens one from their end separately.
     */
    void clear();

    /**
     * Details about the last interacted entity.
     *
     * @param entityId In-game entity id
     * @param pos      Block position of the entity at interaction time
     */
    record EntityInteraction(int entityId, java.util.UUID entityUuid, net.minecraft.core.BlockPos pos) {}

    enum InteractionType {
        BLOCK,
        ENTITY
    }
}
