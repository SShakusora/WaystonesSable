package com.sshakusora.waystonessable.compat;

import com.sshakusora.waystonessable.network.SableTeleportPayload;
import com.sshakusora.waystonessable.network.SubLevelGuardPayload;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.tag.ModBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
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
    private static final ThreadLocal<TeleportTargetContext> TELEPORT_TARGET = new ThreadLocal<>();

    @FunctionalInterface
    public interface ClientDistanceProvider {
        double getDistanceSqr(Player player, Waystone waystone);
    }

    private static volatile ClientDistanceProvider clientDistanceProvider;

    private SableWaystoneCompat() {
    }

    public static void setClientDistanceProvider(ClientDistanceProvider provider) {
        clientDistanceProvider = provider;
    }

    public static Vec3 projectToVisible(Level level, Vec3 pos) {
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, (Position) pos);
    }

    public static double getWaystoneDistanceSqr(Player player, Waystone waystone) {
        if (player.level().isClientSide() && clientDistanceProvider != null) {
            return clientDistanceProvider.getDistanceSqr(player, waystone);
        }

        if (waystone.getDimension() != player.level().dimension()) {
            return waystone.getPos().distToCenterSqr(player.getX(), player.getY(), player.getZ());
        }

        return SableCompanion.INSTANCE.distanceSquaredWithSubLevels(
                player.level(),
                player.position(),
                waystone.getPos().getCenter()
        );
    }

    public static double getWaystoneDistance(Player player, Waystone waystone) {
        return Math.sqrt(getWaystoneDistanceSqr(player, waystone));
    }

    public static boolean isEntityInsideBlock(Level level, BlockPos blockPos, Entity entity) {
        Vec3 entityPos = entity.position();
        SubLevelAccess blockSubLevel = SableCompanion.INSTANCE.getContaining(level, blockPos);
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
        if (SableCompanion.INSTANCE.getContaining(level, blockPos) == null) {
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
        SubLevelAccess targetSubLevel = SableCompanion.INSTANCE.getContaining(level, waystoneTargetPos);
        if (targetSubLevel == null) {
            SubLevelData storedTarget = level instanceof ServerLevel serverLevel
                    ? getTeleportTargetData(serverLevel)
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

        return isOccupiedPlot(container, pos);
    }

    public static boolean isWaystoneOnSubLevel(MinecraftServer server, Waystone waystone) {
        ServerLevel level = server.getLevel(waystone.getDimension());
        if (level == null) {
            return false;
        }

        return isWaystoneOnSubLevel(level, waystone.getPos());
    }

    public static boolean isWaystoneOnSubLevel(ServerLevel level, BlockPos pos) {
        if (SableCompanion.INSTANCE.getContaining(level, pos) != null) {
            return true;
        }

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container != null && container.getPlot(pos.getX() >> 4, pos.getZ() >> 4) != null) {
            return true;
        }

        return isOccupiedPlot(container, pos);
    }

    public static Vec3 getVisibleWaystonePos(ServerLevel level, Waystone waystone) {
        Vec3 localPos = waystone.getPos().getCenter();
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, localPos);
        if (subLevel != null) {
            return projectToVisible(level, localPos);
        }

        TrackingPoint trackingPoint = SubLevelTrackingPointSavedData.getOrLoad(level)
                .getTrackingPoint(waystone.getWaystoneUid());
        if (trackingPoint != null && trackingPoint.inSubLevel() && trackingPoint.subLevelID() != null) {
            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            HoldingSubLevel holdingSubLevel = container != null
                    ? container.getHoldingChunkMap().getHoldingSubLevel(trackingPoint.subLevelID())
                    : null;
            if (holdingSubLevel != null) {
                Vector3d transformed = holdingSubLevel.data().pose().transformPosition(new Vector3d(localPos.x, localPos.y, localPos.z));
                return new Vec3(transformed.x, transformed.y, transformed.z);
            }
        }

        return localPos;
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
        Vector3d placeholder = serverSubLevel.getLastSerializationPointer() == null
                ? serverSubLevel.logicalPose().transformPosition(new Vector3d(localPos.x, localPos.y, localPos.z))
                : null;
        trackingPoints.setTrackingPoint(waystone.getWaystoneUid(), new TrackingPoint(
                true,
                serverSubLevel.getUniqueId(),
                serverSubLevel.getLastSerializationPointer(),
                new Vector3d(localPos.x, localPos.y, localPos.z),
                placeholder
        ));
    }

    public static void removeWaystoneTrackingPoint(ServerLevel level, Waystone waystone) {
        SubLevelTrackingPointSavedData.getOrLoad(level).removeTrackingPoint(waystone.getWaystoneUid());
    }

    public static void beginTeleport(Waystone targetWaystone) {
        TELEPORT_TARGET.set(new TeleportTargetContext(targetWaystone));
    }

    public static void endTeleport() {
        TELEPORT_TARGET.remove();
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

    public static void syncTrackingAfterTeleport(Entity entity, Vec3 waystoneTargetPos, boolean dimensionalTeleport) {
        EntityMovementExtension movementExtension = (EntityMovementExtension) entity;
        SubLevelAccess targetSubLevel = SableCompanion.INSTANCE.getContaining(entity.level(), waystoneTargetPos);
        SubLevelData storedTarget = targetSubLevel == null && entity.level() instanceof ServerLevel serverLevel
                ? getTeleportTargetData(serverLevel)
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

    private static SubLevelData getTeleportTargetData(ServerLevel level) {
        TeleportTargetContext context = TELEPORT_TARGET.get();
        if (context == null || context.waystone.getDimension() != level.dimension()) {
            return null;
        }
        if (context.resolved) {
            return context.data;
        }

        context.resolved = true;
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        TrackingPoint trackingPoint = SubLevelTrackingPointSavedData.getOrLoad(level)
                .getTrackingPoint(context.waystone.getWaystoneUid());
        if (trackingPoint == null || !trackingPoint.inSubLevel()) {
            return null;
        }

        if (trackingPoint.subLevelID() != null) {
            HoldingSubLevel holdingSubLevel = container.getHoldingChunkMap().getHoldingSubLevel(trackingPoint.subLevelID());
            if (holdingSubLevel != null) {
                context.data = holdingSubLevel.data();
                return context.data;
            }
        }

        if (trackingPoint.lastSavedSubLevelPointer() != null) {
            var pointer = trackingPoint.lastSavedSubLevelPointer();
            context.data = container.getHoldingChunkMap().getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
        }
        return context.data;
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

    private record WarpPlateArrivalGuard(ResourceKey<Level> dimension, BlockPos blockPos) {
    }

    private record MovingBlockKey(ResourceKey<Level> dimension, BlockPos blockPos) {
    }

    private static final class TeleportTargetContext {
        private final Waystone waystone;
        private boolean resolved;
        private SubLevelData data;

        private TeleportTargetContext(Waystone waystone) {
            this.waystone = waystone;
        }
    }
}
