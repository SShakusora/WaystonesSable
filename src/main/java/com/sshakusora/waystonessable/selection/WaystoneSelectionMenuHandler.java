package com.sshakusora.waystonessable.selection;

import com.sshakusora.waystonessable.association.SubLevelKey;
import com.sshakusora.waystonessable.spatial.WaystoneSubLevelLookup;
import com.sshakusora.waystonessable.spatial.WaystoneWorldPositionResolver;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.blay09.mods.waystones.api.MutablePersonalizedWaystone;
import net.blay09.mods.waystones.api.event.BuildWaystoneSelectionMenuEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prepares display coordinates and automatic groups without changing teleport targets.
 */
public final class WaystoneSelectionMenuHandler {
    public void onBuildSelectionMenu(final BuildWaystoneSelectionMenuEvent event) {
        final MinecraftServer server = event.getPlayer().getServer();
        if (server == null) {
            return;
        }

        // Send the definition before the menu's opening data, including for existing players.
        SubLevelWaystoneGroup.syncForMenu(event.getPlayer());

        final Map<SubLevelKey, Optional<Pose3dc>> poseCache = new HashMap<>();
        final List<MutablePersonalizedWaystone> waystones = event.getWaystones();
        for (int i = 0; i < waystones.size(); i++) {
            MutablePersonalizedWaystone waystone = waystones.get(i);
            if (waystone instanceof WorldPositionDisplayWaystone displayWaystone) {
                waystone = displayWaystone.getDelegate();
            }

            final boolean onSubLevel = WaystoneSubLevelLookup.isOnSubLevel(server, waystone);
            final Optional<Vec3> worldCenter = WaystoneWorldPositionResolver.resolve(server, waystone, poseCache);
            waystones.set(i, new WorldPositionDisplayWaystone(waystone, worldCenter.orElse(null), onSubLevel));
        }
    }
}
