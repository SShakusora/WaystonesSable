package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableTrackingPointerSync;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HoldingSubLevel.class, remap = false)
public abstract class HoldingSubLevelMixin {

    @Inject(
            method = "setPointer(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;)V",
            at = @At("RETURN")
    )
    private void waystonesSable$recordPointer(
            GlobalSavedSubLevelPointer pointer,
            CallbackInfo ci
    ) {
        HoldingSubLevel self = (HoldingSubLevel) (Object) this;
        SableTrackingPointerSync.record(self.data().uuid(), pointer);
    }
}
