package com.sshakusora.waystonessable.compat;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.TeleportDestination;
import net.blay09.mods.waystones.api.event.CollectDefaultWaystoneGroupsEvent;
import net.blay09.mods.waystones.api.event.CollectDynamicWaystoneGroupsEvent;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;

public final class SableWaystoneEventHandler {

    private SableWaystoneEventHandler() {
    }

    public static void register() {
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Prepare.class, SableWaystoneEventHandler::onPrepareTeleport);
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Pre.class, SableWaystoneEventHandler::onTeleportEntityPre);
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Post.class, SableWaystoneEventHandler::onTeleportEntityPost);
        Balm.getEvents().onEvent(CollectDefaultWaystoneGroupsEvent.class, SableWaystoneEventHandler::onCollectDefaultGroups);
        Balm.getEvents().onEvent(CollectDynamicWaystoneGroupsEvent.class, SableWaystoneEventHandler::onCollectDynamicGroups);
    }

    private static void onPrepareTeleport(WaystoneTeleportEvent.Prepare event) {
        SableWaystoneCompat.prepareStoredSubLevelForTeleport(event);
    }

    private static void onTeleportEntityPre(WaystoneTeleportEntityEvent.Pre event) {
        boolean dimensionalTeleport = event.getTargetLevel() != event.getEntity().level();
        if (dimensionalTeleport) {
            SableWaystoneCompat.prepareCrossDimensionTeleport(
                    event.getEntity(),
                    event.getTargetLevel(),
                    event.getOriginalDestination().location()
            );
        }

        event.setTargetPosition(SableWaystoneCompat.getVisibleTeleportPos(
                event.getTargetLevel(),
                event.getTargetPosition(),
                event.getContext().getTargetWaystone()
        ));
    }

    private static void onTeleportEntityPost(WaystoneTeleportEntityEvent.Post event) {
        if (!event.getTeleportResult().isSuccessful()) {
            return;
        }

        TeleportDestination resolvedDestination = event.getTeleportResult().resolvedDestination();
        if (resolvedDestination == null) {
            return;
        }

        boolean dimensionalTeleport = event.getTeleportResult().originalDestination().level() != resolvedDestination.level();
        SableWaystoneCompat.syncTrackingAfterTeleport(
                event.getTeleportedEntity(),
                event.getOriginalDestination().location(),
                dimensionalTeleport,
                event.getContext().getTargetWaystone()
        );
    }

    private static void onCollectDefaultGroups(CollectDefaultWaystoneGroupsEvent event) {
        event.addGroup(SableWaystoneGroups.defaultGroup());
    }

    private static void onCollectDynamicGroups(CollectDynamicWaystoneGroupsEvent event) {
        if (SableWaystoneCompat.isWaystoneOnSubLevel(event.getWaystone())) {
            event.addGroup(SableWaystoneGroups.defaultGroup());
        }
    }
}
