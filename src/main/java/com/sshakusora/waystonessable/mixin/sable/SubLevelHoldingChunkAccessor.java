package com.sshakusora.waystonessable.mixin.sable;

import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;
import java.util.UUID;

@Mixin(value = SubLevelHoldingChunk.class, remap = false)
public interface SubLevelHoldingChunkAccessor {
    @Nullable
    @Invoker("snatch")
    Collection<HoldingSubLevel> waystonessable$snatch(UUID subLevelId);

    @Accessor("loadedHoldingSubLevels")
    Object2ObjectMap<UUID, HoldingSubLevel> waystonessable$getLoadedHoldingSubLevels();
}
