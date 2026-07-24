package com.sshakusora.waystonessable.compat;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.api.event.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SableWaystoneEventHandler {

    private SableWaystoneEventHandler() {
    }

    public static void register() {
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, SableWaystoneEventHandler::onPreTeleport);
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Prepare.class, SableWaystoneEventHandler::onPrepareTeleport);
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Pre.class, SableWaystoneEventHandler::onTeleportEntityPre);
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Post.class, SableWaystoneEventHandler::onTeleportEntityPost);
        Balm.getEvents().onEvent(CollectDefaultWaystoneGroupsEvent.class, SableWaystoneEventHandler::onCollectDefaultGroups);
        Balm.getEvents().onEvent(CollectDynamicWaystoneGroupsEvent.class, SableWaystoneEventHandler::onCollectDynamicGroups);
        Balm.getEvents().onEvent(BuildWaystoneSelectionMenuEvent.class, SableWaystoneEventHandler::onBuildWaystoneSelectionMenu);
    }

    private static void onPreTeleport(WaystoneTeleportEvent.Pre event) {
        WaystoneTeleportContext context = event.getContext();
        MinecraftServer server = context.getEntity().getServer();
        if (server == null) {
            return;
        }

        Waystone targetWaystone = context.getTargetWaystone();
        ServerLevel targetLevel = server.getLevel(targetWaystone.getDimension());
        if (!(targetWaystone instanceof SubLevelWaystone)
                && !(targetWaystone instanceof TrackedTargetWaystone)
                && targetLevel != null
                && SableWaystoneCompat.isTrackedSubLevelTeleportTarget(targetLevel, targetWaystone)) {
            context.setTargetWaystone(new TrackedTargetWaystone(targetWaystone));
        }

        context.getFromWaystone().ifPresent(sourceWaystone -> {
            ServerLevel sourceLevel = server.getLevel(sourceWaystone.getDimension());
            if (sourceLevel == null || !SableWaystoneCompat.isTrackedSubLevelTeleportTarget(sourceLevel, sourceWaystone)) {
                return;
            }

            BlockPos visiblePos = BlockPos.containing(SableWaystoneCompat.getVisibleWaystonePos(sourceLevel, sourceWaystone));
            context.setFromWaystone(new VisibleSourceWaystone(sourceWaystone, visiblePos));
        });
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

    private static void onBuildWaystoneSelectionMenu(BuildWaystoneSelectionMenuEvent event) {
        MinecraftServer server = event.getPlayer().getServer();
        if (server == null) {
            return;
        }

        List<MutablePersonalizedWaystone> waystones = event.getWaystones();
        for (int i = 0; i < waystones.size(); i++) {
            MutablePersonalizedWaystone waystone = waystones.get(i);
            if (waystone instanceof SubLevelWaystone) {
                continue;
            }

            ServerLevel level = server.getLevel(waystone.getDimension());
            if (level == null || !SableWaystoneCompat.isWaystoneOnSubLevel(server, waystone)) {
                continue;
            }

            Vec3 visiblePos = SableWaystoneCompat.getVisibleWaystonePos(level, waystone);
            BlockPos visibleBlockPos = BlockPos.containing(visiblePos);
            if (!visibleBlockPos.equals(waystone.getPos())) {
                waystones.set(i, new SubLevelWaystone(waystone, visibleBlockPos));
            }
        }
    }

    private static final class SubLevelWaystone extends MutablePersonalizedWaystoneDelegate {
        private final Waystone visibleBackingWaystone;
        private final BlockPos visiblePos;

        private SubLevelWaystone(MutablePersonalizedWaystone delegate, BlockPos visiblePos) {
            super(delegate);
            this.visiblePos = visiblePos;
            this.visibleBackingWaystone = new WaystoneDelegate(delegate.getBackingWaystone()) {
                @Override
                public BlockPos getPos() {
                    return visiblePos;
                }
            };
        }

        @Override
        public Waystone getBackingWaystone() {
            return visibleBackingWaystone;
        }

        @Override
        public BlockPos getPos() {
            return visiblePos;
        }

        @Override
        public boolean isValidInLevel(ServerLevel level) {
            if (super.isValidInLevel(level)) {
                return true;
            }

            return SableWaystoneCompat.isTrackedSubLevelTeleportTarget(level, this);
        }
    }

    private static final class TrackedTargetWaystone extends WaystoneDelegate {

        private TrackedTargetWaystone(Waystone delegate) {
            super(delegate);
        }

        @Override
        public boolean isValidInLevel(ServerLevel level) {
            if (super.isValidInLevel(level)) {
                return true;
            }
            return SableWaystoneCompat.isTrackedSubLevelTeleportTarget(level, delegate);
        }
    }

    private static final class VisibleSourceWaystone extends WaystoneDelegate {
        private final BlockPos visiblePos;

        private VisibleSourceWaystone(Waystone delegate, BlockPos visiblePos) {
            super(delegate);
            this.visiblePos = visiblePos;
        }

        @Override
        public BlockPos getPos() {
            return visiblePos;
        }

        @Override
        public boolean isValidInLevel(ServerLevel level) {
            if (super.isValidInLevel(level)) {
                return true;
            }
            return SableWaystoneCompat.isTrackedSubLevelTeleportTarget(level, delegate);
        }
    }
}
