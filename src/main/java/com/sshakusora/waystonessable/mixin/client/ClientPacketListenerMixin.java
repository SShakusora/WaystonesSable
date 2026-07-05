package com.sshakusora.waystonessable.mixin.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"), cancellable = true)
    private void waystonesSable$ignoreOldPlotChunkDrop(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        ChunkPos chunkPos = packet.pos();
        if (container != null && container.inBounds(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }
}
