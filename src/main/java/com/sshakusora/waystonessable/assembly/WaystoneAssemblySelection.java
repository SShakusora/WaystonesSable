package com.sshakusora.waystonessable.assembly;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Completes explicit region selections before Sable allocates a plot or moves entities.
 */
public record WaystoneAssemblySelection(List<BlockPos> blocks, BoundingBox3ic bounds) {
    public static WaystoneAssemblySelection complete(ServerLevel level, Iterable<BlockPos> blocks, BoundingBox3ic bounds) {
        final var selected = new LinkedHashSet<BlockPos>();
        // Snapshot first: callers may supply immutable collections or iterators reusing a mutable position.
        for (final var pos : blocks) selected.add(pos.immutable());
        final var expandedBounds = new BoundingBox3i(bounds);
        for (final var pos : List.copyOf(selected)) {
            final var state = level.getBlockState(pos);
            if (!WaystoneBlockPairs.isDoubleBlock(state)) continue;
            final var partner = WaystoneBlockPairs.partnerPos(state, pos);
            if (level.isOutsideBuildHeight(partner) || !level.isLoaded(partner)
                    || !WaystoneBlockPairs.isPair(state, level.getBlockState(partner))) {
                // A partial move is worse than rejecting a malformed structure. Do not allocate or mutate anything.
                throw new IllegalArgumentException(Component.translatable("chat.waystonessable.incomplete_waystone",
                        pos.getX(), pos.getY(), pos.getZ()).getString());
            }
            if (selected.add(partner)) expandedBounds.expandTo(partner.getX(), partner.getY(), partner.getZ());
        }
        return new WaystoneAssemblySelection(List.copyOf(selected), expandedBounds);
    }
}
