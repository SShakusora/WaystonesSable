package com.sshakusora.waystonessable.compat;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.api.event.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SableWaystoneEventHandler {

    private SableWaystoneEventHandler() {
    }

    public static void register() {
        Balm.getEvents().onEvent(WaystoneActivatedEvent.class, SableWaystoneEventHandler::onWaystoneActivated);
        Balm.getEvents().onEvent(WaystoneRemovedEvent.class, SableWaystoneEventHandler::onWaystoneRemoved);
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, SableWaystoneEventHandler::onPreTeleport);
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Prepare.class, SableWaystoneEventHandler::onPrepareTeleport);
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Pre.class, SableWaystoneEventHandler::onTeleportEntityPre);
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Post.class, SableWaystoneEventHandler::onTeleportEntityPost);
        Balm.getEvents().onEvent(CollectDefaultWaystoneGroupsEvent.class, SableWaystoneEventHandler::onCollectDefaultGroups);
        Balm.getEvents().onEvent(CollectDynamicWaystoneGroupsEvent.class, SableWaystoneEventHandler::onCollectDynamicGroups);
        Balm.getEvents().onEvent(BuildWaystoneSelectionMenuEvent.class, SableWaystoneEventHandler::onBuildWaystoneSelectionMenu);
    }

    private static void onWaystoneActivated(WaystoneActivatedEvent event) {
        if (event.getPlayer().level() instanceof ServerLevel level) {
            SableWaystoneCompat.refreshWaystoneTrackingPointIfLoaded(level, event.getWaystone());
        }
    }

    private static void onWaystoneRemoved(WaystoneRemovedEvent event) {
        MinecraftServer server = Balm.getHooks().getServer();
        if (server == null) {
            return;
        }

        ServerLevel level = server.getLevel(event.getWaystone().getDimension());
        if (level != null) {
            SableWaystoneCompat.removeWaystoneTrackingPoint(level, event.getWaystone());
        }
    }

    private static void onPreTeleport(WaystoneTeleportEvent.Pre event) {
        WaystoneTeleportContext context = event.getContext();
        MinecraftServer server = context.getEntity().getServer();
        if (server == null) {
            return;
        }

        Waystone targetWaystone = context.getTargetWaystone();
        ServerLevel targetLevel = server.getLevel(targetWaystone.getDimension());
        if (SableWaystoneCompat.isTwinboundFeather(targetWaystone)) {
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetWaystone.getWaystoneUid());
            if (targetPlayer != null) {
                SableWaystoneCompat.resolveTwinboundSubLevelTarget(targetWaystone, targetPlayer)
                        .ifPresent(context::setTargetWaystone);
            }
        } else {
            if (targetLevel != null) {
                SableWaystoneCompat.refreshWaystoneTrackingPointIfLoaded(targetLevel, targetWaystone);
            }
            if (!(targetWaystone instanceof SubLevelWaystone)
                && !(targetWaystone instanceof TrackedTargetWaystone)
                && targetLevel != null
                && SableWaystoneCompat.isTrackedSubLevelTeleportTarget(targetLevel, targetWaystone)) {
                context.setTargetWaystone(new TrackedTargetWaystone(targetWaystone));
            }
        }

        context.getFromWaystone().ifPresent(sourceWaystone -> {
            ServerLevel sourceLevel = server.getLevel(sourceWaystone.getDimension());
            if (sourceLevel == null) {
                return;
            }

            SableWaystoneCompat.refreshWaystoneTrackingPointIfLoaded(sourceLevel, sourceWaystone);
            if (!SableWaystoneCompat.isTrackedSubLevelTeleportTarget(sourceLevel, sourceWaystone)) {
                return;
            }

            SableWaystoneCompat.resolveVisibleWaystonePos(sourceLevel, sourceWaystone)
                    .map(BlockPos::containing)
                    .ifPresent(visiblePos -> context.setFromWaystone(new VisibleSourceWaystone(sourceWaystone, visiblePos)));
        });
    }

    private static void onPrepareTeleport(WaystoneTeleportEvent.Prepare event) {
        Waystone targetWaystone = event.getContext().getTargetWaystone();
        if (SableWaystoneCompat.isTwinboundFeather(targetWaystone)) {
            SableWaystoneCompat.useLoadedSubLevelChunks(event, targetWaystone);
            return;
        }

        SableWaystoneCompat.prepareStoredSubLevelForTeleport(event);
        if (targetWaystone instanceof SubLevelWaystone subLevelWaystone) {
            // The menu wrapper carries only a display position. Destination resolution must use the
            // original plot-local Waystone after Prepare has synchronously restored its SubLevel.
            event.getContext().setTargetWaystone(new TrackedTargetWaystone(subLevelWaystone.storageWaystone));
            SableWaystoneCompat.useLoadedStorageWaystoneChunks(event, subLevelWaystone.storageWaystone);
        }
    }

    private static void onTeleportEntityPre(WaystoneTeleportEntityEvent.Pre event) {
        var visibleTarget = SableWaystoneCompat.resolveVisibleTeleportPos(
                event.getTargetLevel(),
                event.getTargetPosition(),
                event.getContext().getTargetWaystone()
        );
        if (visibleTarget.isEmpty()) {
            event.overrideResult(EntityTeleportResult.failed(
                    event.getEntity(),
                    event.getOriginalDestination(),
                    new WaystoneTeleportError.DestinationOutOfBounds()
            ));
            return;
        }

        boolean dimensionalTeleport = event.getTargetLevel() != event.getEntity().level();
        if (dimensionalTeleport) {
            SableWaystoneCompat.prepareCrossDimensionTeleport(
                    event.getEntity(),
                    event.getTargetLevel(),
                    event.getOriginalDestination().location()
            );
        }

        event.setTargetPosition(visibleTarget.orElseThrow());
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
        for (int i = 0; i < waystones.size(); ) {
            MutablePersonalizedWaystone waystone = waystones.get(i);
            if (waystone instanceof SubLevelWaystone) {
                i++;
                continue;
            }

            ServerLevel level = server.getLevel(waystone.getDimension());
            if (level == null) {
                i++;
                continue;
            }

            if (!SableWaystoneCompat.isWaystoneOnSubLevel(server, waystone)) {
                if (SableWaystoneCompat.isInternalPlotPosition(level, waystone.getPos().getCenter())) {
                    waystones.remove(i);
                } else {
                    i++;
                }
                continue;
            }

            SableWaystoneCompat.refreshWaystoneTrackingPointIfLoaded(level, waystone);
            var visiblePosResult = SableWaystoneCompat.resolveVisibleWaystonePos(level, waystone);
            // Existing worlds can have valid Sable tracking metadata created before position snapshots
            // existed. Keep those entries selectable without touching Sable storage; Prepare unwraps the
            // display-only position and resolves the authoritative plot-local destination after restore.
            Vec3 visiblePos = visiblePosResult
                    .or(() -> SableWaystoneCompat.resolveVisibleMenuFallbackPos(level, waystone))
                    .orElse(null);
            if (visiblePos == null) {
                waystones.remove(i);
                continue;
            }
            BlockPos visibleBlockPos = BlockPos.containing(visiblePos);
            if (!visibleBlockPos.equals(waystone.getPos())) {
                waystones.set(i, new SubLevelWaystone(waystone, visibleBlockPos));
            }
            i++;
        }
    }

    private static final class SubLevelWaystone extends MutablePersonalizedWaystoneDelegate {
        private final MutablePersonalizedWaystone storageWaystone;
        private final Waystone visibleBackingWaystone;
        private final BlockPos visiblePos;

        private SubLevelWaystone(MutablePersonalizedWaystone delegate, BlockPos visiblePos) {
            super(delegate);
            this.storageWaystone = delegate;
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
