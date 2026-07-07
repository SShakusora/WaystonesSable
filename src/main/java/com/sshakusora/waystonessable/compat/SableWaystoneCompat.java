package com.sshakusora.waystonessable.compat;

import com.sshakusora.waystonessable.network.SableTeleportPayload;
import com.sshakusora.waystonessable.network.SubLevelGuardPayload;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
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
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Optional;
import java.util.UUID;
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

    public static Vec3 projectToVisible(Level level, Vec3 pos) {
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, (Position) pos);
    }

    public static Vec3 getVisibleTeleportPos(Level level, Vec3 waystoneTargetPos, Waystone targetWaystone) {
        SubLevelAccess targetSubLevel = SableCompanion.INSTANCE.getContaining(level, waystoneTargetPos);
        if (targetSubLevel == null) {
            SubLevelData storedTarget = level instanceof ServerLevel serverLevel
                    ? getTeleportTargetData(serverLevel, targetWaystone)
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
        TrackingPoint trackingPoint = SubLevelTrackingPointSavedData.getOrLoad(level)
                .getTrackingPoint(waystone.getWaystoneUid());
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
            if (holdingSubLevel != null) {
                return true;
            }
        }

        return trackingPoint.subLevelID() != null || trackingPoint.lastSavedSubLevelPointer() != null || trackingPoint.globalPlaceholderPosition() != null;
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

    public static void prepareStoredSubLevelForTeleport(WaystoneTeleportEvent.Prepare event) {
        MinecraftServer server = Balm.getHooks().getServer();
        if (server == null) {
            return;
        }

        Waystone targetWaystone = event.getContext().getTargetWaystone();
        ServerLevel targetLevel = server.getLevel(targetWaystone.getDimension());
        if (targetLevel == null || SableCompanion.INSTANCE.getContaining(targetLevel, targetWaystone.getPos()) != null) {
            return;
        }

        StoredSubLevelTarget storedTarget = resolveStoredSubLevelTarget(targetLevel, targetWaystone);
        if (storedTarget == null) {
            return;
        }

        addSubLevelChunkPositions(event, storedTarget.data());
        event.addPreparationTask(result -> {
            if (result.right().isPresent()) {
                return CompletableFuture.completedFuture(result);
            }

            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(targetLevel);
            if (container != null) {
                container.getHoldingChunkMap().snatchAndLoad(storedTarget.pointer(), storedTarget.data().uuid());
            }
            return CompletableFuture.completedFuture(result);
        });
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

        TrackingPoint trackingPoint = SubLevelTrackingPointSavedData.getOrLoad(level)
                .getTrackingPoint(waystone.getWaystoneUid());
        if (trackingPoint == null || !trackingPoint.inSubLevel()) {
            return null;
        }

        if (trackingPoint.subLevelID() != null) {
            HoldingSubLevel holdingSubLevel = container.getHoldingChunkMap().getHoldingSubLevel(trackingPoint.subLevelID());
            if (holdingSubLevel != null && holdingSubLevel.pointer() != null) {
                return new StoredSubLevelTarget(holdingSubLevel.pointer(), holdingSubLevel.data());
            }
        }

        if (trackingPoint.lastSavedSubLevelPointer() != null) {
            var pointer = trackingPoint.lastSavedSubLevelPointer();
            SubLevelData data = container.getHoldingChunkMap().getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            if (data != null) {
                return new StoredSubLevelTarget(pointer, data);
            }
        }
        return null;
    }

    private static SubLevelData getTeleportTargetData(ServerLevel level, Waystone waystone) {
        if (waystone.getDimension() != level.dimension()) {
            return null;
        }

        StoredSubLevelTarget storedTarget = resolveStoredSubLevelTarget(level, waystone);
        return storedTarget != null ? storedTarget.data() : null;
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
