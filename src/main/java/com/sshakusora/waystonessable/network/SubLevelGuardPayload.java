package com.sshakusora.waystonessable.network;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.client.ClientTeleportGuard;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

@MethodsReturnNonnullByDefault
public record SubLevelGuardPayload(long protectedPlotCoordinate) implements CustomPacketPayload {

    public static final Type<SubLevelGuardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WaystonesSable.MOD_ID, "sublevel_guard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubLevelGuardPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            SubLevelGuardPayload::protectedPlotCoordinate,
            SubLevelGuardPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubLevelGuardPayload payload, Player player) {
        ClientTeleportGuard.protect(5000L);
    }
}
