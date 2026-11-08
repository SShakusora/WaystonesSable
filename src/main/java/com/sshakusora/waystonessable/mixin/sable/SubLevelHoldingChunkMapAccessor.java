package com.sshakusora.waystonessable.mixin.sable;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public interface SubLevelHoldingChunkMapAccessor {
    @Accessor("level")
    ServerLevel waystonessable$getLevel();

    @Accessor("loadedHoldingChunks")
    Long2ObjectMap<SubLevelHoldingChunk> waystonessable$getLoadedHoldingChunks();

    @Invoker("setDirty")
    void waystonessable$setDirty(ChunkPos chunkPos);
}
