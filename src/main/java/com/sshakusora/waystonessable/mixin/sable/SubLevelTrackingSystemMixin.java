package com.sshakusora.waystonessable.mixin.sable;

import com.sshakusora.waystonessable.teleport.SubLevelRemovalPayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelTrackingSystem.class, remap = false)
public abstract class SubLevelTrackingSystemMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "sendRemoval", at = @At("HEAD"), cancellable = true, require = 1)
    private void waystonessable$sendScopedRemoval(final VeilPacketManager.PacketSink sink,
                                                 final ServerSubLevel subLevel,
                                                 final CallbackInfo callback) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("Cannot send SubLevel removal without its source container");
        }
        final var origin = container.getOrigin();
        final ChunkPos plot = subLevel.getPlot().plotPos;
        // Both departed-player cleanup and onSubLevelRemoved use this common exit.
        // Keep Sable's tracking cleanup, but never send its dimensionless removal packet.
        sink.sendPacket(new SubLevelRemovalPayload(level.dimension().location(),
                ChunkPos.asLong(plot.x - origin.x, plot.z - origin.y), subLevel.getUniqueId()));
        callback.cancel();
    }
}
