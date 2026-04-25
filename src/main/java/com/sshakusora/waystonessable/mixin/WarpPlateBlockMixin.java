package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.Sable;
import net.blay09.mods.waystones.block.WarpPlateBlock;
import net.blay09.mods.waystones.block.entity.WarpPlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WarpPlateBlock.class)
public class WarpPlateBlockMixin {

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void waystonesSable$triggerWarpPlateFromSubLevel(BlockState blockState, Level world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (world.isClientSide) {
            return;
        }

        if (Sable.HELPER.getContaining(world, pos) == null) {
            return;
        }

        if (!SableWaystoneCompat.isEntityInsideBlock(world, pos, entity)) {
            return;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof WarpPlateBlockEntity warpPlate) {
            warpPlate.onEntityCollision(entity);
        }
    }
}
