package com.sshakusora.waystonessable.compat;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores only the last known visible position of tracked Waystones.
 *
 * <p>This deliberately does not contain serialized SubLevel data. Menu construction must remain a lightweight
 * SavedData lookup and must never open Sable region files for every listed Waystone.</p>
 */
public final class WaystonePositionSnapshotSavedData extends SavedData implements SubLevelObserver {
    private static final String FILE_ID = "waystonessable_position_snapshots";
    private static final String SNAPSHOTS_TAG = "snapshots";

    private final ServerLevel level;
    private final Map<UUID, Vec3> positions = new HashMap<>();
    private boolean observerRegistered;

    private WaystonePositionSnapshotSavedData(ServerLevel level) {
        this.level = level;
    }

    public static WaystonePositionSnapshotSavedData getOrLoad(ServerLevel level) {
        WaystonePositionSnapshotSavedData data = level.getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> new WaystonePositionSnapshotSavedData(level),
                        (tag, provider) -> load(level, tag),
                        null
                ),
                FILE_ID
        );
        data.registerObserver();
        return data;
    }

    private static WaystonePositionSnapshotSavedData load(ServerLevel level, CompoundTag tag) {
        WaystonePositionSnapshotSavedData data = new WaystonePositionSnapshotSavedData(level);
        CompoundTag snapshotsTag = tag.getCompound(SNAPSHOTS_TAG);
        for (String key : snapshotsTag.getAllKeys()) {
            try {
                UUID waystoneUid = UUID.fromString(key);
                CompoundTag positionTag = snapshotsTag.getCompound(key);
                Vec3 position = new Vec3(
                        positionTag.getDouble("x"),
                        positionTag.getDouble("y"),
                        positionTag.getDouble("z")
                );
                if (isFinite(position)) {
                    data.positions.put(waystoneUid, position);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed third-party or manually edited entries without invalidating the complete file.
            }
        }
        return data;
    }

    private void registerObserver() {
        if (observerRegistered) {
            return;
        }

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container != null) {
            container.addObserver(this);
            observerRegistered = true;
        }
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (reason != SubLevelRemovalReason.UNLOADED || !(subLevel instanceof ServerSubLevel serverSubLevel)) {
            return;
        }

        UUID subLevelId = serverSubLevel.getUniqueId();
        SubLevelTrackingPointSavedData trackingPoints = SubLevelTrackingPointSavedData.getOrLoad(level);
        for (Map.Entry<UUID, TrackingPoint> entry : trackingPoints.getAllTrackingPoints()) {
            TrackingPoint trackingPoint = entry.getValue();
            if (!trackingPoint.inSubLevel() || !subLevelId.equals(trackingPoint.subLevelID())) {
                continue;
            }

            Vector3d visiblePosition = serverSubLevel.logicalPose().transformPosition(new Vector3d(trackingPoint.point()));
            put(entry.getKey(), new Vec3(visiblePosition.x, visiblePosition.y, visiblePosition.z));
            trackingPoints.setTrackingPoint(entry.getKey(), new TrackingPoint(
                    true,
                    subLevelId,
                    serverSubLevel.getLastSerializationPointer(),
                    new Vector3d(trackingPoint.point()),
                    serverSubLevel.getLastSerializationPointer() == null ? new Vector3d(visiblePosition) : null
            ));
        }
    }

    public Optional<Vec3> get(UUID waystoneUid) {
        return Optional.ofNullable(positions.get(waystoneUid));
    }

    public void put(UUID waystoneUid, Vec3 position) {
        if (!isFinite(position)) {
            return;
        }

        Vec3 previous = positions.put(waystoneUid, position);
        if (!position.equals(previous)) {
            setDirty(true);
        }
    }

    public void remove(UUID waystoneUid) {
        if (positions.remove(waystoneUid) != null) {
            setDirty(true);
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        CompoundTag snapshotsTag = new CompoundTag();
        for (Map.Entry<UUID, Vec3> entry : positions.entrySet()) {
            Vec3 position = entry.getValue();
            CompoundTag positionTag = new CompoundTag();
            positionTag.putDouble("x", position.x);
            positionTag.putDouble("y", position.y);
            positionTag.putDouble("z", position.z);
            snapshotsTag.put(entry.getKey().toString(), positionTag);
        }
        tag.put(SNAPSHOTS_TAG, snapshotsTag);
        return tag;
    }

    private static boolean isFinite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }
}
