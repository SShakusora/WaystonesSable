package com.sshakusora.waystonessable.mixin.sable;

import com.sshakusora.waystonessable.storage.HoldingSubLevelOwner;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = HoldingSubLevel.class, remap = false)
public abstract class HoldingSubLevelMixin implements HoldingSubLevelOwner {
    @Unique
    private @Nullable SubLevelHoldingChunk waystonessable$owner;

    @Override
    public @Nullable SubLevelHoldingChunk waystonessable$getOwner() {
        return waystonessable$owner;
    }

    @Override
    public void waystonessable$setOwner(SubLevelHoldingChunk chunk) {
        waystonessable$owner = chunk;
    }
}
