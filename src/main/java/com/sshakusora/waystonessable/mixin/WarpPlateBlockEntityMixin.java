package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.blay09.mods.waystones.block.entity.WarpPlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WarpPlateBlockEntity.class)
public abstract class WarpPlateBlockEntityMixin extends BlockEntity {

    protected WarpPlateBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Inject(method = "isEntityOnWarpPlate", at = @At("HEAD"), cancellable = true)
    private void waystonesSable$checkEntityOnSubLevelWarpPlate(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Level level = getLevel();
        if (level == null) {
            return;
        }

        boolean blockOnSubLevel = SableCompanion.INSTANCE.getContaining(level, worldPosition) != null;
        boolean guardedArrival = SableWaystoneCompat.hasWarpPlateArrivalGuard(level, worldPosition, entity);
        if (!blockOnSubLevel && !guardedArrival) {
            return;
        }

        SableWaystoneCompat.clearWarpPlateArrivalGuardIfNeeded(level, worldPosition, entity);
        cir.setReturnValue(SableWaystoneCompat.isEntityInsideBlock(level, worldPosition, entity));
    }

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void waystonesSable$ignoreWarpPlateArrival(Entity entity, CallbackInfo ci) {
        Level level = getLevel();
        if (level == null) {
            return;
        }

        if (SableWaystoneCompat.consumeWarpPlateArrivalGuard(level, worldPosition, entity)) {
            ((WarpPlateBlockEntity) (Object) this).markEntityForCooldown(entity);
            ci.cancel();
        }
    }
}
