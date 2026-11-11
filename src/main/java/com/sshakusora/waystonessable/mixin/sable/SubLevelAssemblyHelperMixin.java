package com.sshakusora.waystonessable.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sshakusora.waystonessable.association.WaystoneAssemblyMigration;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyHelperMixin {
    @WrapMethod(method = "moveBlocks")
    private static void waystonessable$moveIdentities(ServerLevel level,
                                                      SubLevelAssemblyHelper.AssemblyTransform transform,
                                                      Iterable<BlockPos> blocks, Operation<Void> original) {
        final var moves = WaystoneAssemblyMigration.capture(level, transform, blocks);
        try {
            original.call(level, transform, blocks);
        } finally {
            WaystoneAssemblyMigration.complete(transform, moves);
        }
    }

    @WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V"))
    private static void waystonessable$notifySource(ServerLevel destination, BlockPos pos, BlockState oldState,
                                                    BlockState newState, int flags, Operation<Void> original,
                                                    ServerLevel source, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                    Iterable<BlockPos> blocks) {
        if (Sable.HELPER.isInPlotGrid(source, pos)) {
            final var container = SubLevelContainer.getContainer(source);
            final var plot = container.getPlot(new ChunkPos(pos));
            if (plot == null || plot.getChunkHolder(plot.toLocal(new ChunkPos(pos))) == null) return;
        }
        original.call(source, pos, oldState, newState, flags);
    }
}
