package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaystoneBlockEntityBase.class)
public abstract class WaystoneBlockEntityBaseMixin {

    @Inject(method = "onLoad", at = @At("RETURN"))
    private void waystonesSable$updateWaystoneTrackingPoint(CallbackInfo ci) {
        WaystoneBlockEntityBase self = (WaystoneBlockEntityBase) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Waystone waystone = self.getWaystone();
        if (waystone.isValid()) {
            SableWaystoneCompat.updateWaystoneTrackingPoint(serverLevel, waystone);
        }
    }
}
