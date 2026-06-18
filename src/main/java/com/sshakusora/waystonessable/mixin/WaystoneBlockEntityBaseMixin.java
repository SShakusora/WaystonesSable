package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("all")
@Mixin(WaystoneBlockEntityBase.class)
public abstract class WaystoneBlockEntityBaseMixin {

    @Inject(method = "onLoad", at = @At("RETURN"))
    private void waystonesSable$updateMovedWaystoneLocation(CallbackInfo ci) {
        WaystoneBlockEntityBase self = (WaystoneBlockEntityBase) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Waystone waystone = self.getWaystone();
        if (!(waystone instanceof MutableWaystone mutableWaystone) || !waystone.isValid()) {
            return;
        }

        boolean dimensionChanged = waystone.getDimension() != serverLevel.dimension();
        boolean positionChanged = !waystone.getPos().equals(self.getBlockPos());
        if (!dimensionChanged && !positionChanged) {
            SableWaystoneCompat.updateWaystoneTrackingPoint(serverLevel, waystone);
            return;
        }

        mutableWaystone.setDimension(serverLevel.dimension());
        mutableWaystone.setPos(self.getBlockPos());
        SableWaystoneCompat.updateWaystoneTrackingPoint(serverLevel, waystone);
        WaystoneManagerImpl.get(serverLevel.getServer()).setDirty();
        WaystoneSyncManager.sendWaystoneUpdateToAll(serverLevel.getServer(), waystone);
    }
}
