package com.sshakusora.waystonessable.selection;

import net.blay09.mods.waystones.api.MutablePersonalizedWaystone;
import net.blay09.mods.waystones.api.MutablePersonalizedWaystoneDelegate;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneDelegate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Adds menu-only groups and display coordinates while retaining server teleport positions.
 */
final class WorldPositionDisplayWaystone extends MutablePersonalizedWaystoneDelegate {
    private final Waystone displayBacking;
    private final boolean onSubLevel;

    WorldPositionDisplayWaystone(final MutablePersonalizedWaystone delegate,
                                 @Nullable final Vec3 worldCenter, final boolean onSubLevel) {
        super(delegate);
        this.onSubLevel = onSubLevel;
        this.displayBacking = worldCenter != null ? new WaystoneDelegate(delegate.getBackingWaystone()) {
            private final BlockPos worldPos = BlockPos.containing(worldCenter);

            @Override
            public BlockPos getPos() {
                return worldPos;
            }
        } : delegate.getBackingWaystone();
    }

    @Override
    public Waystone getBackingWaystone() {
        return displayBacking;
    }

    @Override
    public Set<ResourceLocation> getConfiguredGroups() {
        return withSubLevelGroup(delegate.getConfiguredGroups());
    }

    @Override
    public Set<ResourceLocation> getWaystoneGroups() {
        return withSubLevelGroup(delegate.getWaystoneGroups());
    }

    private Set<ResourceLocation> withSubLevelGroup(final Set<ResourceLocation> original) {
        final Set<ResourceLocation> groups = new HashSet<>(original);
        if (onSubLevel) {
            groups.add(SubLevelWaystoneGroup.SABLE.identifier());
        } else {
            groups.remove(SubLevelWaystoneGroup.SABLE.identifier());
        }
        return Set.copyOf(groups);
    }
}
