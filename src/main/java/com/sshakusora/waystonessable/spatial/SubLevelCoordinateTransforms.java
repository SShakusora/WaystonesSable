package com.sshakusora.waystonessable.spatial;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Converts Sable storage coordinates to visible world coordinates.
 */
public final class SubLevelCoordinateTransforms {
    private SubLevelCoordinateTransforms() {
    }

    private static Direction nearestDirection(final Vec3 vector) {
        final double x = Math.abs(vector.x);
        final double y = Math.abs(vector.y);
        final double z = Math.abs(vector.z);

        if (x >= y && x >= z) {
            return vector.x >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        if (y >= z) {
            return vector.y >= 0.0 ? Direction.UP : Direction.DOWN;
        }
        return vector.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static Vec3 getFeetStoragePos(final Vec3 waystoneTargetPosition) {
        return new Vec3(
                waystoneTargetPosition.x,
                waystoneTargetPosition.y - 0.5,
                waystoneTargetPosition.z
        );
    }

    /**
     * Waystones targets block centers; subtract half a block before transforming entity feet.
     */
    public static Vec3 toWorldTeleportPosition(final Pose3dc pose, final Vec3 targetPosition) {
        return pose.transformPosition(getFeetStoragePos(targetPosition));
    }

    public static Direction toWorldDirection(final Pose3dc pose, final Direction direction) {
        return nearestDirection(pose.transformNormal(new Vec3(
                direction.getStepX(), direction.getStepY(), direction.getStepZ())));
    }
}
