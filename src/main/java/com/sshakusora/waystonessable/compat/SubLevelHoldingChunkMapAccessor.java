package com.sshakusora.waystonessable.compat;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import net.minecraft.world.level.ChunkPos;

/**
 * Narrow bridge to Sable's private holding-chunk lookup and dirty marker.
 *
 * <p>The compatibility layer uses this only when repairing a persisted pointer
 * whose payload is still present but whose holding-chunk index is stale.</p>
 */
public interface SubLevelHoldingChunkMapAccessor {

    SubLevelHoldingChunk waystonesSable$getOrLoadHoldingChunk(ChunkPos chunkPos, boolean create);

    void waystonesSable$setDirty(ChunkPos chunkPos);
}
