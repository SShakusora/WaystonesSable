package com.sshakusora.waystonessable.association;

import com.sshakusora.waystonessable.WaystonesSable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import net.blay09.mods.balm.world.level.block.entity.BalmBlockEntityUtils;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.entity.WarpPortalBlockEntity;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Per-invocation relocation state; no server-global pending work or stale plot references.
 */
public final class WaystoneAssemblyMigration {
    private WaystoneAssemblyMigration() {
    }

    public static List<Move> capture(ServerLevel level, AssemblyTransform transform, Iterable<BlockPos> blocks) {
        final var manager = WaystoneManagerImpl.get(level.getServer());
        final Map<UUID, Move> moves = new LinkedHashMap<>();
        for (final var pos : blocks) {
            final var blockEntity = level.getBlockEntity(pos);
            final var id = WaystoneBlockIdentities.get(blockEntity);
            if (id == null) continue;
            final var waystone = manager.getWaystoneById(id).orElse(null);
            // Only move an existing identity at its actual location, never resurrect a missing one.
            if (waystone == null || !waystone.getDimension().equals(level.dimension())) continue;
            final var move = moves.computeIfAbsent(id, ignored -> new Move(waystone,
                    waystone.getPos().immutable(), new ArrayList<>()));
            move.blocks().add(new MovedBlock(pos.immutable(), transform.apply(pos), level.getBlockState(pos)));
        }
        return List.copyOf(moves.values());
    }

    public static void complete(AssemblyTransform transform, List<Move> moves) {
        final var destination = transform.getLevel();
        final var server = destination.getServer();
        final var manager = WaystoneManagerImpl.get(server);
        for (final var move : moves) {
            final var id = move.waystone().getWaystoneUid();
            final var anchor = move.blocks().stream().filter(it -> it.origin().equals(move.position())).findFirst();
            if (anchor.isEmpty()) {
                WaystonesSable.LOGGER.warn("Waystone relocation {} has no registry anchor among {} blocks", id, move.blocks().size());
                continue;
            }
            // Sable catches individual block failures. Commit only identities whose entire moved group landed.
            if (!move.blocks().stream().allMatch(it -> matches(destination, it, id))) {
                WaystonesSable.LOGGER.warn("Incomplete Waystone relocation for {} to {}; keeping its registry entry", id,
                        anchor.get().destination());
                for (final var block : move.blocks()) {
                    if (!matches(destination, block, id))
                        WaystonesSable.LOGGER.warn("Relocation mismatch at {}: state={}, identity={}",
                                block.destination(), destination.getBlockState(block.destination()),
                                WaystoneBlockIdentities.get(destination.getBlockEntity(block.destination())));
                }
                continue;
            }
            final var registered = manager.getWaystoneById(id).orElse(null);
            if (!(registered instanceof MutableWaystone mutable)) {
                WaystonesSable.LOGGER.warn("Waystone relocation {} registry entry is no longer mutable", id);
                continue;
            }
            if (!move.blocks().stream().allMatch(block -> WaystoneBlockIdentities.restore(
                    destination.getBlockEntity(block.destination()), id))) {
                WaystonesSable.LOGGER.warn("Could not resolve moved Waystone {} through its public loading API", id);
                continue;
            }
            mutable.setDimension(destination.dimension());
            mutable.setPos(anchor.get().destination());
            // Temporary portals must retain their transient status and their original Target/Age NBT.
            if (!(destination.getBlockEntity(anchor.get().destination()) instanceof WarpPortalBlockEntity)) {
                mutable.setTransient(false);
            }
            manager.updateWaystone(registered);
            for (final var block : move.blocks()) {
                final var entity = destination.getBlockEntity(block.destination());
                entity.setChanged();
                BalmBlockEntityUtils.sync(entity);
            }
            WaystoneSyncManager.sendWaystoneUpdateToAll(server, registered);
        }
    }

    private static boolean matches(ServerLevel level, MovedBlock block, UUID id) {
        final var entity = level.getBlockEntity(block.destination());
        return level.getBlockState(block.destination()).is(block.state().getBlock())
                && entity != null && !entity.isRemoved() && id.equals(WaystoneBlockIdentities.get(entity));
    }

    public record Move(Waystone waystone, BlockPos position, List<MovedBlock> blocks) {
    }

    public record MovedBlock(BlockPos origin, BlockPos destination, BlockState state) {
    }
}
