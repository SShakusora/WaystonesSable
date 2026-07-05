package com.sshakusora.waystonessable.gametest;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.gametest.SableTestHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.block.ModBlocks;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@GameTestHolder(WaystonesSable.MOD_ID)
public final class SubLevelClassificationBenchmark {

    private static final int WAYSTONE_COUNT = 1024;
    private static final int SPACING = 24;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURED_ITERATIONS = 10;
    private static final double MAX_BATCH_MILLIS = 100.0;

    private SubLevelClassificationBenchmark() {
    }

    @GameTest(template = "gravity", batch = "benchmark", timeoutTicks = 1200, required = false)
    public static void subLevelClassificationBenchmark(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerLevel level = helper.getLevel();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                helper.fail("Missing ServerSubLevelContainer");
                return;
            }

            List<Scenario> scenarios = createScenarios(helper, container);

            for (Scenario scenario : scenarios) {
                container.removeSubLevel(scenario.subLevel(), SubLevelRemovalReason.UNLOADED);
            }

            for (Scenario scenario : scenarios) {
                Waystone waystone = scenario.waystone();
                if (container.getPlot(waystone.getPos().getX() >> 4, waystone.getPos().getZ() >> 4) != null) {
                    helper.fail("Benchmark setup failed to unload " + waystone.getName().getString());
                    return;
                }
            }

            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                ClassificationResult warmup = classify(level, scenarios);
                if (warmup.hits() != WAYSTONE_COUNT) {
                    helper.fail("Warmup classified only " + warmup.hits() + " of " + WAYSTONE_COUNT + " occupied, unloaded sublevels");
                    return;
                }
            }

            long[] samples = new long[MEASURED_ITERATIONS];
            int hits = 0;
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                ClassificationResult result = classify(level, scenarios);
                samples[i] = result.elapsedNanos();
                hits = result.hits();
            }

            double averageMillis = averageMillis(samples);
            double maxMillis = maxMillis(samples);
            WaystonesSable.LOGGER.info(String.format(
                    Locale.ROOT,
                    "WaystonesSable unloaded-sublevel classification benchmark: batch=%d, hits=%d, iterations=%d, avg=%.3f ms, max=%.3f ms",
                    scenarios.size(),
                    hits,
                    MEASURED_ITERATIONS,
                    averageMillis,
                    maxMillis
            ));

            for (Scenario scenario : scenarios) {
                SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            }

            if (hits != WAYSTONE_COUNT) {
                helper.fail("Expected all occupied, unloaded sublevels to classify true, got " + hits + " of " + WAYSTONE_COUNT);
                return;
            }
            if (maxMillis > MAX_BATCH_MILLIS) {
                helper.fail(String.format(
                        Locale.ROOT,
                        "Classification batch exceeded %.1f ms budget: avg=%.3f ms, max=%.3f ms",
                        MAX_BATCH_MILLIS,
                        averageMillis,
                        maxMillis
                ));
                return;
            }

            helper.succeed();
        });
    }

    private static List<Scenario> createScenarios(GameTestHelper helper, ServerSubLevelContainer container) {
        List<Scenario> scenarios = new ArrayList<>(WAYSTONE_COUNT);
        Vector3d base = SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 4.0, 2.5));
        int columns = (int) Math.ceil(Math.sqrt(WAYSTONE_COUNT));

        for (int i = 0; i < WAYSTONE_COUNT; i++) {
            int row = i / columns;
            int column = i % columns;
            Vector3d pos = new Vector3d(base.x + column * SPACING, base.y, base.z + row * SPACING);
            scenarios.add(createSubLevelWaystone(container, pos, "classbench_" + i));
        }
        return scenarios;
    }

    private static ClassificationResult classify(ServerLevel level, List<Scenario> scenarios) {
        long start = System.nanoTime();
        int hits = 0;
        for (Scenario scenario : scenarios) {
            Waystone waystone = scenario.waystone();
            if (SableWaystoneCompat.isWaystoneOnSubLevel(level.getServer(), waystone)) {
                hits++;
            }
        }
        return new ClassificationResult(hits, System.nanoTime() - start);
    }

    private static Scenario createSubLevelWaystone(ServerSubLevelContainer container, Vector3d position, String name) {
        Pose3d pose = new Pose3d();
        pose.position().set(position);

        ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        LevelPlot plot = subLevel.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        EmbeddedPlotLevelAccessor accessor = plot.getEmbeddedLevelAccessor();

        BlockState lowerState = ModBlocks.waystone.defaultBlockState()
                .setValue(WaystoneBlockBase.HALF, DoubleBlockHalf.LOWER)
                .setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.PLAYER);
        BlockState upperState = lowerState.setValue(WaystoneBlockBase.HALF, DoubleBlockHalf.UPPER);

        accessor.setBlock(BlockPos.ZERO, lowerState, 3, 512);
        accessor.setBlock(BlockPos.ZERO.above(), upperState, 3, 512);

        WaystoneBlockEntityBase lowerEntity = requireWaystone(accessor, BlockPos.ZERO);
        WaystoneBlockEntityBase upperEntity = requireWaystone(accessor, BlockPos.ZERO.above());
        lowerEntity.initializeWaystone(accessor, null, WaystoneOrigin.PLAYER);
        upperEntity.initializeFromBase(lowerEntity);

        Waystone waystone = lowerEntity.getWaystone();
        if (waystone instanceof MutableWaystone mutableWaystone) {
            mutableWaystone.setName(Component.literal(name));
        }

        subLevel.updateLastPose();
        SubLevelWaystoneTestSupport.trackSubLevelWaystone(container.getLevel(), subLevel, waystone);
        return new Scenario(waystone, subLevel);
    }

    private static WaystoneBlockEntityBase requireWaystone(EmbeddedPlotLevelAccessor accessor, BlockPos pos) {
        if (accessor.getBlockEntity(pos) instanceof WaystoneBlockEntityBase waystoneBlockEntity) {
            return waystoneBlockEntity;
        }
        throw new IllegalStateException("Expected WaystoneBlockEntityBase at " + pos);
    }

    private static double averageMillis(long[] samples) {
        long total = 0L;
        for (long sample : samples) {
            total += sample;
        }
        return total / (double) samples.length / 1_000_000.0;
    }

    private static double maxMillis(long[] samples) {
        long max = 0L;
        for (long sample : samples) {
            max = Math.max(max, sample);
        }
        return max / 1_000_000.0;
    }

    private record Scenario(Waystone waystone, ServerSubLevel subLevel) {
    }

    private record ClassificationResult(int hits, long elapsedNanos) {
    }
}
