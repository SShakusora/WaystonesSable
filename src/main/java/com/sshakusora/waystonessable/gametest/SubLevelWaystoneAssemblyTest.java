package com.sshakusora.waystonessable.gametest;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.List;

@GameTestHolder(WaystonesSable.MOD_ID)
public final class SubLevelWaystoneAssemblyTest {

    private SubLevelWaystoneAssemblyTest() {
    }

    @GameTest(template = "gravity", timeoutTicks = 800)
    public static void assembledWaystoneDetachesOriginalAndTracksSubLevelCopy(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            ServerSubLevelContainer container = SubLevelWaystoneTestSupport.requireContainer(helper);
            SubLevelWaystoneTestSupport.NormalWaystone original = SubLevelWaystoneTestSupport.createNormalWaystone(
                    helper,
                    new BlockPos(2, 3, 2),
                    "assembled_waystone"
            );
            Waystone originalWaystone = original.waystone();
            BlockPos oldLowerPos = originalWaystone.getPos();
            BlockPos oldUpperPos = oldLowerPos.above();

            ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                    helper.getLevel(),
                    oldLowerPos,
                    List.of(oldLowerPos, oldUpperPos),
                    new BoundingBox3i(
                            oldLowerPos.getX(),
                            oldLowerPos.getY(),
                            oldLowerPos.getZ(),
                            oldUpperPos.getX(),
                            oldUpperPos.getY(),
                            oldUpperPos.getZ()
                    )
            );

            if (original.lowerEntity().getWaystone().isValid()) {
                helper.fail("Original lower block entity still holds a valid waystone after assembly");
                return;
            }

            BlockPos newLowerPos = subLevel.getPlot().getCenterBlock();
            if (!(helper.getLevel().getBlockEntity(newLowerPos) instanceof WaystoneBlockEntityBase newLowerEntity)) {
                helper.fail("Assembled SubLevel does not contain a lower waystone block entity at " + newLowerPos);
                return;
            }

            Waystone movedWaystone = newLowerEntity.getWaystone();
            if (!movedWaystone.isValid()) {
                helper.fail("Moved SubLevel waystone is not valid");
                return;
            }
            if (!movedWaystone.getWaystoneUid().equals(originalWaystone.getWaystoneUid())) {
                helper.fail("Moved SubLevel waystone did not preserve the original waystone UUID");
                return;
            }
            if (!SableWaystoneCompat.isWaystoneOnSubLevel(helper.getLevel().getServer(), movedWaystone)) {
                helper.fail("Assembled waystone was not tracked as a SubLevel waystone");
                return;
            }
            if (!movedWaystone.isValidInLevel(helper.getLevel())) {
                helper.fail("Assembled SubLevel waystone should pass Waystones validation");
                return;
            }

            SubLevelWaystoneTestSupport.clearSubLevelPlot(container, subLevel);
            helper.succeed();
        });
    }
}
