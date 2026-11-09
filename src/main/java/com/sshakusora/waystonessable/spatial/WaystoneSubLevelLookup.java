package com.sshakusora.waystonessable.spatial;

import com.sshakusora.waystonessable.association.WaystoneBindingSavedData;
import com.sshakusora.waystonessable.association.WaystoneSubLevelBinding;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class WaystoneSubLevelLookup {
    private WaystoneSubLevelLookup() {
    }

    public static boolean isOnSubLevel(final MinecraftServer server, final Waystone waystone) {
        final ServerLevel level = server.getLevel(waystone.getDimension());
        if (level == null) {
            return false;
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        final BlockPos pos = waystone.getPos();
        if (container == null || !container.inBounds(new ChunkPos(pos))) {
            return false;
        }

        final ServerSubLevel loaded = SubLevelPlotLookup.findLoadedSubLevel(container, pos);
        if (loaded != null) {
            return !loaded.isRemoved();
        }

        final WaystoneSubLevelBinding binding =
                WaystoneBindingSavedData.getOrLoad(server).get(waystone.getWaystoneUid());
        if (binding == null
                || !binding.dimension().equals(waystone.getDimension().location().toString())
                || binding.plotX() != SubLevelPlotLookup.relativePlotX(container, pos)
                || binding.plotZ() != SubLevelPlotLookup.relativePlotZ(container, pos)) {
            return false;
        }

        final int logBlockSize = container.getLogPlotSize() + 4;
        final int plotOriginX = (binding.plotX() + container.getOrigin().x) << logBlockSize;
        final int plotOriginZ = (binding.plotZ() + container.getOrigin().y) << logBlockSize;
        return binding.localPos().equals(pos.offset(-plotOriginX, 0, -plotOriginZ));
    }
}
