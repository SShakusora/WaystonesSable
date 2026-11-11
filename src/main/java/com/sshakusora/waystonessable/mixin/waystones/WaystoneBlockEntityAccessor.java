package com.sshakusora.waystonessable.mixin.waystones;

import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/**
 * Reads the persisted identity without initializing a Waystone or firing update events.
 */
@Mixin(value = WaystoneBlockEntityBase.class, remap = false)
public interface WaystoneBlockEntityAccessor {
    @Accessor("waystoneUid")
    @Nullable UUID waystonessable$getStoredUid();
}
