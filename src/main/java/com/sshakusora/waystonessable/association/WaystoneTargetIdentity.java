package com.sshakusora.waystonessable.association;

import com.sshakusora.waystonessable.spatial.SubLevelPlotLookup;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable request identity, independent of mutable Waystone proxies and recycled plots.
 */
public record WaystoneTargetIdentity(UUID waystoneId, ResourceKey<Level> dimension, BlockPos position) {
    public static WaystoneTargetIdentity capture(Waystone waystone) {
        return new WaystoneTargetIdentity(waystone.getWaystoneUid(), waystone.getDimension(), waystone.getPos().immutable());
    }

    public boolean unchanged(Waystone waystone) {
        return waystoneId.equals(waystone.getWaystoneUid()) && dimension.equals(waystone.getDimension())
                && position.equals(waystone.getPos());
    }

    public boolean matches(ServerSubLevelContainer container, ServerSubLevel subLevel, UUID expectedSubLevel) {
        final var level = container.getLevel();
        if (subLevel.isRemoved() || !expectedSubLevel.equals(subLevel.getUniqueId())
                || subLevel.getLevel() != level || !dimension.equals(level.dimension())
                || SubLevelPlotLookup.findLoadedSubLevel(container, position) != subLevel) return false;
        // The plot was verified above, so this cannot fall through to vanilla reserved-coordinate loading.
        final var blockEntity = level.getBlockEntity(position);
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() != level
                || !position.equals(blockEntity.getBlockPos())) return false;
        // Packed/restored BEs may not have received onLoad yet. Their persisted UUID is authoritative then.
        return waystoneId.equals(WaystoneBlockIdentities.get(blockEntity));
    }

    @Contract("_, null -> false")
    public boolean matchesBinding(ServerSubLevelContainer container, @Nullable WaystoneSubLevelBinding binding) {
        if (binding == null || !dimension.location().toString().equals(binding.dimension())
                || !dimension.equals(container.getLevel().dimension())
                || binding.plotX() != SubLevelPlotLookup.relativePlotX(container, position)
                || binding.plotZ() != SubLevelPlotLookup.relativePlotZ(container, position)) return false;
        final int shift = container.getLogPlotSize() + 4;
        return binding.localPos().equals(position.offset(
                -((binding.plotX() + container.getOrigin().x) << shift), 0,
                -((binding.plotZ() + container.getOrigin().y) << shift)));
    }
}
