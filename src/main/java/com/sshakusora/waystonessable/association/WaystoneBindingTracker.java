package com.sshakusora.waystonessable.association;

import com.sshakusora.waystonessable.spatial.SubLevelPlotLookup;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Maintains bindings from lifecycle events, indexed by dimension and reserved plot.
 */
public final class WaystoneBindingTracker {
    private final Set<ServerSubLevelContainer> containers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ServerSubLevelContainer> pendingContainers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<PlotKey, Map<UUID, Waystone>> byPlot = new HashMap<>();
    private final Map<UUID, PlotKey> waystonePlots = new HashMap<>();
    private final Map<UUID, PendingBinding> pendingBindings = new HashMap<>();
    private volatile @Nullable MinecraftServer activeServer;

    public void onContainerReady(ServerSubLevelContainer container) {
        if (!containers.add(container)) return;
        pendingContainers.add(container);
        container.addObserver(new BindingObserver(container));
    }

    /**
     * Called after startup, and on ticks for containers created by runtime dimensions.
     */
    public void initializePendingContainers(MinecraftServer server) {
        if (!server.isSameThread() || server.getLevel(Level.OVERWORLD) == null) return;
        if (activeServer != null && activeServer != server) return;
        activeServer = server;
        if (pendingContainers.isEmpty()) return;
        final Set<ResourceKey<Level>> dimensions = new HashSet<>();
        for (final var iterator = pendingContainers.iterator(); iterator.hasNext(); ) {
            final var container = iterator.next();
            final var level = container.getLevel();
            if (level.getServer() == server && server.getLevel(level.dimension()) == level) {
                dimensions.add(level.dimension());
                iterator.remove();
            }
        }
        if (dimensions.isEmpty()) return;
        WaystoneManagerImpl.get(server).getWaystones()
                .filter(it -> dimensions.contains(it.getDimension()))
                .forEach(it -> trackWaystone(it, false));
    }

    private boolean isServerThread() {
        final var server = activeServer;
        return server != null && server.isSameThread();
    }

    private boolean isActive(ServerSubLevelContainer container) {
        final var server = activeServer;
        return server != null && server.isSameThread()
                && containers.contains(container) && !pendingContainers.contains(container)
                && container.getLevel().getServer() == server
                && server.getLevel(container.getLevel().dimension()) == container.getLevel();
    }

    public void onWaystoneChanged(Waystone waystone) {
        // Only an explicit Waystones initialization/update event may move an existing identity.
        trackWaystone(waystone, true);
    }

    private void trackWaystone(Waystone waystone, boolean allowMove) {
        if (!isServerThread()) return;
        pendingBindings.remove(waystone.getWaystoneUid());
        unindex(waystone.getWaystoneUid());
        for (final var container : containers) {
            if (!isActive(container)) continue;
            if (!container.getLevel().dimension().equals(waystone.getDimension())) continue;
            final var index = WaystoneBindingSavedData.getOrLoad(container.getLevel().getServer());
            if (!container.inBounds(new ChunkPos(waystone.getPos()))) {
                index.remove(waystone.getWaystoneUid());
                return;
            }
            final var key = new PlotKey(waystone.getDimension().location().toString(),
                    SubLevelPlotLookup.relativePlotX(container, waystone.getPos()),
                    SubLevelPlotLookup.relativePlotZ(container, waystone.getPos()));
            byPlot.computeIfAbsent(key, ignored -> new HashMap<>()).put(waystone.getWaystoneUid(), waystone);
            waystonePlots.put(waystone.getWaystoneUid(), key);
            final var loaded = SubLevelPlotLookup.findLoadedSubLevel(container, waystone.getPos());
            if (loaded != null) {
                final var request = new PendingBinding(container, waystone, WaystoneTargetIdentity.capture(waystone),
                        loaded.getUniqueId(), allowMove);
                if (!bindVerified(request, loaded, null)) pendingBindings.put(waystone.getWaystoneUid(), request);
            } else {
                final var previous = index.get(waystone.getWaystoneUid());
                if (previous != null && (!previous.dimension().equals(key.dimension())
                        || previous.plotX() != key.x() || previous.plotZ() != key.z()))
                    index.remove(waystone.getWaystoneUid());
            }
            return;
        }
    }

    private void unindex(UUID id) {
        final var key = waystonePlots.remove(id);
        if (key == null) return;
        final var values = byPlot.get(key);
        if (values != null && values.remove(id) != null && values.isEmpty()) byPlot.remove(key);
    }

    public void removeWaystone(Waystone waystone) {
        final var server = activeServer;
        if (server == null || !server.isSameThread()) return;
        unindex(waystone.getWaystoneUid());
        pendingBindings.remove(waystone.getWaystoneUid());
        WaystoneBindingSavedData.getOrLoad(server).remove(waystone.getWaystoneUid());
    }

    public boolean updateBinding(Waystone waystone, ServerSubLevel subLevel, ServerSubLevelContainer container) {
        return updateBinding(waystone, subLevel, container, null);
    }

    public boolean updateBinding(Waystone waystone, ServerSubLevel subLevel, ServerSubLevelContainer container,
                                 @Nullable GlobalSavedSubLevelPointer pointer) {
        return bindVerified(new PendingBinding(container, waystone, WaystoneTargetIdentity.capture(waystone),
                subLevel.getUniqueId(), false), subLevel, pointer);
    }

    private boolean bindVerified(PendingBinding request, ServerSubLevel subLevel,
                                 @Nullable GlobalSavedSubLevelPointer pointer) {
        final var container = request.container();
        final var waystone = request.waystone();
        if (!isActive(container) || !request.identity().unchanged(waystone)
                || !request.identity().matches(container, subLevel, request.subLevelId())) return false;
        final var index = WaystoneBindingSavedData.getOrLoad(container.getLevel().getServer());
        final var previous = index.get(waystone.getWaystoneUid());
        if (!request.allowMove() && previous != null && (!previous.subLevelId().equals(request.subLevelId())
                || !request.identity().matchesBinding(container, previous))) return false;
        final var dimension = waystone.getDimension().location().toString();
        final int x = subLevel.getPlot().plotPos.x - container.getOrigin().x;
        final int z = subLevel.getPlot().plotPos.z - container.getOrigin().y;
        final var observedPointer = pointer != null ? pointer : subLevel.getLastSerializationPointer();
        index.record(dimension, subLevel.getUniqueId(), x, z, subLevel.logicalPose(), observedPointer);
        final var canonical = index.knownPointer(dimension, subLevel.getUniqueId(), x, z);
        final var hint = previous != null && dimension.equals(previous.dimension())
                && subLevel.getUniqueId().equals(previous.subLevelId()) ? previous.pointer() : null;
        final var effectivePointer = observedPointer != null ? observedPointer : hint != null ? hint : canonical;
        index.put(waystone.getWaystoneUid(), new WaystoneSubLevelBinding(
                dimension, subLevel.getUniqueId(), x, z,
                SubLevelPlotLookup.plotLocalPosition(container, subLevel.getPlot(), waystone.getPos()), effectivePointer));
        return true;
    }

    /**
     * Added callbacks precede plot.load(), and initialization events may precede BE assignment.
     */
    public void processPendingBindings(MinecraftServer server) {
        if (activeServer != server || !server.isSameThread()) return;
        final var pending = List.copyOf(pendingBindings.values());
        pendingBindings.clear();
        for (final var request : pending) {
            if (!isActive(request.container()) || !request.identity().unchanged(request.waystone())) continue;
            final var loaded = SubLevelPlotLookup.findLoadedSubLevel(request.container(), request.identity().position());
            if (loaded != null) bindVerified(request, loaded, null);
        }
    }

    public void releaseServerState(MinecraftServer server) {
        if (activeServer != null && activeServer != server) return;
        activeServer = null;
        containers.clear();
        pendingContainers.clear();
        byPlot.clear();
        waystonePlots.clear();
        pendingBindings.clear();
    }

    private record PlotKey(String dimension, int x, int z) {
    }

    private record PendingBinding(ServerSubLevelContainer container, Waystone waystone,
                                  WaystoneTargetIdentity identity, UUID subLevelId, boolean allowMove) {
    }

    private final class BindingObserver implements SubLevelObserver {
        private final ServerSubLevelContainer container;

        private BindingObserver(ServerSubLevelContainer container) {
            this.container = container;
        }

        @Override
        public void onSubLevelAdded(SubLevel subLevel) {
            if (!isActive(container)) return;
            if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
            for (final var waystone : waystonesOn(subLevel)) {
                pendingBindings.putIfAbsent(waystone.getWaystoneUid(), new PendingBinding(container, waystone,
                        WaystoneTargetIdentity.capture(waystone), serverSubLevel.getUniqueId(), false));
            }
        }

        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            if (!isActive(container)) return;
            if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
            final var dimension = container.getLevel().dimension().location().toString();
            final var index = WaystoneBindingSavedData.getOrLoad(container.getLevel().getServer());
            if (reason == SubLevelRemovalReason.REMOVED) {
                index.removeSubLevel(dimension, subLevel.getUniqueId());
                for (final var waystone : List.copyOf(waystonesOn(subLevel))) unindex(waystone.getWaystoneUid());
                final int x = subLevel.getPlot().plotPos.x - container.getOrigin().x;
                final int z = subLevel.getPlot().plotPos.z - container.getOrigin().y;
                pendingBindings.values().removeIf(it -> it.container() == container
                        && SubLevelPlotLookup.relativePlotX(container, it.identity().position()) == x
                        && SubLevelPlotLookup.relativePlotZ(container, it.identity().position()) == z);
                return;
            }
            final int x = subLevel.getPlot().plotPos.x - container.getOrigin().x;
            final int z = subLevel.getPlot().plotPos.z - container.getOrigin().y;
            index.record(dimension, subLevel.getUniqueId(), x, z, subLevel.logicalPose(), serverSubLevel.getLastSerializationPointer());
            for (final var waystone : waystonesOn(subLevel)) updateBinding(waystone, serverSubLevel, container);
        }

        private Collection<Waystone> waystonesOn(SubLevel subLevel) {
            final var key = new PlotKey(container.getLevel().dimension().location().toString(),
                    subLevel.getPlot().plotPos.x - container.getOrigin().x,
                    subLevel.getPlot().plotPos.z - container.getOrigin().y);
            return byPlot.getOrDefault(key, Map.of()).values();
        }
    }
}
