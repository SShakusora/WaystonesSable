package com.sshakusora.waystonessable.mixin.client;

import com.sshakusora.waystonessable.client.ClientTeleportGuard;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundStopTrackingSubLevelPacket.class)
public class ClientboundStopTrackingSubLevelPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void waystonesSable$ignoreCrossDimensionCollision(PacketContext context, CallbackInfo ci) {
        if (ClientTeleportGuard.shouldIgnoreStop()) {
            ci.cancel();
            return;
        }

        ClientboundStopTrackingSubLevelPacket packet = (ClientboundStopTrackingSubLevelPacket) (Object) this;
        Level level = context.level();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        int chunkX = ChunkPos.getX(packet.plotCoordinate());
        int chunkZ = ChunkPos.getZ(packet.plotCoordinate());
        if (container.getSubLevel(chunkX, chunkZ) == null) {
            ci.cancel();
        }
    }
}
