package com.sshakusora.waystonessable.mixin.sable;

import com.sshakusora.waystonessable.storage.HoldingSubLevelOwner;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelHoldingChunk.class, remap = false)
public abstract class SubLevelHoldingChunkMixin {
    @Inject(method = "acceptHoldingSubLevel", at = @At("HEAD"))
    private void waystonessable$trackOwner(HoldingSubLevel held, CallbackInfo ci) {
        ((HoldingSubLevelOwner) (Object) held).waystonessable$setOwner((SubLevelHoldingChunk) (Object) this);
    }
}
