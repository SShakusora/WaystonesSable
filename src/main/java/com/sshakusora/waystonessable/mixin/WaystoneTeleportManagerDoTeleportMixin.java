package com.sshakusora.waystonessable.mixin;

import com.mojang.datafixers.util.Either;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.TeleportDestination;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.core.WaystoneTeleportManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@SuppressWarnings("all")
@Mixin(WaystoneTeleportManager.class)
public class WaystoneTeleportManagerDoTeleportMixin {

    @Inject(method = "doTeleport(Lnet/blay09/mods/waystones/api/WaystoneTeleportContext;Lnet/blay09/mods/waystones/api/TeleportDestination;)Lcom/mojang/datafixers/util/Either;", at = @At("HEAD"))
    private static void waystonesSable$registerWarpPlateArrivalGuard(WaystoneTeleportContext context, TeleportDestination destination, CallbackInfoReturnable<Either<List<Entity>, ?>> cir) {
        if (!context.getTargetWaystone().getWaystoneType().equals(WaystoneTypes.WARP_PLATE)) {
            return;
        }

        BlockPos targetPos = context.getTargetWaystone().getPos();
        waystonesSable$registerEntityAndVehicle(context.getEntity(), destination, targetPos);
        for (Mob leashedEntity : context.getLeashedEntities()) {
            waystonesSable$registerEntityAndVehicle(leashedEntity, destination, targetPos);
        }
        for (Entity additionalEntity : context.getAdditionalEntities()) {
            waystonesSable$registerEntityAndVehicle(additionalEntity, destination, targetPos);
        }
    }

    private static void waystonesSable$registerEntityAndVehicle(Entity entity, TeleportDestination destination, BlockPos targetPos) {
        SableWaystoneCompat.registerWarpPlateArrivalGuard(entity, destination.level(), targetPos);

        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            SableWaystoneCompat.registerWarpPlateArrivalGuard(vehicle, destination.level(), targetPos);
        }
    }
}
