package com.sshakusora.waystonessable.gametest;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import com.sshakusora.waystonessable.compat.SableWaystoneGroups;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.blay09.mods.waystones.api.MutablePersonalizedWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.menu.WaystoneSelectionListBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.joml.Vector3d;

import java.util.List;

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

    @GameTest(template = "gravity", timeoutTicks = 400)
    public static void selectionMenuSendsVisiblePositionForSubLevelWaystone(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            SubLevelWaystoneTestSupport.SubLevelWaystone scenario = SubLevelWaystoneTestSupport.createSubLevelWaystone(
                    helper,
                    container,
                    new Vector3d(24.5, 76.0, 24.5),
                    "menu_visible_position"
            );
            Waystone waystone = scenario.waystone();
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

            List<MutablePersonalizedWaystone> menuWaystones = new WaystoneSelectionListBuilder(player)
                    .withWaystones(List.of(waystone))
                    .skipSortingIndexUpdate()
                    .build();

            MutablePersonalizedWaystone menuWaystone = menuWaystones.stream()
                    .filter(candidate -> candidate.getWaystoneUid().equals(waystone.getWaystoneUid()))
                    .findFirst()
                    .orElse(null);
            if (menuWaystone == null) {
                helper.fail("Selection menu did not include the SubLevel waystone");
                return;
            }

            Vec3 visiblePos = SableWaystoneCompat.getVisibleWaystonePos(helper.getLevel(), waystone);
            BlockPos expectedClientPos = BlockPos.containing(visiblePos);
            BlockPos sentClientPos = menuWaystone.getBackingWaystone().getPos();
            if (!expectedClientPos.equals(sentClientPos)) {
                helper.fail("Selection menu sent " + sentClientPos + " instead of visible SubLevel position " + expectedClientPos);
                return;
            }

            if (waystone.getPos().equals(sentClientPos)) {
                helper.fail("Selection menu still sent the raw plot coordinate for the SubLevel waystone");
                return;
            }

            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, scenario.subLevel());
            helper.succeed();
        });
    }
}
