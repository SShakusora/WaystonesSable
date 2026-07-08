package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyHelperMixin {

    private static final ThreadLocal<Boolean> waystonesSable$reenteringAssembleBlocks = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "assembleBlocks(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/Iterable;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void waystonesSable$includeCompleteWaystones(ServerLevel level, BlockPos anchor, Iterable<BlockPos> blocks, BoundingBox3ic bounds, CallbackInfoReturnable<ServerSubLevel> cir) {
        if (waystonesSable$reenteringAssembleBlocks.get()) {
            return;
        }

        SableWaystoneCompat.ExpandedWaystoneAssembly expandedAssembly;
        try {
            expandedAssembly = SableWaystoneCompat.expandWaystoneAssemblyBlocks(level, blocks, bounds);
        } catch (RuntimeException exception) {
            WaystonesSable.LOGGER.warn("Failed to inspect Sable assembly at {} for Waystone counterparts; falling back to Sable's original block set", anchor, exception);
            return;
        }

        if (expandedAssembly.changed()) {
            WaystonesSable.LOGGER.info(
                    "Expanding Sable assembly at {} from {} to {} block(s) by adding {} Waystone counterpart block(s)",
                    anchor,
                    expandedAssembly.originalBlockCount(),
                    expandedAssembly.expandedBlockCount(),
                    expandedAssembly.addedWaystoneParts()
            );
        }

        waystonesSable$reenteringAssembleBlocks.set(true);
        try {
            cir.setReturnValue(SubLevelAssemblyHelper.assembleBlocks(
                    level,
                    anchor,
                    expandedAssembly.blocks(),
                    expandedAssembly.changed() ? expandedAssembly.bounds() : bounds
            ));
        } finally {
            waystonesSable$reenteringAssembleBlocks.remove();
        }
    }
}
