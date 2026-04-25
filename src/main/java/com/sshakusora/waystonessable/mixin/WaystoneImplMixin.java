package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.core.WaystoneImpl;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaystoneImpl.class)
public abstract class WaystoneImplMixin {

    @Inject(method = "isValidInLevel", at = @At("HEAD"), cancellable = true)
    private void waystonesSable$isValidInLevel(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(SableWaystoneCompat.isWaystoneValidInLevel(level, (Waystone) this));
    }
}
