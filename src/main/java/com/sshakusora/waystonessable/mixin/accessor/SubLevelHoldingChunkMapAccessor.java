package com.sshakusora.waystonessable.mixin.accessor;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SubLevelHoldingChunkMap.class)
public interface SubLevelHoldingChunkMapAccessor {

    @Accessor("loadedHoldingChunks")
    Long2ObjectMap<SubLevelHoldingChunk> waystonesSable$getLoadedHoldingChunks();
}
