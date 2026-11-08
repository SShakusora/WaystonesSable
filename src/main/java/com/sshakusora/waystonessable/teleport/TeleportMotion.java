package com.sshakusora.waystonessable.teleport;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.LivingEntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Motion belonging to a departed platform must never survive a Waystone teleport.
 */
public final class TeleportMotion {
    private TeleportMotion() {
    }

    public static void arrive(final Entity entity, final @Nullable UUID subLevelId, final Vec3 localAnchor) {
        final TeleportArrivalExtension extension = (TeleportArrivalExtension) entity;
        extension.waystonessable$setArrival(null);
        clearMotion(entity);
        if (subLevelId != null) {
            extension.waystonessable$setArrival(new Arrival(entity.level().dimension(), subLevelId, localAnchor));
        }
    }

    private static void clearMotion(final Entity entity) {
        final EntityMovementExtension movement = (EntityMovementExtension) entity;
        movement.sable$setTrackingSubLevel(null);
        movement.sable$setLastTrackingSubLevelID(null);
        ((EntityStickExtension) entity).sable$setPlotPosition(null);
        if (entity instanceof LivingEntity) {
            ((LivingEntityMovementExtension) entity).sable$getInheritedVelocity().zero();
            // A remote entity may still have interpolation steps belonging to the source plot.
            ((LivingEntityStickExtension) entity).sable$applyLerp();
        }
        if (entity instanceof Player) {
            ((PlayerFreezeExtension) entity).sable$freezeTo(null, null);
        }

        // Sable's travel RETURN hook can otherwise put old inheritedMotion back into the velocity.
        final var collision = movement.sable$getCollisionInfo();
        if (collision != null) {
            collision.inheritedMotion = null;
            collision.trackingSubLevel = null;
            collision.preTrackingSubLevel = null;
            collision.preDeltaMovement = Vec3.ZERO;
            collision.motion = Vec3.ZERO;
            collision.firstCollisions = null;
            collision.horizontalCollision = false;
            collision.verticalCollision = false;
            collision.verticalCollisionBelow = false;
            collision.minorHorizontalCollision = false;
            collision.subLevelHorizontalCollision = false;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0f;
    }

    public static void beforeEntityTick(final EntityTickEvent.Pre event) {
        prepareArrival(event);
        if (!event.isCanceled()) {
            // Passenger rideTick does not fire EntityTickEvent.Pre. The vehicle positions its
            // riders, so discard their one-shot anchors now instead of replaying them on dismount.
            for (final Entity passenger : event.getEntity().getIndirectPassengers()) {
                final TeleportArrivalExtension rider = (TeleportArrivalExtension) passenger;
                if (rider.waystonessable$getArrival() != null) {
                    rider.waystonessable$setArrival(null);
                    clearMotion(passenger);
                    ((EntityMovementExtension) passenger).sable$setTrackingSubLevel(
                            ((EntityMovementExtension) event.getEntity()).sable$getTrackingSubLevel());
                }
            }
        }
    }

    private static void prepareArrival(final EntityTickEvent.Pre event) {
        final Entity entity = event.getEntity();
        final TeleportArrivalExtension extension = (TeleportArrivalExtension) entity;
        final Arrival arrival = extension.waystonessable$getArrival();
        if (arrival == null) {
            return;
        }
        if (entity.isRemoved() || !entity.level().dimension().equals(arrival.dimension)) {
            extension.waystonessable$setArrival(null);
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(entity.level());
        final SubLevel target = container != null ? container.getSubLevel(arrival.subLevelId) : null;
        if (target == null || target.isRemoved()
                || (entity.level().isClientSide()
                && target instanceof ClientSubLevel clientTarget && !clientTarget.isFinalized())) {
            // The arrival payload may precede the destination's SubLevel data on a remote client.
            if (entity.level().isClientSide() && --arrival.remainingTicks > 0) {
                event.setCanceled(true);
            } else {
                extension.waystonessable$setArrival(null);
            }
            return;
        }
        extension.waystonessable$setArrival(null);
        clearMotion(entity);
        // Native Sable movement applies lastPose -> logicalPose during this tick. Rebase exactly
        // once, just before ticking, rather than applying that displacement twice at the landing.
        final Vec3 previousPosition = target.lastPose().transformPosition(arrival.localAnchor);
        if (entity.level().isClientSide() && !entity.isControlledByLocalInstance()) {
            // Remote entities use Sable's plot interpolation instead of local movement physics.
            entity.setPos(target.logicalPose().transformPosition(arrival.localAnchor));
            ((EntityStickExtension) entity).sable$setPlotPosition(arrival.localAnchor);
            if (entity instanceof LivingEntity) {
                ((LivingEntityStickExtension) entity).sable$plotLerpTo(arrival.localAnchor, 0);
            }
        } else {
            entity.setPos(previousPosition);
        }
        ((EntityMovementExtension) entity).sable$setTrackingSubLevel(target);
        entity.xOld = entity.xo = previousPosition.x;
        entity.yOld = entity.yo = previousPosition.y;
        entity.zOld = entity.zo = previousPosition.z;
    }

    public static final class Arrival {
        private final ResourceKey<Level> dimension;
        private final UUID subLevelId;
        private final Vec3 localAnchor;
        private int remainingTicks = 160;

        private Arrival(final ResourceKey<Level> dimension, final UUID subLevelId, final Vec3 localAnchor) {
            this.dimension = dimension;
            this.subLevelId = subLevelId;
            this.localAnchor = localAnchor;
        }
    }
}
