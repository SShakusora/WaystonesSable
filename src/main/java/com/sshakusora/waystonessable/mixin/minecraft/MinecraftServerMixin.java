package com.sshakusora.waystonessable.mixin.minecraft;

import com.sshakusora.waystonessable.association.WaystoneBindingSavedData;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftServer.class, remap = false)
public abstract class MinecraftServerMixin {
    @Inject(method = "saveAllChunks(ZZZ)Z", at = @At("RETURN"))
    private void waystonessable$flushAssociationIndex(final boolean suppressLog,
                                                      final boolean flush,
                                                      final boolean skipSave,
                                                      final CallbackInfoReturnable<Boolean> callback) {
        WaystoneBindingSavedData.flush((MinecraftServer) (Object) this, flush);
    }
}
