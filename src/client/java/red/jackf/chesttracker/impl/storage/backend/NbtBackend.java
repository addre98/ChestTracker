package red.jackf.chesttracker.impl.storage.backend;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import red.jackf.chesttracker.impl.ChestTracker;
import red.jackf.chesttracker.impl.memory.MemoryBankImpl;
import red.jackf.chesttracker.impl.util.Constants;
import red.jackf.chesttracker.impl.util.FileUtil;
import red.jackf.chesttracker.impl.util.Misc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NbtBackend extends FileBasedBackend {
    private static final Logger LOGGER = LogManager.getLogger(ChestTracker.class.getCanonicalName() + "/NBT");

    // Tracking active saves
    private final Map<String, CompletableFuture<Boolean>> pendingSaves = new ConcurrentHashMap<>();

    @Override
    public @Nullable MemoryBankImpl load(String id, @Nullable HolderLookup.Provider registries) {
        var meta = loadMetadata(id);
        if (meta.isEmpty()) return null;
        var path = Constants.STORAGE_DIR.resolve(id + extension());
        var result = Misc.time(() -> FileUtil.loadFromNbt(MemoryBankImpl.DATA_CODEC, path, registries));
        if (result.getFirst().isPresent()) {
            LOGGER.debug("Loaded {} in {}ns", path, result.getSecond());
            return new MemoryBankImpl(meta.get(), result.getFirst().get());
        } else {
            return new MemoryBankImpl(meta.get(), new HashMap<>());
        }
    }

    @Override
    public boolean save(MemoryBankImpl memoryBank, @Nullable HolderLookup.Provider registries) {
        String id = memoryBank.getId();
        int entriesCount = memoryBank.getMemories().size();

        // Taking snapshots of data before transferring it in an async stream (thread safety)
        long snapshotStart = System.nanoTime();
        var metadataSnapshot = memoryBank.getMetadata().deepCopy();
        var memoriesSnapshot = new HashMap<>(memoryBank.getMemories());
        long snapshotTime = System.nanoTime() - snapshotStart;
        metadataSnapshot.updateModified();
        LOGGER.debug("Created snapshot for {} ({} entries) in {}ms",
                id, entriesCount, snapshotTime / 1_000_000);

        // Cancel the previous save if it is still in progress.
        CompletableFuture<Boolean> previous = pendingSaves.get(id);
        if (previous != null && !previous.isDone()) {
            LOGGER.debug("Previous save for {} still in progress, will be replaced", id);
        }

        // Async saving
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            long saveStartTime = System.nanoTime();
            LOGGER.debug("Starting async save for {}", id);

            // Save metadata
            long metaStart = System.nanoTime();
            if (!saveMetadata(id, metadataSnapshot)) {
                long failedTime = System.nanoTime() - saveStartTime;
                LOGGER.error("Failed to save metadata for {} after {}ms",
                        id, failedTime / 1_000_000);
                return false;
            }
            long metaTime = System.nanoTime() - metaStart;

            // Save .nbt data
            long nbtStart = System.nanoTime();
            boolean result = FileUtil.saveToNbt(
                    memoriesSnapshot,
                    MemoryBankImpl.DATA_CODEC,
                    Constants.STORAGE_DIR.resolve(id + extension()),
                    registries
            );
            long nbtTime = System.nanoTime() - nbtStart;

            long totalTime = System.nanoTime() - saveStartTime;

            if (result) {
                LOGGER.debug("Successfully saved {} ({} entries): NBT={}ms, Metadata={}ms, Total={}ms",
                        id, entriesCount, nbtTime / 1_000_000, metaTime / 1_000_000, totalTime / 1_000_000);
            } else {
                LOGGER.error("Failed to save NBT data for {} after {}ms",
                        id, totalTime / 1_000_000);
            }

            return result;
        }, Util.backgroundExecutor()).exceptionally(ex -> {
            LOGGER.error("Exception during async save for {}", id, ex);
            return false;
        });

        // Save the future for tracking
        pendingSaves.put(id, future);

        // Remove from the map after completion
        future.thenAccept(success -> {
            pendingSaves.remove(id);
            if (!success) {
                LOGGER.warn("Save failed for {}, data may be incomplete", id);
            }
        });

        return true; // Returning the launch success, not the save result
    }

    // Waits for all active saves to complete if the game closes/the world is exited
    public void waitForPendingSaves() {
        if (pendingSaves.isEmpty()) {
            LOGGER.debug("No pending saves to wait for");
            return;
        }
        LOGGER.debug("Waiting for {} pending save(s) to complete...", pendingSaves.size());
        long startTime = System.nanoTime();

        CompletableFuture<Void> allSaves = CompletableFuture.allOf(
                pendingSaves.values().toArray(new CompletableFuture[0])
        );

        try {
            // Waiting of 30 seconds in case of game freezing.
            allSaves.get(30, TimeUnit.SECONDS);
            long waitTime = System.nanoTime() - startTime;
            LOGGER.debug("All pending saves completed in {}ms", waitTime / 1_000_000);
        } catch (Exception ex) {
            LOGGER.error("Error or timeout waiting for saves to complete", ex);
        }
    }

    @Override
    public String extension() {
        return ".nbt";
    }
}
