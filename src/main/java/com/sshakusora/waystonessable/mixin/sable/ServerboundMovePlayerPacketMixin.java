package com.sshakusora.waystonessable.mixin.sable;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerboundMovePlayerPacket.class, priority = 900)
public abstract class ServerboundMovePlayerPacketMixin {
    @Dynamic("Added by Sable's ServerboundMovePlayerPacketMixin")
    @Inject(method = "sable$handle", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void waystonessable$ignoreUnconfirmedMovement(final ServerPlayer player, final CallbackInfo callback) {
        // Sable handles plot coordinates before vanilla rejects movement during a teleport.
        // An in-flight source packet must not restore the departed ship as the player's tracker.
        if (player.connection.awaitingPositionFromClient != null) {
            callback.cancel();
        }
    }
}
