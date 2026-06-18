package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("all")
@Mixin(WaystoneBlockBase.class)
public abstract class WaystoneBlockBaseMixin implements BlockSubLevelAssemblyListener {

    @Unique
    private static final ThreadLocal<Boolean> waystonesSable$skipRemoval = ThreadLocal.withInitial(() -> false);

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void waystonesSable$beginMoveAwareRemoval(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving, CallbackInfo ci) {
        boolean skipRemoval = SableWaystoneCompat.consumeMovingWaystone(world, pos);
        waystonesSable$skipRemoval.set(skipRemoval);
        if (!skipRemoval && !state.is(newState.getBlock()) && world instanceof ServerLevel serverLevel
                && world.getBlockEntity(pos) instanceof WaystoneBlockEntityBase waystoneBlockEntity) {
            SableWaystoneCompat.removeWaystoneTrackingPoint(serverLevel, waystoneBlockEntity.getWaystone());
        }
    }

    @Inject(method = "onRemove", at = @At("RETURN"))
    private void waystonesSable$endMoveAwareRemoval(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving, CallbackInfo ci) {
        waystonesSable$skipRemoval.remove();
    }

    @Redirect(method = "onRemove", at = @At(value = "INVOKE", target = "Lnet/blay09/mods/waystones/core/WaystoneSyncManager;sendWaystoneRemovalToAll(Lnet/minecraft/server/MinecraftServer;Lnet/blay09/mods/waystones/api/Waystone;Z)V"))
    private void waystonesSable$skipTransientRemovalSync(MinecraftServer server, Waystone waystone, boolean wasDestroyed) {
        if (!waystonesSable$skipRemoval.get()) {
            WaystoneSyncManager.sendWaystoneRemovalToAll(server, waystone, wasDestroyed);
        }
    }

    @Redirect(method = "onRemove", at = @At(value = "INVOKE", target = "Lnet/blay09/mods/waystones/core/WaystoneManagerImpl;removeWaystone(Lnet/blay09/mods/waystones/api/Waystone;)V"))
    private void waystonesSable$skipTransientManagerRemoval(WaystoneManagerImpl manager, Waystone waystone) {
        if (!waystonesSable$skipRemoval.get()) {
            manager.removeWaystone(waystone);
        }
    }

    @Redirect(method = "onRemove", at = @At(value = "INVOKE", target = "Lnet/blay09/mods/waystones/core/PlayerWaystoneManager;removeKnownWaystone(Lnet/minecraft/server/MinecraftServer;Lnet/blay09/mods/waystones/api/Waystone;)V"))
    private void waystonesSable$skipTransientKnownWaystoneRemoval(MinecraftServer server, Waystone waystone) {
        if (!waystonesSable$skipRemoval.get()) {
            PlayerWaystoneManager.removeKnownWaystone(server, waystone);
        }
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        SableWaystoneCompat.markMovingWaystone(originLevel, oldPos);

        if (newState.hasProperty(WaystoneBlockBase.HALF) && newState.getValue(WaystoneBlockBase.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        if (!(resultingLevel.getBlockEntity(newPos) instanceof WaystoneBlockEntityBase waystoneBlockEntity)) {
            return;
        }

        Waystone waystone = waystoneBlockEntity.getWaystone();
        if (!waystone.isValid()) {
            return;
        }

        WaystoneSyncManager.sendWaystoneUpdateToAll(resultingLevel.getServer(), waystone);
    }
}
