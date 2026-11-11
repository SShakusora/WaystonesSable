package com.sshakusora.waystonessable.assembly;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Treats the two halves as a single structure during Create/Simulated block collection.
 */
public final class WaystoneAssemblyConnections {
    private WaystoneAssemblyConnections() {
    }

    public static void register() {
        BlockMovementChecks.registerAttachedCheck(WaystoneAssemblyConnections::checkAttached);
    }

    private static BlockMovementChecks.CheckResult checkAttached(
            BlockState state, Level level, BlockPos pos, Direction direction) {
        if (!WaystoneBlockPairs.isDoubleBlock(state)) return BlockMovementChecks.CheckResult.PASS;
        final var partner = WaystoneBlockPairs.partnerPos(state, pos);
        if (!partner.equals(pos.relative(direction)) || level.isOutsideBuildHeight(partner) || !level.isLoaded(partner)) {
            return BlockMovementChecks.CheckResult.PASS;
        }
        return WaystoneBlockPairs.isPair(state, level.getBlockState(partner))
                ? BlockMovementChecks.CheckResult.SUCCESS : BlockMovementChecks.CheckResult.PASS;
    }
}
