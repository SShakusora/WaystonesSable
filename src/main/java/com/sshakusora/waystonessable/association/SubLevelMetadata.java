package com.sshakusora.waystonessable.association;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A detached pose snapshot; never retains the ship's chunks or full NBT.
 */
public final class SubLevelMetadata {
    private final int plotX;
    private final int plotZ;
    private final Pose3d pose;
    private final @Nullable GlobalSavedSubLevelPointer pointer;

    public SubLevelMetadata(int plotX, int plotZ, Pose3dc pose,
                            @Nullable GlobalSavedSubLevelPointer pointer) {
        this.plotX = plotX;
        this.plotZ = plotZ;
        this.pose = new Pose3d(pose);
        this.pointer = pointer;
    }

    public boolean matchesPlot(int x, int z) {
        return plotX == x && plotZ == z;
    }

    public boolean matches(int x, int z, Pose3dc other, @Nullable GlobalSavedSubLevelPointer address) {
        return matchesPlot(x, z) && Objects.equals(pointer, address)
                && pose.position().equals(other.position()) && pose.orientation().equals(other.orientation())
                && pose.rotationPoint().equals(other.rotationPoint()) && pose.scale().equals(other.scale());
    }

    public int plotX() {
        return plotX;
    }

    public int plotZ() {
        return plotZ;
    }

    public Pose3d pose() {
        return new Pose3d(pose);
    }

    public @Nullable GlobalSavedSubLevelPointer pointer() {
        return pointer;
    }
}
