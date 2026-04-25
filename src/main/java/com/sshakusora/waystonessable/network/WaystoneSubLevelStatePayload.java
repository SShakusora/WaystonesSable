package com.sshakusora.waystonessable.network;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.client.WaystoneSubLevelClientCache;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@MethodsReturnNonnullByDefault
public record WaystoneSubLevelStatePayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<WaystoneSubLevelStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WaystonesSable.MOD_ID, "waystone_sublevel_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            Entry::waystoneId,
            ByteBufCodecs.BOOL,
            Entry::isOnSubLevel,
            Entry::new
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, WaystoneSubLevelStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ENTRY_CODEC),
            WaystoneSubLevelStatePayload::entries,
            WaystoneSubLevelStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WaystoneSubLevelStatePayload payload, Player player) {
        for (Entry entry : payload.entries()) {
            WaystoneSubLevelClientCache.put(entry.waystoneId(), entry.isOnSubLevel());
        }
    }

    public record Entry(UUID waystoneId, boolean isOnSubLevel) {
    }
}
