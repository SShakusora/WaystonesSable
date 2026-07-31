package com.sshakusora.waystonessable.compat;

import com.mojang.datafixers.util.Either;
import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.network.SableTeleportPayload;
import com.sshakusora.waystonessable.network.SubLevelGuardPayload;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

@SuppressWarnings("all")
public final class SableWaystoneCompat {

    private static volatile Predicate<Waystone> clientWaystoneSubLevelProvider = waystone -> false;

    private SableWaystoneCompat() {
    }

    public static void setClientWaystoneSubLevelProvider(Predicate<Waystone> provider) {
        clientWaystoneSubLevelProvider = provider;
    }

    public static Optional<ServerSubLevel> assembleWaystone(ServerLevel level, BlockPos waystonePartPos) {
        return collectWaystoneAssemblyBlocks(level, waystonePartPos)
                .map(assembly -> SubLevelAssemblyHelper.assembleBlocks(
                        level,
                        assembly.anchor(),
                        assembly.blocks(),
                        assembly.bounds()
                ));
    }

    public static Optional<WaystoneAssemblyBlocks> collectWaystoneAssemblyBlocks(Level level, BlockPos waystonePartPos) {
        BlockState state = level.getBlockState(waystonePartPos);
        if (!state.hasProperty(WaystoneBlockBase.HALF)) {
            return Optional.empty();
        }

        BlockPos lowerPos = state.getValue(WaystoneBlockBase.HALF) == DoubleBlockHalf.LOWER
                ? waystonePartPos
                : waystonePartPos.below();
        BlockPos upperPos = lowerPos.above();

        BlockState lowerState = level.getBlockState(lowerPos);
        BlockState upperState = level.getBlockState(upperPos);
        if (!isWaystoneHalf(lowerState, DoubleBlockHalf.LOWER)
                || !isWaystoneHalf(upperState, DoubleBlockHalf.UPPER)
                || lowerState.getBlock() != upperState.getBlock()) {
            return Optional.empty();
        }

        Set<BlockPos> blocks = new LinkedHashSet<>();
        blocks.add(lowerPos);
        blocks.add(upperPos);

        BoundingBox3i bounds = new BoundingBox3i(
                lowerPos.getX(),
                lowerPos.getY(),
                lowerPos.getZ(),
                upperPos.getX(),
                upperPos.getY(),
                upperPos.getZ()
        );
        return Optional.of(new WaystoneAssemblyBlocks(lowerPos, blocks, bounds));
    }

    public static boolean isWaystoneAttachedTowards(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (direction == null || !state.hasProperty(WaystoneBlockBase.HALF)) {
            return false;
        }

        DoubleBlockHalf half = state.getValue(WaystoneBlockBase.HALF);
        if (half == DoubleBlockHalf.LOWER) {
            if (direction != Direction.UP) {
                return false;
            }

            BlockState upperState = level.getBlockState(pos.above());
            return isWaystoneHalf(upperState, DoubleBlockHalf.UPPER)
                    && state.getBlock() == upperState.getBlock();
        }

        if (direction != Direction.DOWN) {
            return false;
        }

        BlockState lowerState = level.getBlockState(pos.below());
        return isWaystoneHalf(lowerState, DoubleBlockHalf.LOWER)
                && state.getBlock() == lowerState.getBlock();
    }

    public static ExpandedWaystoneAssembly expandWaystoneAssemblyBlocks(Level level, Iterable<BlockPos> blocks, BoundingBox3ic bounds) {
        Set<BlockPos> expandedBlocks = new LinkedHashSet<>();
        int originalBlockCount = 0;
        for (BlockPos block : blocks) {
            if (block == null) {
                continue;
            }
            if (expandedBlocks.add(block.immutable())) {
                originalBlockCount++;
            }
        }

        int addedWaystoneParts = 0;
        List<BlockPos> originalSnapshot = new ArrayList<>(expandedBlocks);
        for (BlockPos block : originalSnapshot) {
            Optional<WaystoneAssemblyBlocks> waystoneAssembly = collectWaystoneAssemblyBlocks(level, block);
            if (waystoneAssembly.isEmpty()) {
                continue;
            }

            for (BlockPos waystonePart : waystoneAssembly.get().blocks()) {
                if (expandedBlocks.add(waystonePart.immutable())) {
                    addedWaystoneParts++;
                }
            }
        }

        BoundingBox3i expandedBounds = expandBounds(bounds, expandedBlocks);
        return new ExpandedWaystoneAssembly(
                List.copyOf(expandedBlocks),
                expandedBounds,
                originalBlockCount,
                addedWaystoneParts
        );
    }

    private static BoundingBox3i expandBounds(BoundingBox3ic bounds, Set<BlockPos> blocks) {
        if (bounds == null && blocks.isEmpty()) {
            return new BoundingBox3i();
        }

        int minX;
        int minY;
        int minZ;
        int maxX;
        int maxY;
        int maxZ;
        if (bounds != null) {
            minX = bounds.minX();
            minY = bounds.minY();
            minZ = bounds.minZ();
            maxX = bounds.maxX();
            maxY = bounds.maxY();
            maxZ = bounds.maxZ();
        } else {
            BlockPos first = blocks.iterator().next();
            minX = maxX = first.getX();
            minY = maxY = first.getY();
            minZ = maxZ = first.getZ();
        }

        for (BlockPos block : blocks) {
            minX = Math.min(minX, block.getX());
            minY = Math.min(minY, block.getY());
            minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX());
            maxY = Math.max(maxY, block.getY());
            maxZ = Math.max(maxZ, block.getZ());
        }
        return new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isWaystoneHalf(BlockState state, DoubleBlockHalf half) {
        return state.getBlock() instanceof WaystoneBlockBase
                && state.hasProperty(WaystoneBlockBase.HALF)
                && state.getValue(WaystoneBlockBase.HALF) == half;
    }

    public record WaystoneAssemblyBlocks(BlockPos anchor, Set<BlockPos> blocks, BoundingBox3i bounds) {
    }

    public record ExpandedWaystoneAssembly(List<BlockPos> blocks, BoundingBox3i bounds, int originalBlockCount, int addedWaystoneParts) {
        public boolean changed() {
            return addedWaystoneParts > 0;
        }

        public int expandedBlockCount() {
            return blocks.size();
        }
    }

    public static Vec3 projectToVisible(Level level, Vec3 pos) {
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, (Position) pos);
    }

    public static Optional<Vec3> resolveVisibleTeleportPos(Level level, Vec3 waystoneTargetPos, Waystone targetWaystone) {
        SubLevelAccess targetSubLevel = SableCompanion.INSTANCE.getContaining(level, waystoneTargetPos);
        if (targetSubLevel == null) {
            SubLevelData storedTarget = level instanceof ServerLevel serverLevel
                    ? getTeleportTargetData(serverLevel, targetWaystone)
                    : null;
            if (storedTarget == null) {
                return isInternalPlotPosition(level, waystoneTargetPos)
                        ? Optional.empty()
                        : Optional.of(waystoneTargetPos);
            }

            return Optional.of(transformStoredTargetPos(storedTarget, getFeetStoragePos(waystoneTargetPos)));
        }

        return Optional.of(targetSubLevel.logicalPose().transformPosition(getFeetStoragePos(waystoneTargetPos)));
    }

    /**
     * Compatibility helper for callers that have already established that the target is resolvable.
     * Teleport event handling must use {@link #resolveVisibleTeleportPos(Level, Vec3, Waystone)} so an
     * orphaned Sable plot coordinate can fail closed instead of being used as a real-world destination.
     */
    public static Vec3 getVisibleTeleportPos(Level level, Vec3 waystoneTargetPos, Waystone targetWaystone) {
        return resolveVisibleTeleportPos(level, waystoneTargetPos, targetWaystone)
                .orElseThrow(() -> new IllegalStateException("Unresolvable Sable plot teleport target at " + waystoneTargetPos));
    }

    public static Vec3 getFeetStoragePos(Vec3 waystoneTargetPos) {
        return new Vec3(waystoneTargetPos.x, waystoneTargetPos.y - 0.5, waystoneTargetPos.z);
    }

    public static boolean isTrackedSubLevelTeleportTarget(ServerLevel level, Waystone waystone) {
        if (!level.dimension().equals(waystone.getDimension())) {
            return false;
        }

        if (SableCompanion.INSTANCE.getContaining(level, waystone.getPos()) != null) {
            return true;
        }

        TrackingPoint trackingPoint = getTrackingPoint(level, waystone);
        if (trackingPoint == null || !trackingPoint.inSubLevel()) {
            return false;
        }

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }

        if (trackingPoint.subLevelID() != null && container.getSubLevel(trackingPoint.subLevelID()) != null) {
            return true;
        }

        if (trackingPoint.subLevelID() != null
                && container.getHoldingChunkMap().getHoldingSubLevel(trackingPoint.subLevelID()) != null) {
            return true;
        }

        // Trust the exact pointer metadata here. Validation must not synchronously open Sable files;
        // Prepare performs the one authoritative restore and fails closed if the pointer is stale.
        return trackingPoint.subLevelID() != null && trackingPoint.lastSavedSubLevelPointer() != null;
    }

    public static boolean isWaystoneOnSubLevel(MinecraftServer server, Waystone waystone) {
        ServerLevel level = server.getLevel(waystone.getDimension());
        if (level == null) {
            return false;
        }

        if (SableCompanion.INSTANCE.getContaining(level, waystone.getPos()) != null) {
            return true;
        }

        return hasTrackedSubLevel(level, waystone);
    }

    public static boolean isWaystoneOnSubLevel(Waystone waystone) {
        MinecraftServer server = Balm.getHooks().getServer();
        if (server != null) {
            return isWaystoneOnSubLevel(server, waystone);
        }

        return clientWaystoneSubLevelProvider.test(waystone);
    }

    public static boolean isWaystoneOnSubLevel(Level level, BlockPos pos) {
        if (SableCompanion.INSTANCE.getContaining(level, pos) != null) {
            return true;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && container.getPlot(pos.getX() >> 4, pos.getZ() >> 4) != null) {
            return true;
        }

        return isOccupiedPlot(container, pos);
    }

    private static boolean hasTrackedSubLevel(ServerLevel level, Waystone waystone) {
        TrackingPoint trackingPoint = getTrackingPoint(level, waystone);
        if (trackingPoint == null || !trackingPoint.inSubLevel()) {
            return false;
        }

        SubLevelAccess loadedSubLevel = SableCompanion.INSTANCE.getContaining(level, waystone.getPos());
        if (loadedSubLevel != null && loadedSubLevel.getUniqueId().equals(trackingPoint.subLevelID())) {
            return true;
        }

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container != null && trackingPoint.subLevelID() != null) {
            if (container.getSubLevel(trackingPoint.subLevelID()) != null) {
                return true;
            }

            HoldingSubLevel holdingSubLevel = container.getHoldingChunkMap()
                    .getHoldingSubLevel(trackingPoint.subLevelID());
            if (holdingSubLevel != null && matchesTrackingPoint(trackingPoint, holdingSubLevel.data())) {
                return true;
            }
        }

        return trackingPoint.subLevelID() != null || trackingPoint.lastSavedSubLevelPointer() != null || trackingPoint.globalPlaceholderPosition() != null;
    }

    public static Optional<Vec3> resolveVisibleWaystonePos(ServerLevel level, Waystone waystone) {
        Vec3 localPos = waystone.getPos().getCenter();
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, localPos);
        if (subLevel != null) {
            Vec3 visiblePos = subLevel.logicalPose().transformPosition(localPos);
            updateVisiblePositionSnapshot(level, waystone, visiblePos);
            return Optional.of(visiblePos);
        }

        SubLevelData storedTarget = getTeleportTargetData(level, waystone);
        if (storedTarget != null) {
            Vec3 visiblePos = transformStoredTargetPos(storedTarget, localPos);
            updateVisiblePositionSnapshot(level, waystone, visiblePos);
            return Optional.of(visiblePos);
        }

        TrackingPoint trackingPoint = getTrackingPoint(level, waystone);
        if (trackingPoint != null && trackingPoint.inSubLevel()) {
            Optional<Vec3> snapshot = WaystonePositionSnapshotSavedData.getOrLoad(level).get(waystone.getWaystoneUid());
            if (snapshot.isPresent()) {
                return snapshot;
            }
        }

        return isInternalPlotPosition(level, localPos) ? Optional.empty() : Optional.of(localPos);
    }

    public static Vec3 getVisibleWaystonePos(ServerLevel level, Waystone waystone) {
        return resolveVisibleWaystonePos(level, waystone)
                .orElseThrow(() -> new IllegalStateException("Unresolvable Sable plot Waystone at " + waystone.getPos()));
    }

    public static Optional<Vec3> resolveVisibleMenuFallbackPos(ServerLevel level, Waystone waystone) {
        TrackingPoint trackingPoint = getTrackingPoint(level, waystone);
        if (trackingPoint == null || !trackingPoint.inSubLevel()) {
            return Optional.empty();
        }

        if (trackingPoint.globalPlaceholderPosition() != null) {
            Vector3d placeholder = trackingPoint.globalPlaceholderPosition();
            Vec3 position = new Vec3(placeholder.x, placeholder.y, placeholder.z);
            return isInternalPlotPosition(level, position) ? Optional.empty() : Optional.of(position);
        }

        GlobalSavedSubLevelPointer pointer = trackingPoint.lastSavedSubLevelPointer();
        if (pointer == null) {
            return Optional.empty();
        }

        // A Sable storage pointer is grouped by the visible holding chunk. It does not reveal the exact
        // pose without opening the SubLevel file, but it is a safe, nearby menu position for old worlds
        // that predate snapshots. Prepare restores the exact pose before destination resolution.
        ChunkPos holdingChunk = pointer.chunkPos();
        Vec3 position = Vec3.atCenterOf(new BlockPos(
                holdingChunk.getMiddleBlockX(),
                level.getSeaLevel(),
                holdingChunk.getMiddleBlockZ()
        ));
        return isInternalPlotPosition(level, position) ? Optional.empty() : Optional.of(position);
    }

    public static void updateWaystoneTrackingPoint(ServerLevel level, Waystone waystone) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, waystone.getPos());
        SubLevelTrackingPointSavedData trackingPoints = SubLevelTrackingPointSavedData.getOrLoad(level);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            if (!isWaystoneOnSubLevel(level, waystone.getPos())) {
                trackingPoints.removeTrackingPoint(waystone.getWaystoneUid());
            }
            return;
        }

        Vec3 localPos = waystone.getPos().getCenter();
        Vector3d visiblePos = serverSubLevel.logicalPose().transformPosition(new Vector3d(localPos.x, localPos.y, localPos.z));
        Vector3d placeholder = serverSubLevel.getLastSerializationPointer() == null
                ? new Vector3d(visiblePos)
                : null;
        trackingPoints.setTrackingPoint(waystone.getWaystoneUid(), new TrackingPoint(
                true,
                serverSubLevel.getUniqueId(),
                serverSubLevel.getLastSerializationPointer(),
                new Vector3d(localPos.x, localPos.y, localPos.z),
                placeholder
        ));
        updateVisiblePositionSnapshot(level, waystone, new Vec3(visiblePos.x, visiblePos.y, visiblePos.z));
    }

    public static void refreshWaystoneTrackingPointIfLoaded(ServerLevel level, Waystone waystone) {
        if (SableCompanion.INSTANCE.getContaining(level, waystone.getPos()) instanceof ServerSubLevel) {
            updateWaystoneTrackingPoint(level, waystone);
        }
    }

    public static void removeWaystoneTrackingPoint(ServerLevel level, Waystone waystone) {
        SubLevelTrackingPointSavedData.getOrLoad(level).removeTrackingPoint(waystone.getWaystoneUid());
        WaystonePositionSnapshotSavedData.getOrLoad(level).remove(waystone.getWaystoneUid());
    }

    public static void prepareStoredSubLevelForTeleport(WaystoneTeleportEvent.Prepare event) {
        MinecraftServer server = Balm.getHooks().getServer();
        if (server == null) {
            return;
        }

        Waystone targetWaystone = event.getContext().getTargetWaystone();
        ServerLevel targetLevel = server.getLevel(targetWaystone.getDimension());
        if (targetLevel == null) {
            return;
        }

        if (SableCompanion.INSTANCE.getContaining(targetLevel, targetWaystone.getPos()) != null) {
            refreshWaystoneTrackingPointIfLoaded(targetLevel, targetWaystone);
            return;
        }

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(targetLevel);
        TrackingPoint trackingPoint = getTrackingPoint(targetLevel, targetWaystone);
        if (container == null || trackingPoint == null || !trackingPoint.inSubLevel()) {
            if (isInternalPlotPosition(targetLevel, targetWaystone.getPos().getCenter())
                    || WaystonePositionSnapshotSavedData.getOrLoad(targetLevel).get(targetWaystone.getWaystoneUid()).isPresent()) {
                rejectUnresolvablePlotTarget(event, targetWaystone, "no matching SubLevel tracking data was available");
            }
            return;
        }

        if (!tryLoadStoredSubLevelForTeleport(event, targetLevel, targetWaystone, container, trackingPoint)) {
            rejectUnresolvablePlotTarget(event, targetWaystone, "the stored SubLevel could not be restored");
        }
    }

    public static void useLoadedStorageWaystoneChunks(WaystoneTeleportEvent.Prepare event, Waystone storageWaystone) {
        MinecraftServer server = Balm.getHooks().getServer();
        ServerLevel targetLevel = server != null ? server.getLevel(storageWaystone.getDimension()) : null;
        if (targetLevel == null
                || !(SableCompanion.INSTANCE.getContaining(targetLevel, storageWaystone.getPos()) instanceof ServerSubLevel)) {
            return;
        }

        // The menu position is display-only and may be approximate during legacy migration. snatchAndLoad
        // has already fully reconstructed the SubLevel and its chunks, so Waystones must not generate or
        // wait on a second set of chunks for that temporary coordinate.
        event.getChunkPositions().clear();
    }

    private static boolean tryLoadStoredSubLevelForTeleport(
            WaystoneTeleportEvent.Prepare event,
            ServerLevel targetLevel,
            Waystone targetWaystone,
            ServerSubLevelContainer container,
            TrackingPoint trackingPoint
    ) {
        UUID subLevelId = trackingPoint.subLevelID();
        if (subLevelId == null) {
            WaystonesSable.LOGGER.warn(
                    "Cannot restore target Waystone {} because its Sable tracking point has no SubLevel UUID.",
                    targetWaystone.getWaystoneUid()
            );
            return false;
        }

        if (container.getSubLevel(subLevelId) instanceof ServerSubLevel loadedSubLevel) {
            updateVisiblePositionSnapshot(targetLevel, targetWaystone, loadedSubLevel, trackingPoint);
            return true;
        }

        HoldingSubLevel holdingSubLevel = container.getHoldingChunkMap().getHoldingSubLevel(subLevelId);
        GlobalSavedSubLevelPointer pointer = holdingSubLevel != null
                ? holdingSubLevel.pointer()
                : trackingPoint.lastSavedSubLevelPointer();
        if (pointer == null) {
            if (holdingSubLevel == null || !matchesTrackingPoint(trackingPoint, holdingSubLevel.data())) {
                return false;
            }

            WaystonesSable.LOGGER.debug(
                    "Skipping Sable snatch for target Waystone {} (sub-level {}) because it has not been persisted yet.",
                    targetWaystone.getWaystoneUid(),
                    subLevelId
            );
            // The in-memory holding SubLevel is restored by Sable when Waystones requests its plot chunks.
            addSubLevelChunkPositions(event, holdingSubLevel.data());
            updateVisiblePositionSnapshot(
                    targetLevel,
                    targetWaystone,
                    transformStoredTargetPos(holdingSubLevel.data(), new Vec3(
                            trackingPoint.point().x(),
                            trackingPoint.point().y(),
                            trackingPoint.point().z()
                    ))
            );
            return true;
        }

        try {
            container.getHoldingChunkMap().snatchAndLoad(pointer, subLevelId);
            if (!(container.getSubLevel(subLevelId) instanceof ServerSubLevel loadedSubLevel)) {
                WaystonesSable.LOGGER.warn(
                        "Sable did not load SubLevel {} for target Waystone {} from pointer {}.",
                        subLevelId,
                        targetWaystone.getWaystoneUid(),
                        pointer
                );
                return false;
            }
            updateVisiblePositionSnapshot(targetLevel, targetWaystone, loadedSubLevel, trackingPoint);
            return true;
        } catch (RuntimeException exception) {
            WaystonesSable.LOGGER.warn(
                    "Failed to load Sable SubLevel {} for target Waystone {} from pointer {}.",
                    subLevelId,
                    targetWaystone.getWaystoneUid(),
                    pointer,
                    exception
            );
            return false;
        }
    }

    private static void updateVisiblePositionSnapshot(
            ServerLevel level,
            Waystone waystone,
            ServerSubLevel subLevel,
            TrackingPoint trackingPoint
    ) {
        Vector3d visiblePos = subLevel.logicalPose().transformPosition(new Vector3d(trackingPoint.point()));
        updateVisiblePositionSnapshot(level, waystone, new Vec3(visiblePos.x, visiblePos.y, visiblePos.z));
    }

    private static void updateVisiblePositionSnapshot(ServerLevel level, Waystone waystone, Vec3 visiblePos) {
        WaystonePositionSnapshotSavedData.getOrLoad(level).put(waystone.getWaystoneUid(), visiblePos);
    }

    private static void rejectUnresolvablePlotTarget(
            WaystoneTeleportEvent.Prepare event,
            Waystone targetWaystone,
            String reason
    ) {
        WaystonesSable.LOGGER.warn(
                "Refusing to preload unresolved Sable plot coordinates for target Waystone {} at {}: {}.",
                targetWaystone.getWaystoneUid(),
                targetWaystone.getPos(),
                reason
        );
        event.getChunkPositions().clear();
        event.addPreparationTask(result -> CompletableFuture.completedFuture(
                result.right().isPresent()
                        ? result
                        : Either.right(new WaystoneTeleportError.DestinationOutOfBounds())
        ));
    }

    public static long getPlotCoordinate(SubLevel subLevel) {
        SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) {
            return Long.MIN_VALUE;
        }

        ChunkPos plotPos = subLevel.getPlot().plotPos;
        int plotX = plotPos.x - container.getOrigin().x;
        int plotZ = plotPos.z - container.getOrigin().y;
        return ChunkPos.asLong(plotX, plotZ);
    }

    public static void prepareCrossDimensionTeleport(Entity entity, Level targetLevel, Vec3 waystoneTargetPos) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        if (!(targetLevel instanceof ServerLevel serverLevel)) {
            return;
        }

        // Vanilla starts removing chunks from the old dimension while processing
        // ServerPlayer#teleportTo. If the client still considers itself inside a
        // Sable plot, ClientChunkCache#drop deliberately throws. Clear the client
        // plot state first; packets on the play connection retain this ordering.
        player.connection.send(new ClientboundCustomPayloadPacket(new SableTeleportPayload(
                Optional.empty(),
                player.getX(),
                player.getY(),
                player.getZ()
        )));

        long plotCoordinate = getPlotCoordinate(serverLevel, BlockPos.containing(waystoneTargetPos));
        player.connection.send(new ClientboundCustomPayloadPacket(new SubLevelGuardPayload(plotCoordinate)));
    }

    public static void syncTrackingAfterTeleport(Entity entity, Vec3 waystoneTargetPos, boolean dimensionalTeleport, Waystone targetWaystone) {
        EntityMovementExtension movementExtension = (EntityMovementExtension) entity;
        SubLevelAccess targetSubLevel = SableCompanion.INSTANCE.getContaining(entity.level(), waystoneTargetPos);
        SubLevelData storedTarget = targetSubLevel == null && entity.level() instanceof ServerLevel serverLevel
                ? getTeleportTargetData(serverLevel, targetWaystone)
                : null;
        boolean targetIsSubLevel = targetSubLevel != null || storedTarget != null;
        movementExtension.sable$setTrackingSubLevel(targetSubLevel instanceof SubLevel concreteSubLevel ? concreteSubLevel : null);

        if (targetIsSubLevel) {
            resetEntityMotion(entity);
        }

        if (entity instanceof ServerPlayer player) {
            if (targetIsSubLevel) {
                Vec3 localFeetPos = getFeetStoragePos(waystoneTargetPos);
                Vector3d playerAnchor = new Vector3d(localFeetPos.x, localFeetPos.y, localFeetPos.z);
                if (targetSubLevel != null) {
                    ((PlayerFreezeExtension) player).sable$freezeTo(targetSubLevel.getUniqueId(), playerAnchor);
                    player.connection.send(new ClientboundCustomPayloadPacket(new ClientboundFreezePlayerPacket(targetSubLevel.getUniqueId(), playerAnchor)));
                } else {
                    ((PlayerFreezeExtension) player).sable$freezeTo(storedTarget.uuid(), playerAnchor);
                    player.connection.send(new ClientboundCustomPayloadPacket(new ClientboundFreezePlayerPacket(storedTarget.uuid(), playerAnchor)));
                }

                Optional<UUID> targetSubLevelId = targetSubLevel != null
                        ? Optional.of(targetSubLevel.getUniqueId())
                        : Optional.of(storedTarget.uuid());
                SableTeleportPayload payload = new SableTeleportPayload(
                        targetSubLevelId,
                        player.getX(),
                        player.getY(),
                        player.getZ()
                );
                player.connection.send(new ClientboundCustomPayloadPacket(payload));

                if (!dimensionalTeleport) {
                    EntitySubLevelUtil.setOldPosNoMovement(player);
                }
                return;
            }

            SableTeleportPayload payload = new SableTeleportPayload(
                    Optional.ofNullable(targetSubLevel).map(SubLevelAccess::getUniqueId),
                    player.getX(),
                    player.getY(),
                    player.getZ()
            );
            player.connection.send(new ClientboundCustomPayloadPacket(payload));
        }
    }

    private static long getPlotCoordinate(ServerLevel level, BlockPos targetPos) {
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return Long.MIN_VALUE;
        }

        int chunkX = targetPos.getX() >> 4;
        int chunkZ = targetPos.getZ() >> 4;
        if (!container.inBounds(chunkX, chunkZ)) {
            return Long.MIN_VALUE;
        }

        int plotX = (chunkX >> container.getLogPlotSize()) - container.getOrigin().x;
        int plotZ = (chunkZ >> container.getLogPlotSize()) - container.getOrigin().y;
        if (plotX < 0 || plotZ < 0) {
            return Long.MIN_VALUE;
        }

        int index = container.getIndex(plotX, plotZ);
        if (container.getPlot(chunkX, chunkZ) == null
                && !container.getOccupancy().get(index)) {
            return Long.MIN_VALUE;
        }

        return ChunkPos.asLong(plotX, plotZ);
    }

    private static boolean isOccupiedPlot(SubLevelContainer container, BlockPos pos) {
        if (container == null) {
            return false;
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (!container.inBounds(chunkX, chunkZ)) {
            return false;
        }

        int plotX = (chunkX >> container.getLogPlotSize()) - container.getOrigin().x;
        int plotZ = (chunkZ >> container.getLogPlotSize()) - container.getOrigin().y;
        return plotX >= 0 && plotZ >= 0 && container.getOccupancy().get(container.getIndex(plotX, plotZ));
    }

    public static boolean isInternalPlotPosition(Level level, Position pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }

        int chunkX = Mth.floor(pos.x()) >> 4;
        int chunkZ = Mth.floor(pos.z()) >> 4;
        return container.inBounds(chunkX, chunkZ);
    }

    private static void addSubLevelChunkPositions(WaystoneTeleportEvent.Prepare event, SubLevelData data) {
        BoundingBox3dc bounds = data.bounds();
        int minChunkX = Mth.floor(bounds.minX() - 1.0) >> 4;
        int maxChunkX = Mth.floor(bounds.maxX() + 1.0) >> 4;
        int minChunkZ = Mth.floor(bounds.minZ() - 1.0) >> 4;
        int maxChunkZ = Mth.floor(bounds.maxZ() + 1.0) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                event.addChunkPosition(new ChunkPos(chunkX, chunkZ));
            }
        }
    }

    private static StoredSubLevelTarget resolveStoredSubLevelTarget(ServerLevel level, Waystone waystone) {
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        TrackingPoint trackingPoint = getTrackingPoint(level, waystone);
        return resolveStoredSubLevelTarget(container, trackingPoint);
    }

    private static StoredSubLevelTarget resolveStoredSubLevelTarget(ServerSubLevelContainer container, TrackingPoint trackingPoint) {
        if (trackingPoint == null || !trackingPoint.inSubLevel()) {
            return null;
        }

        if (trackingPoint.subLevelID() != null) {
            HoldingSubLevel holdingSubLevel = container.getHoldingChunkMap().getHoldingSubLevel(trackingPoint.subLevelID());
            if (holdingSubLevel != null && matchesTrackingPoint(trackingPoint, holdingSubLevel.data())) {
                return new StoredSubLevelTarget(holdingSubLevel.pointer(), holdingSubLevel.data());
            }
        }

        return null;
    }

    private static SubLevelData getTeleportTargetData(ServerLevel level, Waystone waystone) {
        if (!level.dimension().equals(waystone.getDimension())) {
            return null;
        }

        StoredSubLevelTarget storedTarget = resolveStoredSubLevelTarget(level, waystone);
        return storedTarget != null ? storedTarget.data() : null;
    }

    private static TrackingPoint getTrackingPoint(ServerLevel level, Waystone waystone) {
        return SubLevelTrackingPointSavedData.getOrLoad(level).getTrackingPoint(waystone.getWaystoneUid());
    }

    private static boolean matchesTrackingPoint(TrackingPoint trackingPoint, SubLevelData data) {
        if (trackingPoint == null || data == null) {
            return false;
        }

        UUID trackedSubLevelId = trackingPoint.subLevelID();
        return trackedSubLevelId == null || trackedSubLevelId.equals(data.uuid());
    }

    private static Vec3 transformStoredTargetPos(SubLevelData data, Vec3 localPos) {
        Vector3d transformed = data.pose().transformPosition(new Vector3d(localPos.x, localPos.y, localPos.z));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    private static void resetEntityMotion(Entity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;

        if (entity instanceof LivingEntityMovementExtension livingMovementExtension) {
            livingMovementExtension.sable$getInheritedVelocity().zero();
        }
    }

    private record StoredSubLevelTarget(GlobalSavedSubLevelPointer pointer, SubLevelData data) {
    }
}
