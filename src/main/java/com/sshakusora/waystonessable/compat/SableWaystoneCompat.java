package com.sshakusora.waystonessable.compat;

import com.sshakusora.waystonessable.mixin.accessor.SubLevelHoldingChunkAccessor;
import com.sshakusora.waystonessable.mixin.accessor.SubLevelHoldingChunkMapAccessor;
import com.sshakusora.waystonessable.network.SableTeleportPayload;
import com.sshakusora.waystonessable.network.SubLevelGuardPayload;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.tag.ModBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@SuppressWarnings("all")
public final class SableWaystoneCompat {

    private static final Map<UUID, WarpPlateArrivalGuard> WARP_PLATE_ARRIVAL_GUARDS = new ConcurrentHashMap<>();
    private static final Map<MovingBlockKey, Boolean> MOVING_WAYSTONES = new ConcurrentHashMap<>();

    private SableWaystoneCompat() {
    }

    public static Vec3 projectToVisible(Level level, Vec3 pos) {
        return Sable.HELPER.projectOutOfSubLevel(level, pos);
    }

    public static double getWaystoneDistanceSqr(Player player, Waystone waystone) {
        if (waystone.getDimension() != player.level().dimension()) {
            return waystone.getPos().distToCenterSqr(player.getX(), player.getY(), player.getZ());
        }

        Vec3 playerPos = projectToVisible(player.level(), player.position());
        Vec3 waystonePos = projectToVisible(player.level(), waystone.getPos().getCenter());
        return playerPos.distanceToSqr(waystonePos);
    }

    public static double getWaystoneDistance(Player player, Waystone waystone) {
        return Math.sqrt(getWaystoneDistanceSqr(player, waystone));
    }

    public static boolean isEntityInsideBlock(Level level, BlockPos blockPos, Entity entity) {
        Vec3 entityPos = entity.position();
        SubLevel blockSubLevel = Sable.HELPER.getContaining(level, blockPos);
        if (blockSubLevel != null) {
            entityPos = blockSubLevel.logicalPose().transformPositionInverse(entityPos);
        }

        return entityPos.x >= blockPos.getX() && entityPos.x < blockPos.getX() + 1
                && entityPos.y >= blockPos.getY() && entityPos.y < blockPos.getY() + 1
                && entityPos.z >= blockPos.getZ() && entityPos.z < blockPos.getZ() + 1;
    }

    public static List<Entity> getEntitiesInsideBlock(Level level, BlockPos blockPos) {
        return getEntitiesInsideBlock(level, blockPos, EntitySelector.ENTITY_STILL_ALIVE);
    }

    public static List<Entity> getEntitiesInsideBlock(Level level, BlockPos blockPos, Predicate<? super Entity> predicate) {
        if (Sable.HELPER.getContaining(level, blockPos) == null) {
            AABB bounds = new AABB(
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ(),
                    blockPos.getX() + 1,
                    blockPos.getY() + 1,
                    blockPos.getZ() + 1
            );
            return level.getEntities((Entity) null, bounds, entity ->
                    EntitySelector.ENTITY_STILL_ALIVE.test(entity) && predicate.test(entity)
            );
        }

        Vec3 visibleCenter = projectToVisible(level, blockPos.getCenter());
        AABB searchBounds = new AABB(visibleCenter, visibleCenter).inflate(2.0);
        return level.getEntities((Entity) null, searchBounds, entity ->
                EntitySelector.ENTITY_STILL_ALIVE.test(entity)
                        && predicate.test(entity)
                        && isEntityInsideBlock(level, blockPos, entity)
        );
    }

    public static void registerWarpPlateArrivalGuard(Entity entity, Level level, BlockPos blockPos) {
        WARP_PLATE_ARRIVAL_GUARDS.put(entity.getUUID(), new WarpPlateArrivalGuard(level.dimension(), blockPos.immutable()));
    }

    public static void markMovingWaystone(Level level, BlockPos blockPos) {
        MOVING_WAYSTONES.put(new MovingBlockKey(level.dimension(), blockPos.immutable()), Boolean.TRUE);
    }

    public static boolean consumeMovingWaystone(Level level, BlockPos blockPos) {
        return MOVING_WAYSTONES.remove(new MovingBlockKey(level.dimension(), blockPos.immutable())) != null;
    }

    public static boolean consumeWarpPlateArrivalGuard(Level level, BlockPos blockPos, Entity entity) {
        WarpPlateArrivalGuard guard = WARP_PLATE_ARRIVAL_GUARDS.get(entity.getUUID());
        if (guard == null) {
            return false;
        }

        if (guard.dimension != level.dimension() || !guard.blockPos.equals(blockPos)) {
            return false;
        }

        if (!isEntityInsideBlock(level, blockPos, entity)) {
            return false;
        }

        WARP_PLATE_ARRIVAL_GUARDS.remove(entity.getUUID(), guard);
        return true;
    }

    public static boolean hasWarpPlateArrivalGuard(Level level, BlockPos blockPos, Entity entity) {
        WarpPlateArrivalGuard guard = WARP_PLATE_ARRIVAL_GUARDS.get(entity.getUUID());
        return guard != null && guard.dimension == level.dimension() && guard.blockPos.equals(blockPos);
    }

    public static void clearWarpPlateArrivalGuardIfNeeded(Level level, BlockPos blockPos, Entity entity) {
        WarpPlateArrivalGuard guard = WARP_PLATE_ARRIVAL_GUARDS.get(entity.getUUID());
        if (guard == null) {
            return;
        }

        if (guard.dimension != level.dimension() || !guard.blockPos.equals(blockPos)) {
            return;
        }

        if (!isEntityInsideBlock(level, blockPos, entity)) {
            WARP_PLATE_ARRIVAL_GUARDS.remove(entity.getUUID(), guard);
        }
    }

    public static Vec3 getVisibleTeleportPos(Level level, Vec3 waystoneTargetPos) {
        SubLevel targetSubLevel = Sable.HELPER.getContaining(level, waystoneTargetPos);
        if (targetSubLevel == null) {
            SubLevelData storedTarget = level instanceof ServerLevel serverLevel
                    ? findStoredSubLevelData(serverLevel, BlockPos.containing(waystoneTargetPos))
                    : null;
            if (storedTarget == null) {
                return waystoneTargetPos;
            }

            return transformStoredTargetPos(storedTarget, getFeetStoragePos(waystoneTargetPos));
        }

        return targetSubLevel.logicalPose().transformPosition(getFeetStoragePos(waystoneTargetPos));
    }

    public static Vec3 getFeetStoragePos(Vec3 waystoneTargetPos) {
        return new Vec3(waystoneTargetPos.x, waystoneTargetPos.y - 0.5, waystoneTargetPos.z);
    }

    public static boolean isWaystoneValidInLevel(ServerLevel level, Waystone waystone) {
        BlockPos pos = waystone.getPos();

        if (level.getBlockState(pos).is(ModBlockTags.IS_TELEPORT_TARGET)) {
            return true;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }

        if (container.getPlot(pos.getX() >> 4, pos.getZ() >> 4) != null) {
            return true;
        }

        return findStoredSubLevelData(level, pos) != null;
    }

    public static boolean isWaystoneOnSubLevel(MinecraftServer server, Waystone waystone) {
        ServerLevel level = server.getLevel(waystone.getDimension());
        if (level == null) {
            return false;
        }

        return isWaystoneOnSubLevel(level, waystone.getPos());
    }

    public static boolean isWaystoneOnSubLevel(ServerLevel level, BlockPos pos) {
        if (Sable.HELPER.getContaining(level, pos) != null) {
            return true;
        }

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container != null && container.getPlot(pos.getX() >> 4, pos.getZ() >> 4) != null) {
            return true;
        }

        return findStoredSubLevelData(level, pos) != null;
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

        long plotCoordinate = getPlotCoordinate(serverLevel, BlockPos.containing(waystoneTargetPos));
        if (plotCoordinate != Long.MIN_VALUE) {
            player.connection.send(new ClientboundCustomPayloadPacket(new SubLevelGuardPayload(plotCoordinate)));
        }
    }

    public static void syncTrackingAfterTeleport(Entity entity, Vec3 waystoneTargetPos, boolean dimensionalTeleport) {
        EntityMovementExtension movementExtension = (EntityMovementExtension) entity;
        SubLevel targetSubLevel = Sable.HELPER.getContaining(entity.level(), waystoneTargetPos);
        SubLevelData storedTarget = targetSubLevel == null && entity.level() instanceof ServerLevel serverLevel
                ? findStoredSubLevelData(serverLevel, BlockPos.containing(waystoneTargetPos))
                : null;
        boolean targetIsSubLevel = targetSubLevel != null || storedTarget != null;
        movementExtension.sable$setTrackingSubLevel(targetSubLevel);

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

                SableTeleportPayload payload = new SableTeleportPayload(
                        Optional.ofNullable(targetSubLevel).map(SubLevel::getUniqueId),
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
                    Optional.ofNullable(targetSubLevel).map(SubLevel::getUniqueId),
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
                && !container.getOccupancy().get(index)
                && findStoredSubLevelData(level, targetPos) == null) {
            return Long.MIN_VALUE;
        }

        return ChunkPos.asLong(plotX, plotZ);
    }

    private static SubLevelData findStoredSubLevelData(ServerLevel level, BlockPos targetPos) {
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        int chunkX = targetPos.getX() >> 4;
        int chunkZ = targetPos.getZ() >> 4;
        if (!container.inBounds(chunkX, chunkZ)) {
            return null;
        }

        int plotX = (chunkX >> container.getLogPlotSize()) - container.getOrigin().x;
        int plotZ = (chunkZ >> container.getLogPlotSize()) - container.getOrigin().y;
        if (plotX < 0 || plotZ < 0) {
            return null;
        }

        if (!container.getOccupancy().get(container.getIndex(plotX, plotZ))) {
            return null;
        }

        SubLevelData loadedHoldingData = findLoadedHoldingSubLevel(container, plotX, plotZ);
        if (loadedHoldingData != null) {
            return loadedHoldingData;
        }

        return findStoredSubLevelData(container, plotX, plotZ);
    }

    private static SubLevelData findStoredSubLevelData(ServerSubLevelContainer container, int localPlotX, int localPlotZ) {
        SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        File[] regionFiles = storage.getFolder().toFile().listFiles((dir, name) -> name.endsWith(SubLevelRegionFile.FILE_EXTENSION));
        if (regionFiles == null) {
            return null;
        }

        for (File regionFile : regionFiles) {
            String fileName = regionFile.getName();
            String withoutExtension = fileName.substring(0, fileName.length() - SubLevelRegionFile.FILE_EXTENSION.length());
            String[] parts = withoutExtension.split("\\.");
            if (parts.length != 3) {
                continue;
            }

            int regionX;
            int regionZ;
            try {
                regionX = Integer.parseInt(parts[1]);
                regionZ = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            for (int localX = 0; localX < SubLevelRegionFile.SIDE_LENGTH; localX++) {
                for (int localZ = 0; localZ < SubLevelRegionFile.SIDE_LENGTH; localZ++) {
                    ChunkPos chunkPos = new ChunkPos(
                            regionX * SubLevelRegionFile.SIDE_LENGTH + localX,
                            regionZ * SubLevelRegionFile.SIDE_LENGTH + localZ
                    );

                    SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(chunkPos);
                    if (holdingChunk == null) {
                        continue;
                    }

                    for (SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
                        SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
                        if (data == null) {
                            continue;
                        }

                        BlockPos plotPos = readPlotPos(data);
                        if (plotPos == null) {
                            continue;
                        }

                        if (plotPos.getX() != localPlotX || plotPos.getZ() != localPlotZ) {
                            continue;
                        }

                        return data;
                    }
                }
            }
        }

        return null;
    }

    private static SubLevelData findLoadedHoldingSubLevel(ServerSubLevelContainer container, int localPlotX, int localPlotZ) {
        SubLevelHoldingChunkMapAccessor mapAccessor = (SubLevelHoldingChunkMapAccessor) container.getHoldingChunkMap();

        for (SubLevelHoldingChunk loadedChunk : mapAccessor.waystonesSable$getLoadedHoldingChunks().values()) {
            SubLevelHoldingChunkAccessor chunkAccessor = (SubLevelHoldingChunkAccessor) loadedChunk;
            for (HoldingSubLevel holdingSubLevel : chunkAccessor.waystonesSable$getLoadedHoldingSubLevels().values()) {
                BlockPos plotPos = readPlotPos(holdingSubLevel.data());
                if (plotPos == null || plotPos.getX() != localPlotX || plotPos.getZ() != localPlotZ) {
                    continue;
                }

                return holdingSubLevel.data();
            }
        }

        return null;
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

    private static BlockPos readPlotPos(SubLevelData data) {
        if (!data.fullTag().contains("plot")) {
            return null;
        }

        var plotTag = data.fullTag().getCompound("plot");
        if (!plotTag.contains("plot_x") || !plotTag.contains("plot_z")) {
            return null;
        }

        return new BlockPos(plotTag.getInt("plot_x"), 0, plotTag.getInt("plot_z"));
    }

    private record WarpPlateArrivalGuard(ResourceKey<Level> dimension, BlockPos blockPos) {
    }

    private record MovingBlockKey(ResourceKey<Level> dimension, BlockPos blockPos) {
    }
}
