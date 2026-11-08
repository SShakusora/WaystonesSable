package com.sshakusora.waystonessable.mixin.sable;

import com.sshakusora.waystonessable.association.WaystoneBindingSavedData;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public abstract class SubLevelHoldingChunkMapMixin {
    @Inject(
            method = "moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Lnet/minecraft/world/level/ChunkPos;)Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;",
            at = @At("RETURN")
    )
    private void waystonessable$publishPointer(final @Nullable ServerSubLevel subLevel,
                                               final SubLevelData data,
                                               final @Nullable GlobalSavedSubLevelPointer lastPointer,
                                               final ChunkPos moveToChunk,
                                               final CallbackInfoReturnable<GlobalSavedSubLevelPointer> callback) {
        final GlobalSavedSubLevelPointer pointer = callback.getReturnValue();
        if (pointer != null) {
            final SubLevelHoldingChunkMap map = (SubLevelHoldingChunkMap) (Object) this;
            WaystoneBindingSavedData.updateSubLevelPointer(
                    ((SubLevelHoldingChunkMapAccessor) map).waystonessable$getLevel(),
                    data.uuid(),
                    pointer
            );
        }
    }
}
