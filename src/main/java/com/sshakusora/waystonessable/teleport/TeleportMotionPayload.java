package com.sshakusora.waystonessable.teleport;

import com.sshakusora.waystonessable.WaystonesSable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Sent after the vanilla teleport packet; ordinary position corrections do not reset Sable motion.
 */
public record TeleportMotionPayload(ResourceLocation dimension, int entityId, UUID entityUuid,
                                    Vec3 worldPosition, @Nullable UUID subLevelId,
                                    Vec3 localAnchor) implements CustomPacketPayload {
    public static final Type<TeleportMotionPayload> TYPE = new Type<>(WaystonesSable.asResource("teleport_motion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportMotionPayload> STREAM_CODEC =
            StreamCodec.ofMember(TeleportMotionPayload::write, TeleportMotionPayload::read);

    public static TeleportMotionPayload forEntity(final Entity entity, final @Nullable UUID subLevelId,
                                                  final Vec3 localAnchor) {
        return new TeleportMotionPayload(entity.level().dimension().location(), entity.getId(), entity.getUUID(),
                entity.position(), subLevelId, localAnchor);
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeVarInt(entityId);
        buffer.writeUUID(entityUuid);
        buffer.writeVec3(worldPosition);
        buffer.writeBoolean(subLevelId != null);
        if (subLevelId != null) {
            buffer.writeUUID(subLevelId);
            buffer.writeVec3(localAnchor);
        }
    }

    private static TeleportMotionPayload read(final RegistryFriendlyByteBuf buffer) {
        final ResourceLocation dimension = buffer.readResourceLocation();
        final int entityId = buffer.readVarInt();
        final UUID entityUuid = buffer.readUUID();
        final Vec3 worldPosition = buffer.readVec3();
        final boolean hasSubLevel = buffer.readBoolean();
        return new TeleportMotionPayload(dimension, entityId, entityUuid, worldPosition,
                hasSubLevel ? buffer.readUUID() : null, hasSubLevel ? buffer.readVec3() : Vec3.ZERO);
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, TeleportMotionPayload::handle);
    }

    static void handle(final TeleportMotionPayload payload, final IPayloadContext context) {
        final var level = context.player().level();
        if (!level.dimension().location().equals(payload.dimension)) {
            return;
        }
        final Entity entity = level.getEntity(payload.entityId);
        if (entity != null && entity.getUUID().equals(payload.entityUuid)) {
            TeleportMotion.arrive(entity, payload.subLevelId, payload.localAnchor);
            if (!entity.isControlledByLocalInstance()) {
                // Entity tracker teleport packets arrive on a later tick for remote entities.
                // Discard an in-progress lerp to the source instead of letting it undo the reset.
                entity.moveTo(payload.worldPosition);
                entity.lerpTo(payload.worldPosition.x, payload.worldPosition.y, payload.worldPosition.z,
                        entity.getYRot(), entity.getXRot(), 0);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
