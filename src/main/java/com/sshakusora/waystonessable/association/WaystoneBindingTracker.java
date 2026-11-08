package com.sshakusora.waystonessable.association;

import com.sshakusora.waystonessable.spatial.SubLevelPlotLookup;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneManager;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Keeps the persistent associations in sync with Waystones and SubLevel lifecycles.
 */
public final class WaystoneBindingTracker {
    private final Set<ServerSubLevelContainer> containers =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    public void onContainerReady(final ServerSubLevelContainer container) {
        if (!containers.add(container)) {
            return;
        }

        container.addObserver(new WaystoneBindingObserver(container.getLevel()));
        updateLoadedWaystoneBindings(container);
    }

    public void reconcileLoadedWaystones(final WaystoneManager manager) {
        for (final ServerSubLevelContainer container : containerSnapshot()) {
            updateLoadedWaystoneBindings(container, manager);
        }
    }

    private List<ServerSubLevelContainer> containerSnapshot() {
        synchronized (containers) {
            return new ArrayList<>(containers);
        }
    }

    public void onWaystoneChanged(final Waystone waystone) {
        for (final ServerSubLevelContainer container : containerSnapshot()) {
            final ServerLevel level = container.getLevel();
            if (!level.dimension().equals(waystone.getDimension())) {
                continue;
            }

            final ServerSubLevel subLevel = SubLevelPlotLookup.findLoadedSubLevel(container, waystone.getPos());
            if (subLevel != null) {
                updateBinding(waystone, subLevel, container);
            }
        }
    }

    public void removeWaystone(final Waystone waystone) {
        for (final ServerSubLevelContainer container : containerSnapshot()) {
            final MinecraftServer server = container.getLevel().getServer();
            if (server != null) {
                WaystoneBindingSavedData.getOrLoad(server).remove(waystone.getWaystoneUid());
            }
        }
    }

    private void updateLoadedWaystoneBindings(final ServerSubLevelContainer container) {
        final ServerLevel level = container.getLevel();
        final MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        updateLoadedWaystoneBindings(container, WaystoneManagerImpl.get(server));
    }

    private void updateLoadedWaystoneBindings(final ServerSubLevelContainer container,
                                              final WaystoneManager manager) {
        final ServerLevel level = container.getLevel();
        final MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        manager.getWaystones()
                .filter(waystone -> level.dimension().equals(waystone.getDimension()))
                .forEach(waystone -> {
                    final ServerSubLevel subLevel = SubLevelPlotLookup.findLoadedSubLevel(container, waystone.getPos());
                    if (subLevel != null) {
                        updateBinding(waystone, subLevel, container);
                    }
                });
    }

    public void updateBinding(final Waystone waystone, final ServerSubLevel subLevel, final ServerSubLevelContainer container) {
        updateBinding(waystone, subLevel, container, null);
    }

    public void updateBinding(final Waystone waystone, final ServerSubLevel subLevel,
                              final ServerSubLevelContainer container, @Nullable final GlobalSavedSubLevelPointer pointer) {
        final WaystoneBindingSavedData index = WaystoneBindingSavedData.getOrLoad(container.getLevel().getServer());
        final LevelPlot plot = subLevel.getPlot();
        if (!SubLevelPlotLookup.ownsWaystone(subLevel, waystone)) {
            index.remove(waystone.getWaystoneUid());
            return;
        }

        final WaystoneSubLevelBinding previous = index.get(waystone.getWaystoneUid());
        final GlobalSavedSubLevelPointer effectivePointer = pointer != null
                ? pointer
                : previous != null && subLevel.getUniqueId().equals(previous.subLevelId())
                ? previous.pointer()
                : null;

        final WaystoneSubLevelBinding next = new WaystoneSubLevelBinding(
                waystone.getDimension().location().toString(),
                subLevel.getUniqueId(),
                plot.plotPos.x - container.getOrigin().x,
                plot.plotPos.z - container.getOrigin().y,
                SubLevelPlotLookup.plotLocalPosition(container, plot, waystone.getPos()),
                effectivePointer
        );
        index.put(waystone.getWaystoneUid(), next);
    }

    public void releaseServerState(final MinecraftServer server) {
        synchronized (containers) {
            containers.removeIf(container -> container.getLevel().getServer() == server);
        }
    }

    private final class WaystoneBindingObserver implements SubLevelObserver {
        private final ServerLevel level;
        private int ticks;

        private WaystoneBindingObserver(final ServerLevel level) {
            this.level = level;
        }

        @Override
        public void onSubLevelAdded(final SubLevel subLevel) {
            final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(this.level);
            if (container != null) {
                updateLoadedWaystoneBindings(container);
            }
        }

        @Override
        public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
            if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
                return;
            }

            final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(this.level);
            if (container == null) {
                return;
            }
            final WaystoneBindingSavedData index = WaystoneBindingSavedData.getOrLoad(this.level.getServer());
            WaystoneManagerImpl.get(this.level.getServer()).getWaystones()
                    .filter(waystone -> this.level.dimension().equals(waystone.getDimension()))
                    .filter(waystone -> SubLevelPlotLookup.ownsWaystone(serverSubLevel, waystone))
                    .forEach(waystone -> {
                        if (reason == SubLevelRemovalReason.REMOVED) {
                            index.remove(waystone.getWaystoneUid());
                        } else {
                            updateBinding(waystone, serverSubLevel, container);
                        }
                    });
        }

        @Override
        public void tick(final SubLevelContainer subLevels) {
            if (++this.ticks % 20 == 0 && subLevels instanceof ServerSubLevelContainer container) {
                updateLoadedWaystoneBindings(container);
            }
        }
    }
}
