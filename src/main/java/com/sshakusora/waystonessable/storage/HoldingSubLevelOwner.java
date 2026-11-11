package com.sshakusora.waystonessable.storage;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import org.jetbrains.annotations.Nullable;

public interface HoldingSubLevelOwner {
    @Nullable SubLevelHoldingChunk waystonessable$getOwner();

    void waystonessable$setOwner(SubLevelHoldingChunk chunk);
}
