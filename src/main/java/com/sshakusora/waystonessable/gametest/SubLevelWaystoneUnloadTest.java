package com.sshakusora.waystonessable.gametest;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import com.sshakusora.waystonessable.compat.SubLevelHoldingChunkMapAccessor;
import com.sshakusora.waystonessable.compat.WaystonePositionSnapshotSavedData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.neoforge.gametest.SableTestHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.MutablePersonalizedWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.menu.WaystoneSelectionListBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.joml.Vector3d;

import java.util.*;

@GameTestHolder(WaystonesSable.MOD_ID)
public final class SubLevelWaystoneUnloadTest {

    private static final int SMALL_BATCH = 16;
    private static final int MEDIUM_BATCH = 128;
    private static final int SPACING = 24;

    private SubLevelWaystoneUnloadTest() {
    }

    @GameTest(template = "gravity", timeoutTicks = 600)
    public static void unloadedSubLevelWaystoneRemainsRecognized(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    new Vector3d(10.5, 74.0, 10.5),
                    "single_unloaded"
            );

            container.removeSubLevel(scenario.subLevel(), SubLevelRemovalReason.UNLOADED);

            assertRecognized(helper, scenario.waystone(), "single unloaded SubLevel waystone");
            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            helper.succeed();
        });
    }

    @GameTest(template = "gravity", timeoutTicks = 1200)
    public static void unloadedSubLevelWaystonesRemainRecognizedInBatch(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            List<SubLevelWaystoneTestSupport.SubLevelWaystone> scenarios = createBatch(helper, container, MEDIUM_BATCH, "batch_unloaded");

            for (SubLevelWaystoneTestSupport.SubLevelWaystone scenario : scenarios) {
                container.removeSubLevel(scenario.subLevel(), SubLevelRemovalReason.UNLOADED);
            }

            int recognized = 0;
            int indexed = 0;
            for (SubLevelWaystoneTestSupport.SubLevelWaystone scenario : scenarios) {
                Waystone waystone = scenario.waystone();
                if (WaystonesAPI.getWaystone(helper.getLevel().getServer(), waystone.getWaystoneUid()).isPresent()) {
                    indexed++;
                }
                if (SableWaystoneCompat.isWaystoneOnSubLevel(helper.getLevel().getServer(), waystone)) {
                    recognized++;
                }
            }

            if (indexed != MEDIUM_BATCH) {
                helper.fail("Waystones API indexed only " + indexed + " of " + MEDIUM_BATCH + " SubLevel waystones");
                return;
            }
            if (recognized != MEDIUM_BATCH) {
                helper.fail("Recognized only " + recognized + " of " + MEDIUM_BATCH + " unloaded SubLevel waystones");
                return;
            }

            List<Waystone> sourceWaystones = scenarios.stream()
                    .map(SubLevelWaystoneTestSupport.SubLevelWaystone::waystone)
                    .toList();
            List<MutablePersonalizedWaystone> menuWaystones = new WaystoneSelectionListBuilder(
                    FakePlayerFactory.getMinecraft(helper.getLevel())
            )
                    .withWaystones(sourceWaystones)
                    .skipSortingIndexUpdate()
                    .build();
            long visibleSubLevelWaystones = menuWaystones.stream()
                    .filter(candidate -> sourceWaystones.stream().anyMatch(source ->
                            source.getWaystoneUid().equals(candidate.getWaystoneUid())))
                    .filter(candidate -> !SableWaystoneCompat.isInternalPlotPosition(
                            helper.getLevel(),
                            candidate.getPos().getCenter()
                    ))
                    .count();
            if (visibleSubLevelWaystones != MEDIUM_BATCH) {
                helper.fail("Snapshot-backed selection menu exposed only " + visibleSubLevelWaystones
                        + " of " + MEDIUM_BATCH + " unloaded SubLevel waystones without raw plot coordinates");
                return;
            }

            for (SubLevelWaystoneTestSupport.SubLevelWaystone scenario : scenarios) {
                SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            }
            helper.succeed();
        });
    }

    @GameTest(template = "gravity", batch = "disk_stored_menu", timeoutTicks = 1200)
    public static void selectionMenuUsesSnapshotWithoutRestoringDiskStoredSubLevel(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            Vector3d visiblePosition = SableTestHelper.absolutePosition(helper, new Vector3d(18.5, 7.0, 18.5));
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    visiblePosition,
                    "disk_stored_menu_snapshot"
            );
            Waystone waystone = scenario.waystone();
            UUID subLevelId = scenario.subLevel().getUniqueId();
            BlockPos expectedVisiblePos = BlockPos.containing(
                    SableWaystoneCompat.getVisibleWaystonePos(helper.getLevel(), waystone)
            );

            storeAndEvictSubLevel(helper, container, scenario.subLevel(), waystone);
            if (container.getSubLevel(subLevelId) != null) {
                helper.fail("Menu snapshot setup failed: disk-stored SubLevel remained loaded");
                return;
            }

            List<MutablePersonalizedWaystone> menuWaystones = new WaystoneSelectionListBuilder(
                    FakePlayerFactory.getMinecraft(helper.getLevel())
            )
                    .withWaystones(List.of(waystone))
                    .skipSortingIndexUpdate()
                    .build();
            MutablePersonalizedWaystone menuWaystone = menuWaystones.stream()
                    .filter(candidate -> candidate.getWaystoneUid().equals(waystone.getWaystoneUid()))
                    .findFirst()
                    .orElse(null);
            if (menuWaystone == null) {
                helper.fail("Selection menu dropped the disk-stored SubLevel Waystone despite its position snapshot");
                return;
            }
            if (!expectedVisiblePos.equals(menuWaystone.getPos())) {
                helper.fail("Selection menu used " + menuWaystone.getPos()
                        + " instead of the cached visible position " + expectedVisiblePos);
                return;
            }
            if (container.getSubLevel(subLevelId) != null) {
                helper.fail("Building the selection menu restored a disk-stored SubLevel");
                return;
            }

            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            helper.succeed();
        });
    }

    @GameTest(template = "gravity", batch = "legacy_disk_stored_menu", timeoutTicks = 12000)
    public static void selectionMenuMigratesMissingSnapshotWithoutDiskRead(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            Vector3d visiblePosition = SableTestHelper.absolutePosition(helper, new Vector3d(8.5, 6.0, 8.5));
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    visiblePosition,
                    "legacy_disk_stored_menu"
            );
            Waystone waystone = scenario.waystone();
            UUID subLevelId = scenario.subLevel().getUniqueId();
            Vec3 expected = SableWaystoneCompat.getVisibleTeleportPos(
                    helper.getLevel(),
                    Vec3.atCenterOf(waystone.getPos()),
                    waystone
            );

            storeAndEvictSubLevel(helper, container, scenario.subLevel(), waystone);
            WaystonePositionSnapshotSavedData.getOrLoad(helper.getLevel()).remove(waystone.getWaystoneUid());
            if (container.getSubLevel(subLevelId) != null) {
                helper.fail("Legacy menu setup failed: disk-stored SubLevel remained loaded");
                return;
            }

            List<MutablePersonalizedWaystone> menuWaystones = new WaystoneSelectionListBuilder(
                    FakePlayerFactory.getMinecraft(helper.getLevel())
            )
                    .withWaystones(List.of(waystone))
                    .skipSortingIndexUpdate()
                    .build();
            MutablePersonalizedWaystone menuWaystone = menuWaystones.stream()
                    .filter(candidate -> candidate.getWaystoneUid().equals(waystone.getWaystoneUid()))
                    .findFirst()
                    .orElse(null);
            if (menuWaystone == null) {
                helper.fail("Legacy disk-stored Waystone disappeared because it had no position snapshot");
                return;
            }
            if (SableWaystoneCompat.isInternalPlotPosition(helper.getLevel(), menuWaystone.getPos().getCenter())) {
                helper.fail("Legacy disk-stored Waystone exposed its reserved plot coordinate in the menu");
                return;
            }
            if (container.getSubLevel(subLevelId) != null) {
                helper.fail("Legacy menu fallback restored a disk-stored SubLevel while building the menu");
                return;
            }

            ArmorStand entity = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
            BlockPos startPos = helper.absolutePos(new BlockPos(2, 3, 2));
            entity.moveTo(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
            helper.getLevel().addFreshEntity(entity);
            WaystoneTeleportContext context = WaystonesAPI.createUnboundTeleportContext(entity, menuWaystone);
            Set<ChunkPos> chunkPositions = new LinkedHashSet<>();
            chunkPositions.add(new ChunkPos(menuWaystone.getPos()));
            WaystoneTeleportEvent.Prepare prepareEvent = new WaystoneTeleportEvent.Prepare(context, chunkPositions);
            Balm.getEvents().fireEvent(prepareEvent);
            if (!(container.getSubLevel(subLevelId) instanceof ServerSubLevel loadedSubLevel)) {
                helper.fail("Legacy menu Prepare event did not restore its disk-stored SubLevel");
                entity.discard();
                return;
            }
            if (!waystone.getPos().equals(context.getTargetWaystone().getPos())) {
                helper.fail("Legacy menu Prepare event kept the approximate display coordinate as its storage target");
                entity.discard();
                return;
            }
            if (!prepareEvent.getChunkPositions().isEmpty()) {
                helper.fail("Legacy menu Prepare event retained display-only chunk requests after synchronous restore");
                entity.discard();
                return;
            }

            Vec3 resolved = SableWaystoneCompat.resolveVisibleTeleportPos(
                    helper.getLevel(),
                    Vec3.atCenterOf(context.getTargetWaystone().getPos()),
                    context.getTargetWaystone()
            ).orElse(null);
            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, loadedSubLevel);
            entity.discard();
            if (resolved == null || resolved.distanceToSqr(expected) > 0.01) {
                helper.fail("Legacy menu Prepare event resolved the wrong visible destination: actual="
                        + resolved + ", expected=" + expected);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "gravity", batch = "disk_stored_prepare", timeoutTicks = 800)
    public static void prepareEventLoadsUnloadedSubLevelBeforeWaystonesValidation(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    new Vector3d(14.5, 78.0, 14.5),
                    "prepare_unloaded"
            );
            Waystone waystone = scenario.waystone();
            UUID subLevelId = scenario.subLevel().getUniqueId();

            storeAndEvictSubLevel(helper, container, scenario.subLevel(), waystone);
            if (container.getSubLevel(subLevelId) != null) {
                helper.fail("Prepare setup failed: SubLevel was still loaded after UNLOADED removal");
                return;
            }

            WaystoneTeleportContext context = WaystonesAPI.createUnboundTeleportContext(
                    FakePlayerFactory.getMinecraft(helper.getLevel()),
                    waystone
            );
            Set<ChunkPos> chunkPositions = new LinkedHashSet<>();
            WaystoneTeleportEvent.Prepare event = new WaystoneTeleportEvent.Prepare(context, chunkPositions);
            SableWaystoneCompat.prepareStoredSubLevelForTeleport(event);

            if (!(container.getSubLevel(subLevelId) instanceof ServerSubLevel)) {
                helper.fail("Prepare event did not restore the stored SubLevel before Waystones starts loading chunks");
                return;
            }
            if (!event.getPreparationTasks().isEmpty()) {
                helper.fail("Successful synchronous SubLevel restore unexpectedly registered a delayed preparation task");
                return;
            }

            helper.runAfterDelay(20, () -> {
                if (!(container.getSubLevel(subLevelId) instanceof ServerSubLevel loadedSubLevel)) {
                    helper.fail("Prepare event did not load the stored SubLevel before validation");
                    return;
                }
                if (!waystone.isValidInLevel(helper.getLevel())) {
                    helper.fail("Loaded SubLevel waystone should pass Waystones' default validation after Prepare");
                    return;
                }

                SubLevelWaystoneTestSupport.clearSubLevelPlot(container, loadedSubLevel);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "gravity", batch = "disk_stored_prepare", timeoutTicks = 800)
    public static void firstPersistencePointerIsSyncedBeforeTeleportRestore(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    new Vector3d(18.5, 78.0, 18.5),
                    "first_persistence_pointer"
            );
            Waystone waystone = scenario.waystone();
            UUID subLevelId = scenario.subLevel().getUniqueId();

            if (scenario.subLevel().getLastSerializationPointer() != null) {
                helper.fail("First-persistence setup unexpectedly already had a Sable storage pointer");
                return;
            }

            ChunkPos holdingChunkPos = new ChunkPos(waystone.getPos());
            container.getHoldingChunkMap().moveToUnloaded(scenario.subLevel(), holdingChunkPos);
            container.getHoldingChunkMap().updateChunkStatus(holdingChunkPos, false);
            container.getHoldingChunkMap().saveAll();

            TrackingPoint trackingPoint = SubLevelTrackingPointSavedData.getOrLoad(helper.getLevel())
                    .getTrackingPoint(waystone.getWaystoneUid());
            if (trackingPoint == null || trackingPoint.lastSavedSubLevelPointer() == null) {
                helper.fail("Sable assigned a first-persistence pointer, but WaystonesSable did not sync it");
                return;
            }
            if (container.getSubLevel(subLevelId) != null
                    || container.getHoldingChunkMap().getHoldingSubLevel(subLevelId) != null) {
                helper.fail("First-persistence setup did not evict the SubLevel from memory");
                return;
            }

            WaystoneTeleportContext context = WaystonesAPI.createUnboundTeleportContext(
                    FakePlayerFactory.getMinecraft(helper.getLevel()),
                    waystone
            );
            Set<ChunkPos> chunkPositions = new LinkedHashSet<>();
            WaystoneTeleportEvent.Prepare event = new WaystoneTeleportEvent.Prepare(context, chunkPositions);
            SableWaystoneCompat.prepareStoredSubLevelForTeleport(event);

            if (!(container.getSubLevel(subLevelId) instanceof ServerSubLevel)) {
                helper.fail("Synced first-persistence pointer did not restore the SubLevel during Prepare");
                return;
            }

            ServerSubLevel loadedSubLevel = (ServerSubLevel) container.getSubLevel(subLevelId);
            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, loadedSubLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "gravity", batch = "holding_index_drift", timeoutTicks = 1200)
    public static void staleHoldingIndexDoesNotRejectPersistedWaystone(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    new Vector3d(18.5, 78.0, 18.5),
                    "stale_holding_index"
            );
            Waystone waystone = scenario.waystone();
            UUID subLevelId = scenario.subLevel().getUniqueId();

            storeAndEvictSubLevel(helper, container, scenario.subLevel(), waystone);
            TrackingPoint trackingPoint = SubLevelTrackingPointSavedData.getOrLoad(helper.getLevel())
                    .getTrackingPoint(waystone.getWaystoneUid());
            if (trackingPoint == null || trackingPoint.lastSavedSubLevelPointer() == null) {
                helper.fail("Stale-index setup did not produce a persisted tracking pointer");
                return;
            }

            GlobalSavedSubLevelPointer pointer = trackingPoint.lastSavedSubLevelPointer();
            SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();
            SubLevelHoldingChunk holdingChunk = ((SubLevelHoldingChunkMapAccessor) holdingMap)
                    .waystonesSable$getOrLoadHoldingChunk(pointer.chunkPos(), false);
            if (holdingChunk == null) {
                helper.fail("Stale-index setup could not load the persisted holding chunk");
                return;
            }
            if (!holdingChunk.getSubLevelPointers().remove(pointer.local())) {
                helper.fail("Stale-index setup did not find the target pointer in the holding chunk");
                return;
            }
            holdingMap.updateChunkStatus(pointer.chunkPos(), false);
            holdingMap.saveAll();

            if (holdingMap.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local()) == null) {
                helper.fail("Stale-index setup accidentally removed the SubLevel payload");
                return;
            }

            WaystoneTeleportContext context = WaystonesAPI.createUnboundTeleportContext(
                    FakePlayerFactory.getMinecraft(helper.getLevel()),
                    waystone
            );
            WaystoneTeleportEvent.Prepare event = new WaystoneTeleportEvent.Prepare(
                    context,
                    new LinkedHashSet<>()
            );
            SableWaystoneCompat.prepareStoredSubLevelForTeleport(event);

            if (!(container.getSubLevel(subLevelId) instanceof ServerSubLevel loadedSubLevel)) {
                helper.fail("A persisted SubLevel was rejected after its holding index entry was removed");
                return;
            }
            SubLevelHoldingChunk repairedChunk = ((SubLevelHoldingChunkMapAccessor) holdingMap)
                    .waystonesSable$getOrLoadHoldingChunk(pointer.chunkPos(), false);
            if (repairedChunk == null || !repairedChunk.getSubLevelPointers().contains(pointer.local())) {
                helper.fail("Sable pointer repair did not restore the holding-chunk index entry");
                return;
            }

            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, loadedSubLevel);
            helper.succeed();
        });
    }

    @GameTest(template = "gravity", batch = "orphaned_plot_target", timeoutTicks = 12000)
    public static void orphanedPlotTargetFailsWithoutLoadingReservedChunks(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            Vector3d visiblePosition = SableTestHelper.absolutePosition(helper, new Vector3d(8.5, 6.0, 8.5));
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    visiblePosition,
                    "orphaned_plot_target"
            );
            Waystone waystone = scenario.waystone();
            UUID subLevelId = scenario.subLevel().getUniqueId();
            storeAndEvictSubLevel(helper, container, scenario.subLevel(), waystone);
            SubLevelTrackingPointSavedData.getOrLoad(helper.getLevel()).removeTrackingPoint(waystone.getWaystoneUid());
            if (SableWaystoneCompat.resolveVisibleTeleportPos(
                    helper.getLevel(),
                    Vec3.atCenterOf(waystone.getPos()),
                    waystone
            ).isPresent()) {
                helper.fail("Orphaned reserved-plot coordinate was exposed as a usable teleport target");
                return;
            }

            ArmorStand entity = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
            BlockPos startPos = helper.absolutePos(new BlockPos(2, 3, 2));
            Vec3 expectedPosition = Vec3.atBottomCenterOf(startPos);
            entity.moveTo(expectedPosition.x, expectedPosition.y, expectedPosition.z);
            helper.getLevel().addFreshEntity(entity);

            WaystoneTeleportContext context = WaystonesAPI.createUnboundTeleportContext(entity, waystone);
            WaystonesAPI.tryTeleportAsync(context).whenComplete((result, throwable) ->
                    helper.getLevel().getServer().execute(() -> {
                        try {
                            if (throwable != null) {
                                helper.fail("Orphaned plot teleport completed exceptionally instead of failing safely: " + throwable);
                                return;
                            }
                            if (!(result.right().orElse(null) instanceof WaystoneTeleportError.DestinationOutOfBounds)) {
                                helper.fail("Orphaned plot teleport returned the wrong result: " + result);
                                return;
                            }
                            if (entity.position().distanceToSqr(expectedPosition) > 0.01) {
                                helper.fail("Orphaned plot teleport moved the entity despite being rejected: " + entity.position());
                                return;
                            }
                            if (container.getSubLevel(subLevelId) != null) {
                                helper.fail("Rejected orphaned target unexpectedly restored or loaded its reserved plot");
                                return;
                            }
                            helper.succeed();
                        } catch (Throwable callbackError) {
                            helper.fail("Orphaned plot teleport verification failed unexpectedly: " + callbackError);
                        } finally {
                            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
                            entity.discard();
                        }
                    })
            );
        });
    }

    @GameTest(template = "gravity", batch = "disk_stored_warp_plate", timeoutTicks = 12000)
    public static void boundWarpPlateTeleportLoadsDiskStoredSubLevel(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            Vector3d visiblePosition = SableTestHelper.absolutePosition(helper, new Vector3d(8.5, 6.0, 8.5));
            SubLevelWaystoneTestSupport.SubLevelWarpPlate scenario = SubLevelWaystoneTestSupport.createSubLevelWarpPlate(
                    helper,
                    container,
                    visiblePosition,
                    "disk_stored_warp_plate"
            );
            Waystone waystone = scenario.waystone();
            UUID subLevelId = scenario.subLevel().getUniqueId();
            Vec3 expected = SableWaystoneCompat.getVisibleTeleportPos(
                    helper.getLevel(),
                    Vec3.atCenterOf(waystone.getPos()),
                    waystone
            );
            storeAndEvictSubLevel(helper, container, scenario.subLevel(), waystone);

            ItemStack attunedShard = WaystonesAPI.createAttunedShard(waystone);
            Waystone boundTarget = WaystonesAPI.getBoundWaystone(null, attunedShard).orElse(null);
            if (boundTarget == null) {
                helper.fail("Could not resolve the Warp Plate target from its attuned shard");
                return;
            }

            ArmorStand entity = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
            BlockPos startPos = helper.absolutePos(new BlockPos(2, 3, 2));
            entity.moveTo(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
            helper.getLevel().addFreshEntity(entity);

            WaystoneTeleportContext context = WaystonesAPI.createUnboundTeleportContext(entity, boundTarget);
            WaystonesAPI.tryTeleportAsync(context).whenComplete((result, throwable) ->
                    helper.getLevel().getServer().execute(() -> {
                        try {
                            if (throwable != null) {
                                helper.fail("Warp Plate teleport completed exceptionally: " + throwable);
                                return;
                            }
                            if (result.right().isPresent()) {
                                helper.fail("Warp Plate teleport was rejected: " + result.right().orElseThrow());
                                return;
                            }
                            if (!(container.getSubLevel(subLevelId) instanceof ServerSubLevel loadedSubLevel)) {
                                helper.fail("Warp Plate teleport did not load the disk-stored target SubLevel");
                                return;
                            }

                            Vec3 actual = entity.position();
                            double distanceSqr = actual.distanceToSqr(expected);
                            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, loadedSubLevel);
                            if (distanceSqr > 0.01) {
                                helper.fail("Warp Plate teleported to the wrong visible position: actual=" + actual
                                        + ", expected=" + expected + ", distance squared=" + distanceSqr);
                                return;
                            }
                            helper.succeed();
                        } catch (Throwable callbackError) {
                            helper.fail("Warp Plate teleport verification failed unexpectedly: " + callbackError);
                        } finally {
                            entity.discard();
                        }
                    })
            );
        });
    }

    @GameTest(template = "gravity", batch = "unsaved_far_warp_plate", timeoutTicks = 12000)
    public static void boundWarpPlateTeleportAcceptsUnsavedFarHoldingSubLevel(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            Vector3d sourcePosition = SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 4.0, 2.5));
            SubLevelWaystoneTestSupport.SubLevelWarpPlate source = SubLevelWaystoneTestSupport.createSubLevelWarpPlate(
                    helper,
                    container,
                    sourcePosition,
                    "source_sublevel_warp_plate"
            );
            Vector3d visiblePosition = SableTestHelper.absolutePosition(helper, new Vector3d(8.5, 6.0, 2008.5));
            SubLevelWaystoneTestSupport.SubLevelWarpPlate scenario = SubLevelWaystoneTestSupport.createSubLevelWarpPlate(
                    helper,
                    container,
                    visiblePosition,
                    "unsaved_far_warp_plate"
            );
            Waystone waystone = scenario.waystone();
            UUID subLevelId = scenario.subLevel().getUniqueId();
            scenario.blockEntity().setShardItem(WaystonesAPI.createAttunedShard(source.waystone()));
            Vec3 expected = SableWaystoneCompat.getVisibleTeleportPos(
                    helper.getLevel(),
                    Vec3.atCenterOf(waystone.getPos()),
                    waystone
            );

            ChunkPos holdingChunk = new ChunkPos(BlockPos.containing(visiblePosition.x, visiblePosition.y, visiblePosition.z));
            container.getHoldingChunkMap().moveToUnloaded(scenario.subLevel(), holdingChunk);
            var holdingSubLevel = container.getHoldingChunkMap().getHoldingSubLevel(subLevelId);
            if (holdingSubLevel == null || holdingSubLevel.pointer() != null) {
                helper.fail("Test setup did not produce an in-memory holding SubLevel with a null persistence pointer");
                return;
            }

            ItemStack attunedShard = WaystonesAPI.createAttunedShard(waystone);
            Waystone boundTarget = WaystonesAPI.getBoundWaystone(null, attunedShard).orElse(null);
            if (boundTarget == null) {
                helper.fail("Could not resolve the far Warp Plate target from its attuned shard");
                return;
            }
            if (!boundTarget.isValidInLevel(helper.getLevel())) {
                helper.fail("Tracked far Warp Plate target was rejected before teleport preparation");
                return;
            }

            ArmorStand entity = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
            entity.setNoGravity(true);
            Vec3 sourceVisiblePos = SableWaystoneCompat.getVisibleWaystonePos(helper.getLevel(), source.waystone());
            entity.moveTo(sourceVisiblePos.x, sourceVisiblePos.y, sourceVisiblePos.z);
            helper.getLevel().addFreshEntity(entity);

            WaystoneTeleportContext context = WaystonesAPI.createUnboundTeleportContext(entity, boundTarget)
                    .setFromWaystone(source.waystone());
            WaystonesAPI.tryTeleportAsync(context).whenComplete((result, throwable) ->
                    helper.getLevel().getServer().execute(() -> {
                        try {
                            if (throwable != null) {
                                helper.fail("Far Warp Plate teleport completed exceptionally: " + throwable);
                                return;
                            }
                            if (result.right().isPresent()) {
                                helper.fail("Far Warp Plate teleport was rejected: " + result.right().orElseThrow());
                                return;
                            }

                            Vec3 actual = entity.position();
                            double distanceSqr = actual.distanceToSqr(expected);
                            if (distanceSqr > 0.01) {
                                helper.fail("Far Warp Plate teleported to the wrong visible position: actual=" + actual
                                        + ", expected=" + expected + ", distance squared=" + distanceSqr);
                                entity.discard();
                                return;
                            }

                            helper.runAfterDelay(80, () -> {
                                try {
                                    Vec3 positionAfterCooldown = entity.position();
                                    if (positionAfterCooldown.distanceToSqr(expected) > 0.01) {
                                        helper.fail("Entity standing on the arrived Warp Plate teleported back without leaving it: actual="
                                                + positionAfterCooldown + ", expected=" + expected);
                                        return;
                                    }
                                    helper.succeed();
                                } finally {
                                    ServerSubLevel loadedSubLevel = (ServerSubLevel) container.getSubLevel(subLevelId);
                                    SubLevelWaystoneTestSupport.clearSubLevelPlot(
                                            container,
                                            loadedSubLevel != null ? loadedSubLevel : scenario.subLevel()
                                    );
                                    SubLevelWaystoneTestSupport.clearSubLevelPlot(container, source.subLevel());
                                    entity.discard();
                                }
                            });
                        } catch (Throwable callbackError) {
                            helper.fail("Far Warp Plate teleport verification failed unexpectedly: " + callbackError);
                            entity.discard();
                        }
                    })
            );
        });
    }

    @GameTest(template = "gravity", timeoutTicks = 800)
    public static void loadedAndUnloadedBatchDoNotCrossClassifyNormalWaystone(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            List<SubLevelWaystoneTestSupport.SubLevelWaystone> scenarios = createBatch(helper, container, SMALL_BATCH, "mixed_batch");
            SubLevelWaystoneTestSupport.NormalWaystone normal = SubLevelWaystoneTestSupport.createNormalWaystone(
                    helper,
                    new BlockPos(2, 3, 2),
                    "mixed_normal"
            );

            for (int i = 0; i < scenarios.size(); i += 2) {
                container.removeSubLevel(scenarios.get(i).subLevel(), SubLevelRemovalReason.UNLOADED);
            }

            for (SubLevelWaystoneTestSupport.SubLevelWaystone scenario : scenarios) {
                assertRecognized(helper, scenario.waystone(), "mixed batch SubLevel waystone " + scenario.waystone().getName().getString());
            }
            if (SableWaystoneCompat.isWaystoneOnSubLevel(helper.getLevel().getServer(), normal.waystone())) {
                helper.fail("Normal world waystone was classified as SubLevel after batch setup");
                return;
            }

            for (SubLevelWaystoneTestSupport.SubLevelWaystone scenario : scenarios) {
                SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            }
            helper.succeed();
        });
    }

    private static List<SubLevelWaystoneTestSupport.SubLevelWaystone> createBatch(
            GameTestHelper helper,
            ServerSubLevelContainer container,
            int count,
            String prefix
    ) {
        List<SubLevelWaystoneTestSupport.SubLevelWaystone> scenarios = new ArrayList<>(count);
        Vector3d base = SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 4.0, 2.5));
        int columns = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            int row = i / columns;
            int column = i % columns;
            Vector3d pos = new Vector3d(base.x + column * SPACING, base.y, base.z + row * SPACING);
            scenarios.add(SubLevelWaystoneTestSupport.createSubLevelWaystone(helper, container, pos, prefix + "_" + i));
        }
        return scenarios;
    }

    private static void storeAndEvictSubLevel(
            GameTestHelper helper,
            ServerSubLevelContainer container,
            ServerSubLevel subLevel,
            Waystone waystone
    ) {
        container.getHoldingChunkMap().saveAll();
        SubLevelWaystoneTestSupport.trackSubLevelWaystone(helper.getLevel(), subLevel, waystone);

        GlobalSavedSubLevelPointer pointer = subLevel.getLastSerializationPointer();
        if (pointer == null) {
            throw new IllegalStateException("SubLevel was not persisted before the unload test");
        }

        ChunkPos holdingChunkPos = pointer.chunkPos();
        container.getHoldingChunkMap().moveToUnloaded(subLevel, holdingChunkPos);
        container.getHoldingChunkMap().updateChunkStatus(holdingChunkPos, false);
        container.getHoldingChunkMap().saveAll();

        if (container.getHoldingChunkMap().getHoldingSubLevel(subLevel.getUniqueId()) != null) {
            throw new IllegalStateException("Holding SubLevel was not evicted from memory");
        }
        if (container.getHoldingChunkMap().getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local()) == null) {
            throw new IllegalStateException("Evicted SubLevel was not available from disk storage");
        }
    }

    private static void assertRecognized(GameTestHelper helper, Waystone waystone, String label) {
        if (!WaystonesAPI.getWaystone(helper.getLevel().getServer(), waystone.getWaystoneUid()).isPresent()) {
            helper.fail("Waystones API no longer indexes " + label);
            return;
        }
        if (!SableWaystoneCompat.isWaystoneOnSubLevel(helper.getLevel().getServer(), waystone)) {
            helper.fail("Compatibility layer did not classify " + label + " as SubLevel");
            return;
        }
    }
}
