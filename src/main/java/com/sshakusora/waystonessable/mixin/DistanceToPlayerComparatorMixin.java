package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.comparator.DistanceToPlayerComparator;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DistanceToPlayerComparator.class)
public abstract class DistanceToPlayerComparatorMixin {

    @Shadow
    @Final
    private Player player;

    @Inject(method = "compare(Lnet/blay09/mods/waystones/api/Waystone;Lnet/blay09/mods/waystones/api/Waystone;)I", at = @At("HEAD"), cancellable = true)
    private void waystonesSable$compareVisibleDistance(Waystone first, Waystone second, CallbackInfoReturnable<Integer> cir) {
        double distance1 = SableWaystoneCompat.getWaystoneDistanceSqr(this.player, first);
        double distance2 = SableWaystoneCompat.getWaystoneDistanceSqr(this.player, second);
        cir.setReturnValue(Double.compare(distance1, distance2));
    }
}
