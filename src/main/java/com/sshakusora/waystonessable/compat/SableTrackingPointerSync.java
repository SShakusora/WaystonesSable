package com.sshakusora.waystonessable.compat;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges Sable's storage-pointer assignment to its tracking-point SavedData.
 *
 * <p>Sable assigns a pointer to a {@code HoldingSubLevel} after it has notified
 * sub-level observers that the live sub-level was removed. The observer therefore
 * cannot see the pointer for a newly persisted sub-level. The mixins queue those
 * assignments and flush them once Sable's save pass has completed.</p>
 */
public final class SableTrackingPointerSync {
    private static final Map<UUID, GlobalSavedSubLevelPointer> PENDING_POINTERS = new HashMap<>();

    private SableTrackingPointerSync() {
    }

    public static synchronized void record(UUID subLevelId, GlobalSavedSubLevelPointer pointer) {
        if (subLevelId != null && pointer != null) {
            PENDING_POINTERS.put(subLevelId, pointer);
        }
    }

    public static void flush() {
        MinecraftServer server = Balm.hooks().getServer();
        if (server == null) {
            return;
        }

        Map<UUID, GlobalSavedSubLevelPointer> pointers;
        synchronized (SableTrackingPointerSync.class) {
            if (PENDING_POINTERS.isEmpty()) {
                return;
            }
            pointers = new HashMap<>(PENDING_POINTERS);
            PENDING_POINTERS.clear();
        }

        for (ServerLevel level : server.getAllLevels()) {
            syncLevel(level, pointers);
        }
    }

    private static void syncLevel(
            ServerLevel level,
            Map<UUID, GlobalSavedSubLevelPointer> pointers
    ) {
        SubLevelTrackingPointSavedData trackingPoints = SubLevelTrackingPointSavedData.getOrLoad(level);
        for (Map.Entry<UUID, TrackingPoint> entry : trackingPoints.getAllTrackingPoints()) {
            TrackingPoint trackingPoint = entry.getValue();
            if (trackingPoint == null
                    || !trackingPoint.inSubLevel()
                    || trackingPoint.subLevelID() == null
                    || trackingPoint.point() == null) {
                continue;
            }

            GlobalSavedSubLevelPointer pointer = pointers.get(trackingPoint.subLevelID());
            if (pointer == null || pointer.equals(trackingPoint.lastSavedSubLevelPointer())) {
                continue;
            }

            trackingPoints.setTrackingPoint(entry.getKey(), new TrackingPoint(
                    true,
                    trackingPoint.subLevelID(),
                    pointer,
                    trackingPoint.point(),
                    null
            ));
        }
    }
}
