package com.sshakusora.waystonessable.network;

import com.sshakusora.waystonessable.WaystonesSable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("all")
@MethodsReturnNonnullByDefault
public record SableTeleportPayload(Optional<UUID> subLevelId, double anchorX, double anchorY, double anchorZ) implements CustomPacketPayload {

    public static final Type<SableTeleportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WaystonesSable.MOD_ID, "sable_teleport"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SableTeleportPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
            SableTeleportPayload::subLevelId,
            ByteBufCodecs.DOUBLE,
            SableTeleportPayload::anchorX,
            ByteBufCodecs.DOUBLE,
            SableTeleportPayload::anchorY,
            ByteBufCodecs.DOUBLE,
            SableTeleportPayload::anchorZ,
            SableTeleportPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SableTeleportPayload payload, Player player) {
        if (player == null) return;
        EntityMovementExtension sablePlayer = (EntityMovementExtension) player;
        EntityStickExtension stickPlayer = (EntityStickExtension) player;
        player.moveTo(payload.anchorX(), payload.anchorY(), payload.anchorZ());

        if (payload.subLevelId().isPresent()) {
            SubLevelContainer container = SubLevelContainer.getContainer(player.level());
            if (container != null) {
                SubLevel subLevel = container.getSubLevel(payload.subLevelId().get());
                if (subLevel != null) {
                    sablePlayer.sable$setTrackingSubLevel(subLevel);
                    EntitySubLevelUtil.setOldPosNoMovement(player);
                    return;
                }
            }
        } else {
            sablePlayer.sable$setTrackingSubLevel(null);
        }

        stickPlayer.sable$setPlotPosition(null);
        EntitySubLevelUtil.setOldPosNoMovement(player);
    }
}
