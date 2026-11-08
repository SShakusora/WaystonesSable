package com.sshakusora.waystonessable.spatial;

import com.sshakusora.waystonessable.association.WaystoneBindingSavedData;
import com.sshakusora.waystonessable.association.WaystoneSubLevelBinding;
import com.sshakusora.waystonessable.storage.StoredSubLevelLookup;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WaystoneWorldPositionResolver {
    private WaystoneWorldPositionResolver() {
    }

    public static Optional<Vec3> resolve(final MinecraftServer server, final Waystone waystone,
                                         final Map<UUID, Optional<Pose3dc>> poseCache) {
        final ServerLevel level = server.getLevel(waystone.getDimension());
        if (level == null) {
            return Optional.empty();
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        final BlockPos pos = waystone.getPos();
        if (container == null || !container.inBounds(new ChunkPos(pos))) {
            return Optional.empty();
        }

        final Vec3 center = Vec3.atCenterOf(pos);
        final ServerSubLevel loaded = SubLevelPlotLookup.findLoadedSubLevel(container, pos);
        if (loaded != null) {
            return Optional.of(loaded.logicalPose().transformPosition(center));
        }

        final WaystoneSubLevelBinding entry =
                WaystoneBindingSavedData.getOrLoad(server).get(waystone.getWaystoneUid());
        final int plotX = SubLevelPlotLookup.relativePlotX(container, pos);
        final int plotZ = SubLevelPlotLookup.relativePlotZ(container, pos);
        if (entry == null
                || !entry.dimension().equals(waystone.getDimension().location().toString())
                || entry.plotX() != plotX
                || entry.plotZ() != plotZ) {
            return Optional.empty();
        }

        return poseCache.computeIfAbsent(entry.subLevelId(), id -> StoredSubLevelLookup.findPose(container, entry, plotX, plotZ))
                .map(pose -> pose.transformPosition(center));
    }
}
