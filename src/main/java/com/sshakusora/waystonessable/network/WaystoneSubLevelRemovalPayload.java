package com.sshakusora.waystonessable.network;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.client.WaystoneSubLevelClientCache;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

@MethodsReturnNonnullByDefault
public record WaystoneSubLevelRemovalPayload(UUID waystoneId) implements CustomPacketPayload {

    public static final Type<WaystoneSubLevelRemovalPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WaystonesSable.MOD_ID, "waystone_sublevel_removal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaystoneSubLevelRemovalPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            WaystoneSubLevelRemovalPayload::waystoneId,
            WaystoneSubLevelRemovalPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WaystoneSubLevelRemovalPayload payload, Player player) {
        WaystoneSubLevelClientCache.remove(payload.waystoneId());
    }
}
