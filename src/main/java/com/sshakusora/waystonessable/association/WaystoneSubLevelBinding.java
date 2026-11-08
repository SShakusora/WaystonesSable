package com.sshakusora.waystonessable.association;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persistent ownership and last known storage address of a Waystone.
 */
public record WaystoneSubLevelBinding(String dimension, UUID subLevelId, int plotX, int plotZ,
                                      BlockPos localPos, @Nullable GlobalSavedSubLevelPointer pointer) {
}
