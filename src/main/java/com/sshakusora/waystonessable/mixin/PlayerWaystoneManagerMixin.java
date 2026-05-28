package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.Optional;

@Mixin(PlayerWaystoneManager.class)
public abstract class PlayerWaystoneManagerMixin {

    @Inject(method = "getNearestWaystone", at = @At("HEAD"), cancellable = true)
    private static void waystonesSable$getNearestWaystone(Player player, CallbackInfoReturnable<Optional<Waystone>> cir) {
        var waystones = PlayerWaystoneManager.getPlayerWaystoneData(player.level()).getWaystones(player);
        Optional<Waystone> nearest = waystones.stream()
                .filter(waystone -> waystone.getDimension() == player.level().dimension())
                .min(Comparator.comparingDouble(waystone -> SableWaystoneCompat.getWaystoneDistanceSqr(player, waystone)));
        cir.setReturnValue(nearest);
    }
}
