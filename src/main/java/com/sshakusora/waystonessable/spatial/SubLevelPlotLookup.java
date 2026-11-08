package com.sshakusora.waystonessable.spatial;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Locates loaded SubLevels and computes reserved plot addresses.
 */
public final class SubLevelPlotLookup {
    private SubLevelPlotLookup() {
    }

    @Nullable
    public static ServerSubLevel findLoadedSubLevel(final ServerSubLevelContainer container, final BlockPos position) {
        final LevelPlot plot = container.getPlot(new ChunkPos(position));
        if (plot == null || !plot.contains(Vec3.atCenterOf(position))) {
            return null;
        }

        return plot.getSubLevel() instanceof ServerSubLevel subLevel ? subLevel : null;
    }

    public static boolean ownsWaystone(final ServerSubLevel subLevel, final Waystone waystone) {
        return subLevel.getPlot().contains(Vec3.atCenterOf(waystone.getPos()));
    }

    public static int relativePlotX(final ServerSubLevelContainer container, final BlockPos position) {
        return (new ChunkPos(position).x >> container.getLogPlotSize()) - container.getOrigin().x;
    }

    public static int relativePlotZ(final ServerSubLevelContainer container, final BlockPos position) {
        return (new ChunkPos(position).z >> container.getLogPlotSize()) - container.getOrigin().y;
    }

    public static BlockPos plotLocalPosition(final ServerSubLevelContainer container,
                                             final LevelPlot plot, final BlockPos position) {
        final int logBlockSize = container.getLogPlotSize() + 4;
        return new BlockPos(position.getX() - (plot.plotPos.x << logBlockSize), position.getY(),
                position.getZ() - (plot.plotPos.z << logBlockSize));
    }
}
