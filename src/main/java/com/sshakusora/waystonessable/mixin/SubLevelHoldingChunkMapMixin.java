package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableTrackingPointerSync;
import com.sshakusora.waystonessable.compat.SubLevelHoldingChunkMapAccessor;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public abstract class SubLevelHoldingChunkMapMixin implements SubLevelHoldingChunkMapAccessor {

    @Inject(method = "saveAll()V", at = @At("RETURN"))
    private void waystonesSable$flushRecordedPointers(CallbackInfo ci) {
        SableTrackingPointerSync.flush();
    }

    @Override
    @Invoker("getOrLoadHoldingChunk")
    public abstract SubLevelHoldingChunk waystonesSable$getOrLoadHoldingChunk(ChunkPos chunkPos, boolean create);

    @Override
    @Invoker("setDirty")
    public abstract void waystonesSable$setDirty(ChunkPos chunkPos);
}
