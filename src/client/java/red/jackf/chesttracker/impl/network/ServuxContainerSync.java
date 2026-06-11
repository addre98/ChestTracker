package red.jackf.chesttracker.impl.network;

import fi.dy.masa.malilib.network.ClientPlayHandler;
import fi.dy.masa.malilib.network.IPluginClientPlayHandler;
import fi.dy.masa.malilib.util.InventoryUtils;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import red.jackf.chesttracker.api.providers.MemoryBuilder;
import red.jackf.chesttracker.api.providers.ProviderUtils;
import red.jackf.chesttracker.impl.ChestTracker;
import red.jackf.chesttracker.impl.config.ChestTrackerConfig;
import red.jackf.chesttracker.impl.memory.MemoryBankAccessImpl;
import red.jackf.chesttracker.impl.memory.MemoryBankImpl;

import red.jackf.whereisit.api.search.ConnectedBlocksGrabber;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ServuxContainerSync {
    private static final ServuxContainerSync INSTANCE = new ServuxContainerSync();
    
    public static ServuxContainerSync getInstance() {
        return INSTANCE;
    }

    private static final ServuxEntitiesHandler<ServuxEntitiesPacket.Payload> HANDLER = ServuxEntitiesHandler.getInstance();
    private final Minecraft mc = Minecraft.getInstance();
    
    private boolean servuxServer = false;
    private boolean hasInvalidServux = false;
    private String servuxVersion = "unknown";
    
    private boolean isSyncing = false;
    private final Set<BlockPos> pendingRequests = new LinkedHashSet<>();
    private final Map<BlockPos, CompoundTag> receivedData = new HashMap<>();
    private int totalContainers = 0;
    private int processedContainers = 0;
    private long syncStartTime = 0;
    
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChestTracker-ServuxScanner");
        t.setDaemon(true);
        return t;
    });

    private Object miniHudEntitiesDataManager = null;
    private boolean miniHudExists = false;

    public void init() {
        ChestTracker.LOGGER.info("ServuxContainerSync: Initializing...");
        
        try {
            Class<?> entitiesDataManagerClass = Class.forName("fi.dy.masa.minihud.data.EntitiesDataManager");
            java.lang.reflect.Method getInstanceMethod = entitiesDataManagerClass.getMethod("getInstance");
            miniHudEntitiesDataManager = getInstanceMethod.invoke(null);
            miniHudExists = true;
            ChestTracker.LOGGER.info("ServuxContainerSync: MiniHUD detected, will use its network layer via reflection");
        } catch (Exception e) {
            ChestTracker.LOGGER.info("ServuxContainerSync: MiniHUD not found, using standalone network layer");
            miniHudExists = false;
        }
        
        if (!miniHudExists) {
            ClientPlayHandler.getInstance().registerClientPlayHandler(HANDLER);
            HANDLER.registerPlayPayload(ServuxEntitiesPacket.Payload.ID, ServuxEntitiesPacket.Payload.CODEC, IPluginClientPlayHandler.BOTH_CLIENT);
            HANDLER.registerPlayReceiver(ServuxEntitiesPacket.Payload.ID, HANDLER::receivePlayPayload);
            ChestTracker.LOGGER.info("ServuxContainerSync: Registered standalone network handlers");
        }
    }

    private boolean metadataRequested = false;

    public void registerReceiver() {
        ChestTracker.LOGGER.info("ServuxContainerSync: registerReceiver called - skipping, MiniHUD handles receiver registration");
    }

    public void requestMetadataIfNeeded() {
        if (metadataRequested || servuxServer || hasInvalidServux) {
            return;
        }
        
        if (miniHudExists && miniHudEntitiesDataManager != null) {
            try {
                java.lang.reflect.Method requestMetadataMethod = miniHudEntitiesDataManager.getClass().getMethod("requestMetadata");
                requestMetadataMethod.invoke(miniHudEntitiesDataManager);
                metadataRequested = true;
            } catch (Exception e) {
                ChestTracker.LOGGER.error("ServuxContainerSync: Failed to request metadata via MiniHUD: {}", e.getLocalizedMessage());
            }
        } else {
            requestMetadata();
            metadataRequested = true;
        }
    }

    public void onWorldJoin() {
        ChestTracker.LOGGER.info("ServuxContainerSync: onWorldJoin called");
        
        resetSync();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.empty());
        }

        if (mc.hasSingleplayerServer()) {
            ChestTracker.LOGGER.info("ServuxContainerSync: Skipping - running in singleplayer");
            return;
        }

        registerReceiver();
        requestMetadataIfNeeded();
    }

    public void onWorldLeave(boolean isLogout) {
        ChestTracker.LOGGER.info("ServuxContainerSync: onWorldLeave called, isLogout={}", isLogout);
        if (isLogout) {
            HANDLER.reset(ServuxEntitiesHandler.CHANNEL_ID);
            HANDLER.resetFailures(ServuxEntitiesHandler.CHANNEL_ID);
            this.servuxServer = false;
            this.hasInvalidServux = false;
            ChestTracker.LOGGER.info("ServuxContainerSync: Reset Servux connection state");
        }
        resetSync();

        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.empty());
        }
    }

    private void requestMetadata() {
        if (!mc.hasSingleplayerServer()) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("version", "ChestTracker");
            ChestTracker.LOGGER.info("ServuxContainerSync: Sending metadata request to Servux");
            HANDLER.encodeClientData(ServuxEntitiesPacket.MetadataRequest(nbt));
        }
    }

    public boolean receiveServuxMetadata(CompoundTag data) {
        if (!mc.hasSingleplayerServer()) {
            ChestTracker.LOGGER.info("ServuxContainerSync: received METADATA from Servux");
            
            if (data.getIntOr("version", -1) != ServuxEntitiesPacket.PROTOCOL_VERSION) {
                ChestTracker.LOGGER.warn("ServuxContainerSync: Mis-matched protocol version!");
            }
            
            this.servuxVersion = data.getStringOr("servux", "?");
            this.servuxServer = true;
            this.hasInvalidServux = false;
            this.metadataRequested = false;
            
            ChestTracker.LOGGER.info("ServuxContainerSync: connected to Servux version {}", servuxVersion);
            return true;
        }
        ChestTracker.LOGGER.info("ServuxContainerSync: Ignoring metadata - singleplayer");
        return false;
    }

    public void onPacketFailure() {
        ChestTracker.LOGGER.info("ServuxContainerSync: Packet failure detected");
        this.servuxServer = false;
        this.hasInvalidServux = true;
        this.metadataRequested = false;
    }

    public boolean hasServuxServer() {
        if (miniHudExists && miniHudEntitiesDataManager != null) {
            try {
                java.lang.reflect.Method hasServuxMethod = miniHudEntitiesDataManager.getClass().getMethod("hasServuxServer");
                boolean miniHudHasServux = (Boolean) hasServuxMethod.invoke(miniHudEntitiesDataManager);
                if (miniHudHasServux) {
                    this.servuxServer = true;
                }
                return this.servuxServer;
            } catch (Exception e) {
                ChestTracker.LOGGER.error("ServuxContainerSync: Failed to get MiniHUD Servux status: {}", e.getLocalizedMessage());
            }
        }
        return this.servuxServer;
    }

    public boolean isCurrentlySyncing() {
        return isSyncing;
    }

    public void startSync() {
        ChestTracker.LOGGER.info("ServuxContainerSync: startSync called");
        
        if (isSyncing) {
            sendChatMessage(Component.translatable("chesttracker.servux.syncAlreadyInProgress"));
            return;
        }

        if (!hasServuxServer()) {
            ChestTracker.LOGGER.info("ServuxContainerSync: No Servux server detected");
            sendChatMessage(Component.translatable("chesttracker.servux.noServux"));
            return;
        }

        MemoryBankImpl memoryBank = MemoryBankAccessImpl.INSTANCE.getLoadedInternal().orElse(null);
        if (memoryBank == null) {
            ChestTracker.LOGGER.info("ServuxContainerSync: No memory bank loaded");
            sendChatMessage(Component.translatable("chesttracker.servux.noMemoryBank"));
            return;
        }

        Level world = mc.level;
        if (world == null || mc.player == null) {
            ChestTracker.LOGGER.info("ServuxContainerSync: No world or player");
            sendChatMessage(Component.translatable("chesttracker.servux.noWorld"));
            return;
        }

        ChestTracker.LOGGER.info("ServuxContainerSync: Starting sync process");
        isSyncing = true;
        syncStartTime = System.currentTimeMillis();
        pendingRequests.clear();
        receivedData.clear();
        processedContainers = 0;

        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.empty());
        }

        sendChatMessage(Component.translatable("chesttracker.servux.syncStarting"));

        CompletableFuture.supplyAsync(() -> scanContainers(world, memoryBank), scanExecutor)
            .thenAccept(containerPositions -> {
                ChestTracker.LOGGER.info("ServuxContainerSync: scan returned {} positions", containerPositions.size());
                
                if (!isSyncing) {
                    ChestTracker.LOGGER.info("ServuxContainerSync: sync was cancelled");
                    return;
                }
                
                if (containerPositions.isEmpty()) {
                    sendChatMessage(Component.translatable("chesttracker.servux.noContainersFound"));
                    resetSync();
                    return;
                }

                totalContainers = containerPositions.size();
                ChestTracker.LOGGER.info("ServuxContainerSync: found {} containers", totalContainers);

                mc.execute(() -> {
                    if (isSyncing) {
                        pendingRequests.addAll(containerPositions);
                        processNextRequests();
                    }
                });
            })
            .exceptionally(e -> {
                ChestTracker.LOGGER.error("ServuxContainerSync: error during scan", e);
                mc.execute(() -> {
                    sendChatMessage(Component.translatable("chesttracker.servux.syncError"));
                    resetSync();
                });
                return null;
            });
    }

    private List<BlockPos> scanContainers(Level world, MemoryBankImpl memoryBank) {
        ChestTracker.LOGGER.info("ServuxContainerSync: Starting container scan");
        int syncRange = ChestTrackerConfig.INSTANCE.instance().rendering.servuxSyncRange;
        BlockPos playerPos = mc.player.blockPosition();
        List<BlockPos> containerPositions = new ArrayList<>();
        Set<BlockPos> processedPositions = new HashSet<>();

        var filteringSettings = memoryBank.getMetadata().getFilteringSettings();
        var rememberedContainers = filteringSettings.rememberedContainers;

        int chunkRange = (syncRange + 15) / 16;
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                
                if (!world.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                var chunk = world.getChunk(chunkX, chunkZ);
                var blockEntities = chunk.getBlockEntities();

                for (var entry : blockEntities.entrySet()) {
                    BlockPos pos = entry.getKey();
                    
                    if (processedPositions.contains(pos)) {
                        continue;
                    }

                    if (pos.distToCenterSqr(playerPos.getX() + 0.5, playerPos.getY() + 0.5, playerPos.getZ() + 0.5) > syncRange * syncRange) {
                        continue;
                    }

                    BlockEntity blockEntity = entry.getValue();
                    BlockState blockState = world.getBlockState(pos);

                    if (isContainerBlockEntity(blockEntity) && rememberedContainers.predicate.test(blockState)) {
                        List<BlockPos> connected = ConnectedBlocksGrabber.getConnected(world, blockState, pos);

                        for (BlockPos connectedPos : connected) {
                            processedPositions.add(connectedPos);
                        }

                        if (!connected.isEmpty()) {
                            containerPositions.add(connected.get(0));
                        }
                    }
                }
            }
        }
        
        ChestTracker.LOGGER.info("ServuxContainerSync: Scan complete, found {} containers", containerPositions.size());
        return containerPositions;
    }

    private boolean isContainerBlockEntity(BlockEntity blockEntity) {
        BlockEntityType<?> type = blockEntity.getType();
        return type == BlockEntityType.CHEST ||
               type == BlockEntityType.BARREL ||
               type == BlockEntityType.SHULKER_BOX ||
               type == BlockEntityType.HOPPER ||
               type == BlockEntityType.FURNACE ||
               type == BlockEntityType.BLAST_FURNACE ||
               type == BlockEntityType.SMOKER ||
               type == BlockEntityType.DISPENSER ||
               type == BlockEntityType.DROPPER ||
               type == BlockEntityType.BREWING_STAND;
    }

    private void processNextRequests() {
        if (pendingRequests.isEmpty()) {
            if (processedContainers >= totalContainers) {
                finishSync();
            }
            return;
        }

        if (System.currentTimeMillis() - syncStartTime > getSyncTimeoutMs()) {
            ChestTracker.LOGGER.warn("ServuxContainerSync: sync timeout");
            sendChatMessage(Component.translatable("chesttracker.servux.syncTimeout"));
            resetSync();
            return;
        }

        int requestsPerTick = ChestTrackerConfig.INSTANCE.instance().rendering.servuxSyncRequestRate;
        int sent = 0;

        Iterator<BlockPos> iterator = pendingRequests.iterator();
        while (iterator.hasNext() && sent < requestsPerTick) {
            BlockPos pos = iterator.next();
            iterator.remove();
            
            if (miniHudExists && miniHudEntitiesDataManager != null) {
                try {
                    java.lang.reflect.Method requestMethod = miniHudEntitiesDataManager.getClass()
                        .getMethod("requestBlockEntity", Level.class, BlockPos.class);
                    requestMethod.invoke(miniHudEntitiesDataManager, mc.level, pos);
                } catch (Exception e) {
                    ChestTracker.LOGGER.error("ServuxContainerSync: Failed to request via MiniHUD");
                    processedContainers++;
                }
            } else {
                HANDLER.encodeClientData(ServuxEntitiesPacket.BlockEntityRequest(pos));
            }
            sent++;
        }

        if (sent > 0 && isSyncing) {
            mc.execute(() -> {
                if (isSyncing) {
                    processNextRequests();
                }
            });
        }
    }

    public void handleBlockEntityData(BlockPos pos, CompoundTag nbt) {
        if (!isSyncing) {
            return;
        }

        if (nbt != null && nbt.contains("Items")) {
            receivedData.put(pos, nbt);
            processedContainers++;
            saveContainerToMemory(pos, nbt);
        } else {
            processedContainers++;
        }

        if (processedContainers >= totalContainers && pendingRequests.isEmpty()) {
            finishSync();
        }
    }

    private void saveContainerToMemory(BlockPos pos, CompoundTag nbt) {
        MemoryBankImpl memoryBank = MemoryBankAccessImpl.INSTANCE.getLoadedInternal().orElse(null);
        if (memoryBank == null || mc.level == null) {
            return;
        }

        var currentKey = ProviderUtils.getPlayersCurrentKey();
        if (currentKey.isEmpty()) {
            return;
        }

        try {
            CompoundData compoundData = DataConverterNbt.fromVanillaCompound(nbt);
            Container container = InventoryUtils.getDataInventory(compoundData, -1, mc.level.registryAccess());
            
            if (container == null) {
                return;
            }

            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }

            if (items.isEmpty()) {
                return;
            }

            BlockState blockState = mc.level.getBlockState(pos);
            MemoryBuilder memoryBuilder = MemoryBuilder.create(items)
                .withCustomName(null)
                .inContainer(blockState.getBlock());

            List<BlockPos> connected = ConnectedBlocksGrabber.getConnected(mc.level, blockState, pos);
            if (connected.size() > 1) {
                BlockPos rootPos = connected.get(0);
                List<BlockPos> otherPositions = connected.stream()
                    .filter(p -> !p.equals(rootPos))
                    .collect(Collectors.toList());
                memoryBuilder.otherPositions(otherPositions);
            }

            var result = memoryBuilder.toResult(currentKey.get(), pos);
            memoryBank.addMemory(result.key(), result.position(), result.memory());

        } catch (Exception e) {
            ChestTracker.LOGGER.error("ServuxContainerSync: error saving container at {}", pos, e);
        }
    }

    private void finishSync() {
        int savedCount = receivedData.size();
        ChestTracker.LOGGER.info("ServuxContainerSync: sync completed, saved {} containers", savedCount);
        sendChatMessage(Component.translatable("chesttracker.servux.syncComplete", savedCount, totalContainers));
        resetSync();
    }

    private long getSyncTimeoutMs() {
        return ChestTrackerConfig.INSTANCE.instance().rendering.servuxSyncTimeout * 1000L;
    }

    private void resetSync() {
        isSyncing = false;
        pendingRequests.clear();
        receivedData.clear();
        totalContainers = 0;
        processedContainers = 0;
        syncStartTime = 0;
    }

    public void updateActionBarProgress() {
        if (!isSyncing || mc.player == null) {
            return;
        }

        if (System.currentTimeMillis() - syncStartTime > getSyncTimeoutMs()) {
            ChestTracker.LOGGER.warn("ServuxContainerSync: sync timeout");
            sendChatMessage(Component.translatable("chesttracker.servux.syncTimeout"));
            resetSync();
            return;
        }

        Component message = Component.translatable("chesttracker.servux.syncProgress",
                Component.literal(processedContainers + "/" + totalContainers).withStyle(ChatFormatting.GOLD));
        mc.player.sendOverlayMessage(message);
    }

    private void sendChatMessage(Component message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(message);
        }
    }
}
