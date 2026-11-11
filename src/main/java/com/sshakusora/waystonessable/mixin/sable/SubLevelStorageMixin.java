package com.sshakusora.waystonessable.mixin.sable;

import com.sshakusora.waystonessable.storage.StorageRevision;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SubLevelStorage.class, remap = false)
public abstract class SubLevelStorageMixin implements StorageRevision {
    @Unique
    private long waystonessable$revision;

    @Override
    public long waystonessable$getRevision() {
        return waystonessable$revision;
    }

    @Inject(method = {"attemptSaveHoldingChunk", "attemptRemoveHoldingChunk",
            "attemptSaveSubLevel(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)V"}, at = @At("HEAD"))
    private void waystonessable$onWrite(CallbackInfo ci) {
        waystonessable$revision++;
    }

    @Inject(method = "attemptSaveSubLevel(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;", at = @At("HEAD"))
    private void waystonessable$onAllocate(CallbackInfoReturnable<GlobalSavedSubLevelPointer> cir) {
        waystonessable$revision++;
    }
}
