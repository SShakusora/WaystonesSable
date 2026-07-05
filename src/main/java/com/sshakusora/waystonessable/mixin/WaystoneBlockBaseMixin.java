package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.blay09.mods.waystones.core.WaystoneImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WaystoneBlockBase.class)
public abstract class WaystoneBlockBaseMixin implements BlockSubLevelAssemblyListener {

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        Waystone movedWaystone = null;
        if (originLevel.getBlockEntity(oldPos) instanceof WaystoneBlockEntityBase oldWaystoneBlockEntity) {
            movedWaystone = oldWaystoneBlockEntity.getWaystone();
            oldWaystoneBlockEntity.detachWaystone();
        }

        if (newState.hasProperty(WaystoneBlockBase.HALF) && newState.getValue(WaystoneBlockBase.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        if (!(resultingLevel.getBlockEntity(newPos) instanceof WaystoneBlockEntityBase waystoneBlockEntity)) {
            return;
        }

        waystoneBlockEntity.onLoad();
        Waystone waystone = waystoneBlockEntity.getWaystone();
        if (!waystone.isValid() && movedWaystone instanceof WaystoneImpl movedWaystoneImpl) {
            waystoneBlockEntity.initializeFromExisting(resultingLevel, movedWaystoneImpl, ItemStack.EMPTY);
            waystone = waystoneBlockEntity.getWaystone();
        }
        if (!waystone.isValid()) {
            return;
        }

        SableWaystoneCompat.updateWaystoneTrackingPoint(resultingLevel, waystone);
        WaystoneSyncManager.sendWaystoneUpdateToAll(resultingLevel.getServer(), waystone);
    }
}
