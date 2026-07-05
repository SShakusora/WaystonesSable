package com.sshakusora.waystonessable.gametest;

import com.mojang.datafixers.util.Either;
import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.neoforge.gametest.SableTestHelper;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
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

            for (SubLevelWaystoneTestSupport.SubLevelWaystone scenario : scenarios) {
                SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            }
            helper.succeed();
        });
    }

    @GameTest(template = "gravity", timeoutTicks = 800)
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

            container.getHoldingChunkMap().saveAll();
            SubLevelWaystoneTestSupport.trackSubLevelWaystone(helper.getLevel(), scenario.subLevel(), waystone);
            BlockPos holdingChunkAnchor = BlockPos.containing(
                    scenario.subLevel().logicalPose().position().x,
                    scenario.subLevel().logicalPose().position().y,
                    scenario.subLevel().logicalPose().position().z
            );
            container.getHoldingChunkMap().moveToUnloaded(scenario.subLevel(), new ChunkPos(holdingChunkAnchor));
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

            if (event.getPreparationTasks().isEmpty()) {
                helper.fail("Prepare event did not register a task for the unloaded SubLevel target");
                return;
            }

            Either<Void, WaystoneTeleportError> result = Either.left(null);
            for (var preparationTask : event.getPreparationTasks()) {
                result = preparationTask.apply(result).join();
            }
            if (result.right().isPresent()) {
                helper.fail("Prepare task returned a teleport error: " + result.right().get());
                return;
            }

            helper.runAfterDelay(20, () -> {
                if (container.getSubLevel(subLevelId) == null) {
                    helper.fail("Prepare event did not load the stored SubLevel before validation");
                    return;
                }
                if (!waystone.isValidInLevel(helper.getLevel())) {
                    helper.fail("Loaded SubLevel waystone should pass Waystones' default validation after Prepare");
                    return;
                }

                SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
                helper.succeed();
            });
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
