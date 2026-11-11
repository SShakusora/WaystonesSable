package com.sshakusora.waystonessable.assembly;

import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Structural pairing shared by Sable selection, optional Create collection, and block entity loading.
 */
public final class WaystoneBlockPairs {
    private WaystoneBlockPairs() {
    }

    public static boolean isDoubleBlock(BlockState state) {
        return state.getBlock() instanceof WaystoneBlockBase && state.hasProperty(WaystoneBlockBase.HALF);
    }

    public static BlockPos partnerPos(BlockState state, BlockPos pos) {
        return state.getValue(WaystoneBlockBase.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
    }

    public static boolean isPair(BlockState state, BlockState partner) {
        return isDoubleBlock(state) && partner.is(state.getBlock())
                && partner.hasProperty(WaystoneBlockBase.HALF)
                && partner.getValue(WaystoneBlockBase.HALF) != state.getValue(WaystoneBlockBase.HALF);
    }

    public static boolean connects(BlockPos pos, BlockState state, BlockPos candidate, BlockState candidateState) {
        return isPair(state, candidateState) && partnerPos(state, pos).equals(candidate);
    }
}
