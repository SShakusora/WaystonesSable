package com.sshakusora.waystonessable.client;

import net.blay09.mods.waystones.api.Waystone;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaystoneSubLevelClientCache {

    private static final Map<UUID, Boolean> WAYSTONE_SUBLEVEL_STATES = new ConcurrentHashMap<>();

    private WaystoneSubLevelClientCache() {
    }

    public static boolean isOnSubLevel(Waystone waystone) {
        return WAYSTONE_SUBLEVEL_STATES.getOrDefault(waystone.getWaystoneUid(), false);
    }

    public static void put(UUID waystoneId, boolean isOnSubLevel) {
        WAYSTONE_SUBLEVEL_STATES.put(waystoneId, isOnSubLevel);
    }

    public static void remove(UUID waystoneId) {
        WAYSTONE_SUBLEVEL_STATES.remove(waystoneId);
    }

    public static void clear() {
        WAYSTONE_SUBLEVEL_STATES.clear();
    }
}
