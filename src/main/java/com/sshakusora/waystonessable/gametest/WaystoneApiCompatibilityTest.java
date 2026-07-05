package com.sshakusora.waystonessable.gametest;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneGroups;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.joml.Vector3d;

@GameTestHolder(WaystonesSable.MOD_ID)
public final class WaystoneApiCompatibilityTest {

    private WaystoneApiCompatibilityTest() {
    }

    @GameTest(template = "gravity", timeoutTicks = 400)
    public static void subLevelWaystoneIsVisibleThroughWaystonesApi(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    new Vector3d(12.5, 76.0, 12.5),
                    "api_sublevel"
            );
            Waystone waystone = scenario.waystone();

            if (!WaystonesAPI.getWaystone(helper.getLevel().getServer(), waystone.getWaystoneUid()).isPresent()) {
                helper.fail("WaystonesAPI.getWaystone did not find the SubLevel waystone");
                return;
            }
            if (WaystonesAPI.getAllWaystones(helper.getLevel().getServer())
                    .noneMatch(candidate -> candidate.getWaystoneUid().equals(waystone.getWaystoneUid()))) {
                helper.fail("WaystonesAPI.getAllWaystones did not include the SubLevel waystone");
                return;
            }
            if (!waystone.getWaystoneGroups().contains(SableWaystoneGroups.SABLE_GROUP_ID)) {
                helper.fail("Waystones dynamic groups did not include the Sable group for a SubLevel waystone");
                return;
            }

            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            if (WaystonesAPI.isWaystoneActivated(player, waystone)) {
                helper.fail("SubLevel waystone should not start activated for a fresh mock player");
                return;
            }
            try {
                WaystonesAPI.activateWaystone(player, waystone);
            } catch (NullPointerException e) {
                if (e.getMessage() == null || !e.getMessage().contains("Connection.channel()")) {
                    throw e;
                }
            }
            if (!WaystonesAPI.isWaystoneActivated(player, waystone)) {
                helper.fail("WaystonesAPI.activateWaystone did not activate the SubLevel waystone for the mock player");
                return;
            }

            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            helper.succeed();
        });
    }
}
