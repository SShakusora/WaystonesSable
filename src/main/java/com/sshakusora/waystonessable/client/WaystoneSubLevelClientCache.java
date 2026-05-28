package com.sshakusora.waystonessable.client;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaystoneSubLevelClientCache {

    private static final Map<UUID, SubLevelState> WAYSTONE_STATES = new ConcurrentHashMap<>();

    static {
        SableWaystoneCompat.setClientDistanceProvider((player, waystone) -> {
            Vec3 visiblePos = getVisiblePosition(waystone);
            if (visiblePos != null) {
                return player.position().distanceToSqr(visiblePos);
            }
            return player.distanceToSqr(waystone.getPos().getCenter());
        });
    }

    private WaystoneSubLevelClientCache() {
    }

    public static boolean isOnSubLevel(Waystone waystone) {
        SubLevelState state = WAYSTONE_STATES.get(waystone.getWaystoneUid());
        return state != null && state.isOnSubLevel();
    }

    public static Vec3 getVisiblePosition(Waystone waystone) {
        SubLevelState state = WAYSTONE_STATES.get(waystone.getWaystoneUid());
        return state != null ? state.visiblePos() : null;
    }

    public static void put(UUID waystoneId, boolean isOnSubLevel, Vec3 visiblePos, ResourceKey<Level> visibleDimension) {
        WAYSTONE_STATES.put(waystoneId, new SubLevelState(isOnSubLevel, visiblePos, visibleDimension));
    }

    public static void remove(UUID waystoneId) {
        WAYSTONE_STATES.remove(waystoneId);
    }

    public static void clear() {
        WAYSTONE_STATES.clear();
    }

    private record SubLevelState(boolean isOnSubLevel, Vec3 visiblePos, ResourceKey<Level> visibleDimension) {
    }
}
