package com.sshakusora.waystonessable.gametest;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import com.sshakusora.waystonessable.compat.SableWaystoneGroups;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.joml.Vector3d;

@GameTestHolder(WaystonesSable.MOD_ID)
public final class SubLevelWaystonePlacementTest {

    private SubLevelWaystonePlacementTest() {
    }

    @GameTest(template = "gravity", timeoutTicks = 400)
    public static void subLevelWaystoneIsRecognizedAndGrouped(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    new Vector3d(8.5, 72.0, 8.5),
                    "placement_sublevel"
            );

            Waystone waystone = scenario.waystone();
            if (!waystone.isValid()) {
                helper.fail("SubLevel waystone should be valid");
                return;
            }
            if (waystone.getDimension() != helper.getLevel().dimension()) {
                helper.fail("SubLevel waystone should keep the server level dimension");
                return;
            }
            if (!"placement_sublevel".equals(waystone.getName().getString())) {
                helper.fail("SubLevel waystone name was not preserved");
                return;
            }
            if (scenario.lowerEntity().getBlockState().getValue(WaystoneBlockBase.HALF) != DoubleBlockHalf.LOWER) {
                helper.fail("Lower waystone block entity is not the lower half");
                return;
            }
            if (scenario.upperEntity().getBlockState().getValue(WaystoneBlockBase.HALF) != DoubleBlockHalf.UPPER) {
                helper.fail("Upper waystone block entity is not the upper half");
                return;
            }
            if (!SableWaystoneCompat.isWaystoneOnSubLevel(helper.getLevel().getServer(), waystone)) {
                helper.fail("Tracked SubLevel waystone was not classified as a SubLevel waystone");
                return;
            }
            if (!waystone.isValidInLevel(helper.getLevel())) {
                helper.fail("SubLevel waystone should pass Waystones validation in its server level");
                return;
            }
            if (!waystone.getWaystoneGroups().contains(SableWaystoneGroups.SABLE_GROUP_ID)) {
                helper.fail("SubLevel waystone was not assigned to the Sable waystone group");
                return;
            }

            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            helper.succeed();
        });
    }

    @GameTest(template = "gravity", timeoutTicks = 400)
    public static void normalWaystoneIsNotClassifiedAsSubLevel(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            SubLevelWaystoneTestSupport.NormalWaystone scenario = SubLevelWaystoneTestSupport.createNormalWaystone(
                    helper,
                    new BlockPos(2, 3, 2),
                    "normal_world"
            );

            Waystone waystone = scenario.waystone();
            if (!waystone.isValid()) {
                helper.fail("Normal waystone should be valid");
                return;
            }
            if (!waystone.isValidInLevel(helper.getLevel())) {
                helper.fail("Normal waystone should pass vanilla Waystones validation");
                return;
            }
            if (SableWaystoneCompat.isWaystoneOnSubLevel(helper.getLevel().getServer(), waystone)) {
                helper.fail("Normal world waystone was incorrectly classified as a SubLevel waystone");
                return;
            }
            if (waystone.getWaystoneGroups().contains(SableWaystoneGroups.SABLE_GROUP_ID)) {
                helper.fail("Normal world waystone was incorrectly assigned to the Sable waystone group");
                return;
            }

            helper.succeed();
        });
    }
}
