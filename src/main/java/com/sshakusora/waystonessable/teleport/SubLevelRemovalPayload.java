package com.sshakusora.waystonessable.teleport;

import com.sshakusora.waystonessable.WaystonesSable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Plot coordinates are local to a dimension and may be reused by another SubLevel.
 * A removal must identify the ship that stopped tracking, even after the player changes worlds.
 */
public record SubLevelRemovalPayload(ResourceLocation dimension, long plotCoordinate,
                                     UUID subLevelId) implements CustomPacketPayload {
    public static final Type<SubLevelRemovalPayload> TYPE = new Type<>(WaystonesSable.asResource("sublevel_removal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubLevelRemovalPayload> STREAM_CODEC =
            StreamCodec.ofMember(SubLevelRemovalPayload::write, SubLevelRemovalPayload::read);

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeLong(plotCoordinate);
        buffer.writeUUID(subLevelId);
    }

    private static SubLevelRemovalPayload read(final RegistryFriendlyByteBuf buffer) {
        return new SubLevelRemovalPayload(buffer.readResourceLocation(), buffer.readLong(), buffer.readUUID());
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, SubLevelRemovalPayload::handle);
    }

    static void handle(final SubLevelRemovalPayload payload, final IPayloadContext context) {
        final var level = context.player().level();
        if (!level.dimension().location().equals(payload.dimension)) {
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        final int plotX = ChunkPos.getX(payload.plotCoordinate);
        final int plotZ = ChunkPos.getZ(payload.plotCoordinate);
        final SubLevel subLevel = container.getSubLevel(plotX, plotZ);
        if (subLevel != null && subLevel.getUniqueId().equals(payload.subLevelId)) {
            container.removeSubLevel(plotX, plotZ, SubLevelRemovalReason.REMOVED);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
