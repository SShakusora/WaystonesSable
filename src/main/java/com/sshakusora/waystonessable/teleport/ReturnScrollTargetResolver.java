package com.sshakusora.waystonessable.teleport;

import com.sshakusora.waystonessable.spatial.WaystoneWorldPositionResolver;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WarpPortalManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ReturnScrollTargetResolver {
    private ReturnScrollTargetResolver() {
    }

    static Optional<Waystone> find(final ServerPlayer player) {
        final Optional<Waystone> returnPortal = WarpPortalManager.getReturnPortal(player);
        if (returnPortal.isPresent()) {
            return returnPortal;
        }

        final var level = player.serverLevel();
        final var container = SubLevelContainer.getContainer(level);
        final Map<UUID, Optional<Pose3dc>> poses = new HashMap<>();
        Waystone nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (final Waystone waystone : WaystonesAPI.getActivatedWaystones(player)) {
            if (!waystone.isValid() || !waystone.getDimension().equals(level.dimension())) {
                continue;
            }
            final Optional<Vec3> resolved = WaystoneWorldPositionResolver.resolve(level.getServer(), waystone, poses);
            if (resolved.isEmpty() && container != null && container.inBounds(new ChunkPos(waystone.getPos()))) {
                continue;
            }
            final double distance = player.distanceToSqr(resolved.orElseGet(() -> waystone.getPos().getCenter()));
            if (distance < nearestDistance) {
                nearest = waystone;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }
}
