package com.sshakusora.waystonessable.mixin.client;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    @Inject(
            method = "handleForgetLevelChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getChunkSource()Lnet/minecraft/client/multiplayer/ClientChunkCache;"
            ),
            cancellable = true,
            require = 1
    )
    private void waystonesSable$ignoreOldPlotChunkDrop(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        ClientSubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) {
            return;
        }

        ChunkPos chunkPos = packet.pos();
        if (waystonesSable$isSableManagedPlotChunk(container, chunkPos)) {
            ci.cancel();
        }
    }

    private static boolean waystonesSable$isSableManagedPlotChunk(ClientSubLevelContainer container, ChunkPos chunkPos) {
        if (container.getPlot(chunkPos) != null) {
            return true;
        }

        if (!container.inBounds(chunkPos)) {
            return false;
        }

        int plotX = (chunkPos.x >> container.getLogPlotSize()) - container.getOrigin().x;
        int plotZ = (chunkPos.z >> container.getLogPlotSize()) - container.getOrigin().y;
        if (plotX < 0 || plotZ < 0) {
            return false;
        }

        int index = container.getIndex(plotX, plotZ);
        return container.getOccupancy().get(index);
    }
}
