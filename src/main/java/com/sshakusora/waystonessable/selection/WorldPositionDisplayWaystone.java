package com.sshakusora.waystonessable.selection;

import net.blay09.mods.waystones.api.MutablePersonalizedWaystone;
import net.blay09.mods.waystones.api.MutablePersonalizedWaystoneDelegate;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneDelegate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Changes only the backing position serialized for menu display.
 */
final class WorldPositionDisplayWaystone extends MutablePersonalizedWaystoneDelegate {
    private final Waystone displayBacking;

    WorldPositionDisplayWaystone(final MutablePersonalizedWaystone delegate, final Vec3 worldCenter) {
        super(delegate);
        final BlockPos worldPos = BlockPos.containing(worldCenter);
        this.displayBacking = new WaystoneDelegate(delegate.getBackingWaystone()) {
            @Override
            public BlockPos getPos() {
                return worldPos;
            }
        };
    }

    @Override
    public Waystone getBackingWaystone() {
        return displayBacking;
    }
}
