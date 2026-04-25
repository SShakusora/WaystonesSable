package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.core.WaystoneTeleportManager;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("all")
@Mixin(WaystoneTeleportManager.class)
public class WaystoneTeleportManagerMixin {

    @Unique
    private static final ThreadLocal<Vec3> waystonesSable$originalTargetPos = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Boolean> waystonesSable$dimensionalTeleport = new ThreadLocal<>();

    @Inject(method = "teleportEntity", at = @At("HEAD"))
    private static void waystonesSable$captureTargetPos(Entity entity, ServerLevel targetWorld, Vec3 targetPos3d, Direction direction, CallbackInfoReturnable<Entity> cir) {
        waystonesSable$originalTargetPos.set(targetPos3d);
        waystonesSable$dimensionalTeleport.set(targetWorld.dimensionType() != entity.level().dimensionType());
        if (targetWorld.dimensionType() != entity.level().dimensionType()) {
            SableWaystoneCompat.prepareCrossDimensionTeleport(entity, targetWorld, targetPos3d);
        }
    }

    @ModifyVariable(method = "teleportEntity", at = @At("HEAD"), argsOnly = true)
    private static Vec3 waystonesSable$adjustTargetPos(Vec3 targetPos3d, Entity entity, ServerLevel targetWorld) {
        return SableWaystoneCompat.getVisibleTeleportPos(targetWorld, targetPos3d);
    }

    @Inject(method = "teleportEntity", at = @At("RETURN"))
    private static void waystonesSable$syncTracking(Entity entity, ServerLevel targetWorld, Vec3 targetPos3d, Direction direction, CallbackInfoReturnable<Entity> cir) {
        Vec3 originalTargetPos = waystonesSable$originalTargetPos.get();
        boolean dimensionalTeleport = Boolean.TRUE.equals(waystonesSable$dimensionalTeleport.get());
        waystonesSable$originalTargetPos.remove();
        waystonesSable$dimensionalTeleport.remove();
        if (originalTargetPos != null && cir.getReturnValue() != null) {
            SableWaystoneCompat.syncTrackingAfterTeleport(cir.getReturnValue(), originalTargetPos, dimensionalTeleport);
        }
    }
}
