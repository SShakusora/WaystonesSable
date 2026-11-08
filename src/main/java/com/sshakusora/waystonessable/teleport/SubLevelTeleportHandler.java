package com.sshakusora.waystonessable.teleport;

import com.mojang.serialization.Codec;
import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.association.WaystoneBindingSavedData;
import com.sshakusora.waystonessable.association.WaystoneBindingTracker;
import com.sshakusora.waystonessable.spatial.SubLevelCoordinateTransforms;
import com.sshakusora.waystonessable.spatial.SubLevelPlotLookup;
import com.sshakusora.waystonessable.storage.SubLevelRestorer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * Owns the complete teleport flow and its temporary force-load tickets.
 */
public final class SubLevelTeleportHandler {
    /**
     * A unique, temporary ticket key prevents us from removing a pre-existing force-load ticket.
     */
    private static final SubLevelLoadingTicketType<UUID> TELEPORT_TICKET = SubLevelLoadingTicketType.create(
            WaystonesSable.asResource("waystone_teleport"),
            Codec.STRING.xmap(UUID::fromString, UUID::toString)
    );

    private final Map<WaystoneTeleportContext, TeleportLoadLease> pendingTeleports =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final WaystoneBindingTracker tracker;
    private final SubLevelRestorer restorer;

    public SubLevelTeleportHandler(final WaystoneBindingTracker tracker, final SubLevelRestorer restorer) {
        this.tracker = tracker;
        this.restorer = restorer;
    }

    public void prepareTeleport(final WaystoneTeleportEvent.Prepare event) {
        final WaystoneTeleportContext context = event.getContext();
        final Entity entity = context.getEntity();
        final MinecraftServer server = entity.getServer();
        if (server == null) {
            return;
        }

        final Waystone targetWaystone = context.getTargetWaystone();
        final ServerLevel targetLevel = server.getLevel(targetWaystone.getDimension());
        if (targetLevel == null) {
            return;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(targetLevel);
        if (container == null) {
            return;
        }

        final ChunkPos targetChunk = new ChunkPos(targetWaystone.getPos());
        ServerSubLevel subLevel = SubLevelPlotLookup.findLoadedSubLevel(container, targetWaystone.getPos());
        SubLevelRestorer.RestoredSubLevel restoration = null;

        final WaystoneBindingSavedData index = WaystoneBindingSavedData.getOrLoad(server);

        if (subLevel == null && container.inBounds(targetChunk)) {
            restoration = restorer.restore(container, targetWaystone, index.get(targetWaystone.getWaystoneUid()));
            subLevel = restoration != null ? restoration.subLevel() : null;
        }

        if (subLevel != null) {
            final UUID ticketKey = UUID.randomUUID();
            container.addForceLoadTicket(subLevel, TELEPORT_TICKET, ticketKey);
            event.getChunkPositions().clear();
            if (restoration != null) {
                WaystonesSable.LOGGER.debug("Loaded unloaded SubLevel {} for Waystone teleport", subLevel.getUniqueId());
            }

            if (restoration != null && restoration.pointer() != null) {
                WaystoneBindingSavedData.updateSubLevelPointer(
                        container.getLevel(), subLevel.getUniqueId(), restoration.pointer());
                tracker.updateBinding(targetWaystone, subLevel, container, restoration.pointer());
            } else {
                tracker.updateBinding(targetWaystone, subLevel, container);
            }
            final TeleportLoadLease previous = pendingTeleports.put(context, new TeleportLoadLease(container, subLevel, ticketKey));
            if (previous != null) {
                removeTicket(previous);
            }
        }
    }

    public void prepareEntityTeleport(final WaystoneTeleportEntityEvent.Pre event) {
        final TeleportLoadLease pending = pendingTeleports.get(event.getContext());
        if (pending == null) {
            return;
        }

        final ServerSubLevel subLevel = pending.subLevel();
        final Waystone targetWaystone = event.getContext().getTargetWaystone();

        if (!SubLevelPlotLookup.ownsWaystone(subLevel, targetWaystone)) {
            return;
        }

        final Pose3dc pose = subLevel.logicalPose();
        event.setTargetPosition(SubLevelCoordinateTransforms.toWorldTeleportPosition(pose, event.getTargetPosition()));
        event.setDirection(SubLevelCoordinateTransforms.toWorldDirection(pose, event.getDirection()));
    }

    public void completeTeleport(final WaystoneTeleportEvent.Complete event) {
        final TeleportLoadLease pending = pendingTeleports.remove(event.getContext());
        if (pending != null) {
            removeTicket(pending);
        }
    }

    private static void removeTicket(final TeleportLoadLease pending) {
        pending.container().removeForceLoadTicket(pending.subLevel(), TELEPORT_TICKET, pending.ticketKey());
    }

    public void releaseServerState(final MinecraftServer server) {
        final List<TeleportLoadLease> removed = new ArrayList<>();
        synchronized (pendingTeleports) {
            pendingTeleports.values().removeIf(pending -> {
                if (pending.container().getLevel().getServer() != server) {
                    return false;
                }
                removed.add(pending);
                return true;
            });
        }
        removed.forEach(SubLevelTeleportHandler::removeTicket);
    }

    private record TeleportLoadLease(ServerSubLevelContainer container, ServerSubLevel subLevel,
                                     UUID ticketKey) {
    }
}
