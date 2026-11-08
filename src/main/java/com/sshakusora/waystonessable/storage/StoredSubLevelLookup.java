package com.sshakusora.waystonessable.storage;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.association.WaystoneSubLevelBinding;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads serialized data without attaching SubLevels to a live container.
 */
public final class StoredSubLevelLookup {
    private StoredSubLevelLookup() {
    }

    @Nullable
    static SubLevelData findInMemory(final SubLevelHoldingChunkMap map, final UUID id,
                                     final int plotX, final int plotZ) {
        final HoldingSubLevel held = map.getHoldingSubLevel(id);
        return held != null && matches(held.data(), id, plotX, plotZ) ? held.data() : null;
    }

    @Nullable
    static SubLevelData findAtPointer(final SubLevelStorage storage,
                                      @Nullable final GlobalSavedSubLevelPointer pointer,
                                      final UUID id, final int plotX, final int plotZ) {
        if (pointer == null) {
            return null;
        }
        final SubLevelData data = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
        return data != null && matches(data, id, plotX, plotZ) ? data : null;
    }

    static boolean matches(final SubLevelData data, final UUID id, final int plotX, final int plotZ) {
        return id.equals(data.uuid()) && matchesPlot(data, plotX, plotZ);
    }

    static boolean matchesPlot(final SubLevelData data, final int plotX, final int plotZ) {
        if (!data.fullTag().contains("plot")) {
            return false;
        }
        final var plotTag = data.fullTag().getCompound("plot");
        return plotTag.getInt("plot_x") == plotX && plotTag.getInt("plot_z") == plotZ;
    }

    public static Optional<Pose3dc> findPose(final ServerSubLevelContainer container,
                                             final WaystoneSubLevelBinding binding,
                                             final int plotX, final int plotZ) {
        final SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        if (map == null) {
            return Optional.empty();
        }
        try {
            SubLevelData data = findInMemory(map, binding.subLevelId(), plotX, plotZ);
            if (data == null) {
                data = findAtPointer(map.getStorage(), binding.pointer(), binding.subLevelId(), plotX, plotZ);
            }
            return data == null ? Optional.empty() : Optional.of(data.pose());
        } catch (final RuntimeException exception) {
            WaystonesSable.LOGGER.warn("Failed to read pose of SubLevel {} for Waystone position", binding.subLevelId(), exception);
            return Optional.empty();
        }
    }
}
