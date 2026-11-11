package com.sshakusora.waystonessable;

import com.sshakusora.waystonessable.assembly.WaystoneAssemblyConnections;
import com.sshakusora.waystonessable.association.WaystoneBindingTracker;
import com.sshakusora.waystonessable.requirement.SubLevelDistanceRequirements;
import com.sshakusora.waystonessable.selection.SubLevelWaystoneGroup;
import com.sshakusora.waystonessable.selection.WaystoneSelectionMenuHandler;
import com.sshakusora.waystonessable.storage.SubLevelReadScheduler;
import com.sshakusora.waystonessable.storage.SubLevelRestorer;
import com.sshakusora.waystonessable.teleport.SubLevelTeleportHandler;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.waystones.api.event.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wires event adapters and owns their server-lifetime state.
 */
public final class WaystonesSableRuntime {
    private final WaystoneBindingTracker tracker = new WaystoneBindingTracker();
    private final SubLevelTeleportHandler teleports = new SubLevelTeleportHandler(tracker, new SubLevelRestorer());

    private final WaystoneSelectionMenuHandler selectionMenus = new WaystoneSelectionMenuHandler();

    public void register() {
        Balm.getEvents().onEvent(WaystoneInitializedEvent.class, event -> tracker.onWaystoneChanged(event.getWaystone()));
        Balm.getEvents().onEvent(WaystoneUpdatedEvent.class, event -> tracker.onWaystoneChanged(event.getWaystone()));
        Balm.getEvents().onEvent(WaystoneRemovedEvent.class, event -> tracker.removeWaystone(event.getWaystone()));
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, teleports::startTeleport, EventPriority.Lowest);
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Prepare.class, teleports::prepareTeleport);
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Pre.class, teleports::prepareEntityTeleport, EventPriority.Lowest);
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Post.class, teleports::completeEntityTeleport);
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Complete.class, teleports::completeTeleport);
        Balm.getEvents().onEvent(BuildWaystoneSelectionMenuEvent.class, selectionMenus::onBuildSelectionMenu);
        Balm.getEvents().onEvent(CollectDefaultWaystoneGroupsEvent.class, SubLevelWaystoneGroup::collectDefaults);
    }

    public void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(SubLevelDistanceRequirements::register);
        if (ModList.get().isLoaded("create")) event.enqueueWork(WaystoneAssemblyConnections::register);
    }

    public void onContainerReady(final ForgeSableSubLevelContainerReadyEvent event) {
        if (event.getContainer() instanceof ServerSubLevelContainer container) {
            tracker.onContainerReady(container);
        }
    }

    public void onServerStopping(final ServerStoppingEvent event) {
        teleports.releaseServerState(event.getServer());
        SubLevelReadScheduler.release(event.getServer());
        tracker.releaseServerState(event.getServer());
    }

    public void onServerStarted(final ServerStartedEvent event) {
        tracker.initializePendingContainers(event.getServer());
    }

    public void onServerTick(final ServerTickEvent.Post event) {
        tracker.initializePendingContainers(event.getServer());
        tracker.processPendingBindings(event.getServer());
        SubLevelReadScheduler.tick(event.getServer());
    }
}
