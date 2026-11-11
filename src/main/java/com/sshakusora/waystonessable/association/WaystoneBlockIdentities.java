package com.sshakusora.waystonessable.association;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.entity.WarpPortalBlockEntity;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Reads packed identities without causing initialization or changing the registry.
 */
public final class WaystoneBlockIdentities {
    private WaystoneBlockIdentities() {
    }

    public static @Nullable UUID get(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof WaystoneBlockEntityBase waystoneEntity) {
            final var waystone = waystoneEntity.getWaystone();
            if (waystone.isValid()) return waystone.getWaystoneUid();
        } else if (!(blockEntity instanceof WarpPortalBlockEntity)) {
            return null;
        }
        // Before Waystones' onLoad, getWaystone() may still be invalid. Its normal save
        // format exposes the packed UUID without initializing a new identity or inventory.
        final var level = blockEntity.getLevel();
        if (level == null) return null;
        final var tag = blockEntity.saveWithoutMetadata(level.registryAccess());
        return tag.hasUUID("UUID") ? tag.getUUID("UUID") : null;
    }

    /**
     * Resolve an existing packed identity through Waystones' public loading methods.
     */
    public static boolean restore(BlockEntity blockEntity, UUID id) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level) || !id.equals(get(blockEntity))
                || WaystoneManagerImpl.get(level.getServer()).getWaystoneById(id).isEmpty()) return false;
        final Waystone waystone;
        if (blockEntity instanceof WaystoneBlockEntityBase waystoneEntity) {
            // The UUID and registry entry were verified, so onLoad resolves the saved
            // proxy instead of initializeWaystone(), which would reset a Warp Plate's shard.
            // Keep that proxy: initializeFromExisting attaches a concrete WaystoneImpl,
            // whose later onLoad can overwrite the canonical position of a two-block stone.
            waystoneEntity.onLoad();
            waystone = waystoneEntity.getWaystone();
        } else if (blockEntity instanceof WarpPortalBlockEntity portal) {
            waystone = portal.getWaystone();
        } else {
            return false;
        }
        return waystone.isValid() && id.equals(waystone.getWaystoneUid());
    }
}
