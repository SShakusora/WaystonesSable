package com.sshakusora.waystonessable.network;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.client.WaystoneSubLevelClientCache;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@MethodsReturnNonnullByDefault
public record WaystoneSubLevelStatePayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<WaystoneSubLevelStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WaystonesSable.MOD_ID, "waystone_sublevel_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = new StreamCodec<>() {
        @Override
        public Entry decode(RegistryFriendlyByteBuf buf) {
            UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
            boolean sub = buf.readBoolean();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            ResourceKey<Level> dim = ResourceKey.streamCodec(Registries.DIMENSION).decode(buf);
            return new Entry(id, sub, new Vec3(x, y, z), dim);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, Entry entry) {
            UUIDUtil.STREAM_CODEC.encode(buf, entry.waystoneId());
            buf.writeBoolean(entry.isOnSubLevel());
            buf.writeDouble(entry.visiblePos().x);
            buf.writeDouble(entry.visiblePos().y);
            buf.writeDouble(entry.visiblePos().z);
            ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, entry.visibleDimension());
        }
    };
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
            WaystoneSubLevelClientCache.put(entry.waystoneId(), entry.isOnSubLevel(), entry.visiblePos(), entry.visibleDimension());
        }
    }

    public record Entry(UUID waystoneId, boolean isOnSubLevel, Vec3 visiblePos, ResourceKey<Level> visibleDimension) {
    }
}
