package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import com.sshakusora.waystonessable.network.WaystoneSubLevelRemovalPayload;
import com.sshakusora.waystonessable.network.WaystoneSubLevelStatePayload;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("all")
@Mixin(WaystoneSyncManager.class)
public class WaystoneSyncManagerMixin {

    @Inject(method = "sendActivatedWaystones", at = @At("RETURN"))
    private static void waystonesSable$syncActivatedWaystoneStates(Player player, CallbackInfo ci) {
        waystonesSable$sendWaystoneStates(player, List.copyOf(PlayerWaystoneManager.getActivatedWaystones(player)));
    }

    @Inject(method = "sendWaystonesOfType", at = @At("RETURN"))
    private static void waystonesSable$syncWaystoneTypeStates(ResourceLocation waystoneType, ServerPlayer player, CallbackInfo ci) {
        List<Waystone> waystones = WaystoneManagerImpl.get(player.server).getWaystonesByType(waystoneType).collect(Collectors.toList());
        waystonesSable$sendWaystoneStates(player, waystones);
    }

    @Inject(method = "sendWaystoneUpdate", at = @At("RETURN"))
    private static void waystonesSable$syncWaystoneUpdate(Player player, Waystone waystone, CallbackInfo ci) {
        waystonesSable$sendWaystoneStates(player, List.of(waystone));
    }

    @Inject(method = "sendWaystoneRemoval", at = @At("RETURN"))
    private static void waystonesSable$syncWaystoneRemoval(Player player, Waystone waystone, boolean wasDestroyed, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundCustomPayloadPacket(new WaystoneSubLevelRemovalPayload(waystone.getWaystoneUid())));
        }
    }

    private static void waystonesSable$sendWaystoneStates(Player player, List<Waystone> waystones) {
        if (!(player instanceof ServerPlayer serverPlayer) || waystones.isEmpty()) {
            return;
        }

        List<WaystoneSubLevelStatePayload.Entry> entries = waystones.stream()
                .map(waystone -> new WaystoneSubLevelStatePayload.Entry(
                        waystone.getWaystoneUid(),
                        SableWaystoneCompat.isWaystoneOnSubLevel(serverPlayer.server, waystone)
                ))
                .toList();
        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(new WaystoneSubLevelStatePayload(entries)));
    }
}
