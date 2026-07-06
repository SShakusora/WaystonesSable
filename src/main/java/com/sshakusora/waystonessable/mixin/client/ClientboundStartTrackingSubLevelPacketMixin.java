package com.sshakusora.waystonessable.mixin.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundStartTrackingSubLevelPacket.class)
public class ClientboundStartTrackingSubLevelPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void waystonesSable$replaceExistingTrackedSubLevel(PacketContext context, CallbackInfo ci) {
        Level level = context.level();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        ClientboundStartTrackingSubLevelPacket packet = (ClientboundStartTrackingSubLevelPacket) (Object) this;
        int chunkX = ChunkPos.getX(packet.plotCoordinate());
        int chunkZ = ChunkPos.getZ(packet.plotCoordinate());
        if (container.getSubLevel(chunkX, chunkZ) != null) {
            container.removeSubLevel(chunkX, chunkZ, SubLevelRemovalReason.REMOVED);
        }
    }
}
