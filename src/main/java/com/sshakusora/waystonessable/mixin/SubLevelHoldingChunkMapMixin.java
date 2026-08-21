package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableTrackingPointerSync;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public abstract class SubLevelHoldingChunkMapMixin {

    @Inject(method = "saveAll()V", at = @At("RETURN"))
    private void waystonesSable$flushRecordedPointers(CallbackInfo ci) {
        SableTrackingPointerSync.flush();
    }
}
