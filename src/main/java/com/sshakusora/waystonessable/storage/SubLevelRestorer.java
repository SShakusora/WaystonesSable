package com.sshakusora.waystonessable.storage;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.association.WaystoneBindingSavedData;
import com.sshakusora.waystonessable.association.WaystoneSubLevelBinding;
import com.sshakusora.waystonessable.association.WaystoneTargetIdentity;
import com.sshakusora.waystonessable.mixin.sable.SubLevelHoldingChunkAccessor;
import com.sshakusora.waystonessable.mixin.sable.SubLevelHoldingChunkMapAccessor;
import com.sshakusora.waystonessable.spatial.SubLevelPlotLookup;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.blay09.mods.waystones.api.Waystone;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Indexed restoration; cold holding chunks are prepared cooperatively before publication.
 */
public final class SubLevelRestorer {
    public CompletableFuture<@Nullable RestoredSubLevel> restore(ServerSubLevelContainer container,
                                                                 Waystone waystone, @Nullable WaystoneSubLevelBinding binding, BooleanSupplier stillValid) {
        final var map = container.getHoldingChunkMap();
        final var identity = WaystoneTargetIdentity.capture(waystone);
        final var index = WaystoneBindingSavedData.getOrLoad(container.getLevel().getServer());
        final int x = SubLevelPlotLookup.relativePlotX(container, waystone.getPos());
        final int z = SubLevelPlotLookup.relativePlotZ(container, waystone.getPos());
        if (!stillValid.getAsBoolean() || map == null || !identity.matchesBinding(container, binding)
                || !binding.equals(index.get(identity.waystoneId()))) return CompletableFuture.completedFuture(null);
        final var memory = loadFromMemory(container, map, binding.subLevelId(), x, z, identity, index);
        if (memory != null) return CompletableFuture.completedFuture(memory);
        // Capture both copies before waiting. External changes cancel this request; our verified repair happens last.
        final var metadata = index.metadata(binding.dimension(), binding.subLevelId());
        final BooleanSupplier current = () -> {
            final var now = index.get(identity.waystoneId());
            if (!stillValid.getAsBoolean() || !identity.unchanged(waystone) || !identity.matchesBinding(container, now)
                    || !binding.subLevelId().equals(now.subLevelId())) return false;
            if (binding.equals(now) && index.metadata(binding.dimension(), binding.subLevelId()) == metadata)
                return true;
            // A coalesced waiter may already have validated/repaired and activated this very target.
            final var active = SubLevelPlotLookup.findLoadedSubLevel(container, identity.position());
            return active != null && identity.matches(container, active, binding.subLevelId());
        };
        return tryCandidate(container, map, binding, identity, index, index.pointerCandidates(binding), 0, current);
    }

    private static CompletableFuture<@Nullable RestoredSubLevel> tryCandidate(ServerSubLevelContainer container,
                                                                              SubLevelHoldingChunkMap map, WaystoneSubLevelBinding binding, WaystoneTargetIdentity identity,
                                                                              WaystoneBindingSavedData index, List<GlobalSavedSubLevelPointer> candidates, int next, BooleanSupplier current) {
        if (!current.getAsBoolean()) return CompletableFuture.completedFuture(null);
        final var active = SubLevelPlotLookup.findLoadedSubLevel(container, identity.position());
        if (active != null)
            return CompletableFuture.completedFuture(verifiedActive(container, active, binding, identity, index));
        if (next >= candidates.size()) return CompletableFuture.completedFuture(null);
        return SubLevelReadScheduler.loadForTarget(container.getLevel().getServer(), map, candidates.get(next).chunkPos(),
                        binding.subLevelId(), binding.plotX(), binding.plotZ())
                .thenCompose(chunk -> {
                    if (!current.getAsBoolean()) return CompletableFuture.completedFuture(null);
                    final var loaded = SubLevelPlotLookup.findLoadedSubLevel(container, identity.position());
                    if (loaded != null)
                        return CompletableFuture.completedFuture(verifiedActive(container, loaded, binding, identity, index));
                    final var held = StoredSubLevelLookup.findInMemory(map, binding.subLevelId(), binding.plotX(), binding.plotZ());
                    if (chunk != null && held != null) {
                        // The actual UUID/plot and current owner validate an address; a hint alone never does.
                        return CompletableFuture.completedFuture(loadFromMemory(container, map, binding.subLevelId(),
                                binding.plotX(), binding.plotZ(), identity, index));
                    }
                    return tryCandidate(container, map, binding, identity, index, candidates, next + 1, current);
                });
    }

    private static @Nullable RestoredSubLevel verifiedActive(ServerSubLevelContainer container, ServerSubLevel active,
                                                             WaystoneSubLevelBinding binding, WaystoneTargetIdentity identity, WaystoneBindingSavedData index) {
        if (!identity.matches(container, active, binding.subLevelId())) return null;
        index.record(binding.dimension(), binding.subLevelId(), binding.plotX(), binding.plotZ(), active.logicalPose(),
                active.getLastSerializationPointer());
        return new RestoredSubLevel(active, active.getLastSerializationPointer());
    }

    private static @Nullable RestoredSubLevel loadFromMemory(ServerSubLevelContainer container,
                                                             SubLevelHoldingChunkMap map, UUID id, int x, int z, WaystoneTargetIdentity identity,
                                                             WaystoneBindingSavedData index) {
        if (StoredSubLevelLookup.findInMemory(map, id, x, z) == null) return null;
        try {
            final var held = map.getHoldingSubLevel(id);
            if (held == null) return null;
            final var owner = ((HoldingSubLevelOwner) (Object) held).waystonessable$getOwner();
            final var accessor = (SubLevelHoldingChunkMapAccessor) map;
            if (owner == null || accessor.waystonessable$getLoadedHoldingChunks().get(owner.getChunkPos().toLong()) != owner)
                return null;
            final var entries = ((SubLevelHoldingChunkAccessor) owner).waystonessable$getLoadedHoldingSubLevels();
            if (entries.get(id) != held) return null;
            // Validate the whole dependency group before mutating any holding state.
            for (final var dependency : held.data().dependencies()) {
                if (!entries.containsKey(dependency)) return null;
            }
            // Only data accepted into the current holding owner can repair the canonical metadata and all hints.
            index.record(identity.dimension().location().toString(), id, x, z, held.data().pose(), held.pointer());
            final var snatched = ((SubLevelHoldingChunkAccessor) owner).waystonessable$snatch(id);
            if (snatched == null) return null;
            accessor.waystonessable$setDirty(owner.getChunkPos());
            for (final var entry : snatched) map.loadHoldingSubLevel(entry);
            return container.getSubLevel(id) instanceof ServerSubLevel loaded && identity.matches(container, loaded, id)
                    ? new RestoredSubLevel(loaded, held.pointer()) : null;
        } catch (RuntimeException exception) {
            WaystonesSable.LOGGER.warn("Failed to restore holding SubLevel {} for Waystone teleport", id, exception);
            return null;
        }
    }

    public record RestoredSubLevel(ServerSubLevel subLevel, @Nullable GlobalSavedSubLevelPointer pointer) {
    }
}
