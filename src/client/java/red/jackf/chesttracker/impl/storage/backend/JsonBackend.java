package red.jackf.chesttracker.impl.storage.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import red.jackf.chesttracker.impl.ChestTracker;
import red.jackf.chesttracker.impl.config.ChestTrackerConfig;
import red.jackf.chesttracker.impl.memory.MemoryBankImpl;
import red.jackf.chesttracker.impl.memory.MemoryKeyImpl;
import red.jackf.chesttracker.impl.memory.metadata.Metadata;
import red.jackf.chesttracker.impl.util.Constants;
import red.jackf.chesttracker.impl.util.FileUtil;
import red.jackf.chesttracker.impl.util.Misc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class JsonBackend extends FileBasedBackend {
    private static final Logger LOGGER = LogManager.getLogger(ChestTracker.class.getCanonicalName() + "/JSON");

    @Override
    public String extension() {
        return ".json";
    }

    // Tracking active saves
    private final Map<String, CompletableFuture<Boolean>> pendingSavesJson = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public MemoryBankImpl load(String id, @Nullable HolderLookup.Provider registries) {
        DynamicOps<JsonElement> ops = registries == null ? JsonOps.INSTANCE : registries.createSerializationContext(JsonOps.INSTANCE);

        Optional<Metadata> metadata = loadMetadata(id);
        if (metadata.isEmpty()) return null;
        Path dataPath = Constants.STORAGE_DIR.resolve(id + extension());
        var result = Misc.time(() -> {
            if (Files.isRegularFile(dataPath)) {
                try {
                    var str = FileUtils.readFileToString(dataPath.toFile(), StandardCharsets.UTF_8);
                    var json = FileUtil.gson().fromJson(str, JsonElement.class);
                    var decoded = MemoryBankImpl.DATA_CODEC.decode(ops, json);
                    if (decoded.isError()) {
                        //noinspection OptionalGetWithoutIsPresent
                        throw new IOException("Invalid Memories JSON: %s".formatted(decoded.error().get().message()));
                    } else {
                        //noinspection OptionalGetWithoutIsPresent
                        return decoded.result().get().getFirst();
                    }
                } catch (JsonParseException | IOException ex) {
                    LOGGER.error("Error loading %s".formatted(dataPath), ex);
                    FileUtil.tryMove(dataPath, dataPath.resolveSibling(dataPath.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return null;
        });
        Map<Identifier, MemoryKeyImpl> data = result.getFirst() == null ? new HashMap<>() : result.getFirst();
        LOGGER.debug("Loaded {} in {}ns", dataPath, result.getSecond());
        return new MemoryBankImpl(metadata.get(), data);
    }

    @Override
    public boolean save(MemoryBankImpl memoryBank, @Nullable HolderLookup.Provider registries) {
        if (ChestTrackerConfig.INSTANCE.instance().storage.AsyncSaving) {
            String id = memoryBank.getId();
            LOGGER.debug("Saving async JSON {}", id);

            // Taking snapshots of data before transferring it in an async stream (thread safety)
            DynamicOps<JsonElement> ops = registries == null
                    ? JsonOps.INSTANCE
                    : registries.createSerializationContext(JsonOps.INSTANCE);
            var metadataSnapshot = memoryBank.getMetadata().deepCopy();
            var memoriesSnapshot = new HashMap<>(memoryBank.getMemories());
            metadataSnapshot.updateModified();

            Path path = Constants.STORAGE_DIR.resolve(id + extension());

            // Cancel the previous save if it is still in progress.
            CompletableFuture<Boolean> previous = pendingSavesJson.get(id);
            if (previous != null && !previous.isDone()) {
                LOGGER.debug("Previous save for {} still in progress, will be replaced", id);
            }

            // Async saving
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Files.createDirectories(path.getParent());
                    Optional<JsonElement> memoryJson = MemoryBankImpl.DATA_CODEC
                            .encodeStart(ops, memoriesSnapshot)
                            .resultOrPartial(Util.prefix("Error encoding memories", LOGGER::error));

                    if (memoryJson.isPresent()) {
                        FileUtils.write(
                                path.toFile(),
                                FileUtil.gson().toJson(memoryJson.get()),
                                StandardCharsets.UTF_8
                        );
                        LOGGER.debug("Async JSON save for {} succeeded", id);
                        return true;
                    } else {
                        LOGGER.error("Unknown error encoding memories for {}", id);
                        return false;
                    }
                } catch (IOException ex) {
                    LOGGER.error("Error saving memories for {}", id, ex);
                    return false;
                }
            }, Util.backgroundExecutor()).exceptionally(ex -> {
                LOGGER.error("Unhandled exception during async JSON save for {}", id, ex);
                return false;
            });

            pendingSavesJson.put(id, future);
            future.thenAccept(success -> {
                pendingSavesJson.remove(id);
                if (!success) {
                    LOGGER.warn("JSON save failed for {}, data may be incomplete", id);
                }
            });

            return true;
        } else {
            LOGGER.debug("Saving {}", memoryBank.getId());

            DynamicOps<JsonElement> ops = registries == null ? JsonOps.INSTANCE : registries.createSerializationContext(JsonOps.INSTANCE);

            memoryBank.getMetadata().updateModified();
            boolean metaSaveSuccess = saveMetadata(memoryBank.getId(), memoryBank.getMetadata());
            if (!metaSaveSuccess) return false;

            Path path = Constants.STORAGE_DIR.resolve(memoryBank.getId() + extension());

            try {
                Files.createDirectories(path.getParent());
                Optional<JsonElement> memoryJson = MemoryBankImpl.DATA_CODEC.encodeStart(ops, memoryBank.getMemories())
                        .resultOrPartial(Util.prefix("Error encoding memories", LOGGER::error));
                if (memoryJson.isPresent()) {
                    FileUtils.write(path.toFile(), FileUtil.gson().toJson(memoryJson.get()), StandardCharsets.UTF_8);
                    return true;
                } else {
                    LOGGER.error("Unknown error encoding memories");
                }
            } catch (IOException ex) {
                LOGGER.error("Error saving memories", ex);
            }

            return false;
        }
    }

    // Waits for all active saves to complete if the game closes/the world
    public void waitForPendingSaves() {
        if (pendingSavesJson.isEmpty()) {
            LOGGER.debug("No pending JSON saves to wait for");
            return;
        }
        LOGGER.debug("Waiting for {} pending JSON save(s) to complete...", pendingSavesJson.size());

        CompletableFuture<Void> allSaves = CompletableFuture.allOf(
                pendingSavesJson.values().toArray(new CompletableFuture[0])
        );

        try {
            // Waiting of 30 seconds in case of game freezing.
            allSaves.get(30, TimeUnit.SECONDS);
            LOGGER.debug("All pending JSON saves completed");
        } catch (Exception ex) {
            LOGGER.error("Error or timeout waiting for JSON saves to complete", ex);
        }
    }
}
