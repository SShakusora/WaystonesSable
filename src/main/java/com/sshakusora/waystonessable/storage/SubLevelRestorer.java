package com.sshakusora.waystonessable.storage;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.association.WaystoneSubLevelBinding;
import com.sshakusora.waystonessable.mixin.sable.SubLevelHoldingChunkAccessor;
import com.sshakusora.waystonessable.mixin.sable.SubLevelHoldingChunkMapAccessor;
import com.sshakusora.waystonessable.spatial.SubLevelPlotLookup;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.blay09.mods.waystones.api.Waystone;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Restores a serialized SubLevel through Sable's dependency-aware holding map.
 */
public final class SubLevelRestorer {
    /**
     * Finds a serialized SubLevel whose plot owns the target Waystone, then lets Sable's
     * holding-chunk map perform the normal dependency-aware load.
     */
    @Nullable
    public RestoredSubLevel restore(final ServerSubLevelContainer container,
                                    final Waystone targetWaystone,
                                    @Nullable final WaystoneSubLevelBinding storedBinding) {
        final SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
        if (holdingChunkMap == null) {
            return null;
        }

        final SubLevelStorage storage = holdingChunkMap.getStorage();
        final int targetPlotX = SubLevelPlotLookup.relativePlotX(container, targetWaystone.getPos());
        final int targetPlotZ = SubLevelPlotLookup.relativePlotZ(container, targetWaystone.getPos());

        final WaystoneSubLevelBinding binding = storedBinding != null
                && storedBinding.dimension().equals(targetWaystone.getDimension().location().toString())
                && storedBinding.plotX() == targetPlotX
                && storedBinding.plotZ() == targetPlotZ
                ? storedBinding
                : null;
        if (binding != null) {
            final RestoredSubLevel held = loadFromMemory(
                    container,
                    holdingChunkMap,
                    binding.subLevelId(),
                    targetPlotX,
                    targetPlotZ
            );
            if (held != null && SubLevelPlotLookup.ownsWaystone(held.subLevel(), targetWaystone)) {
                return held;
            }

            final RestoredSubLevel loadResult = loadFromPointer(
                    container,
                    holdingChunkMap,
                    binding.pointer(),
                    binding.subLevelId(),
                    targetPlotX,
                    targetPlotZ
            );
            if (loadResult != null && SubLevelPlotLookup.ownsWaystone(loadResult.subLevel(), targetWaystone)) {
                return loadResult;
            }
        }

        return SubLevelRegionScanner.scan(storage, targetPlotX, targetPlotZ,
                binding != null ? binding.subLevelId() : null,
                (pointer, data) -> {
                    final RestoredSubLevel loaded = loadFromPointer(container, holdingChunkMap, pointer,
                            data.uuid(), targetPlotX, targetPlotZ);
                    return loaded != null && SubLevelPlotLookup.ownsWaystone(loaded.subLevel(), targetWaystone)
                            ? loaded : null;
                });
    }

    /**
     * Restores a holding sub-level that has not been written to disk yet.
     *
     * <p>Sable exposes the in-memory entry lookup and the final load operation, but its
     * pointer-based {@code snatchAndLoad} cannot address an entry whose pointer is null.
     * The holding chunk's own snatch operation is therefore used here so the target and
     * all of its dependencies are removed from the holding map before Sable loads them,
     * exactly as in the normal pointer-based path.</p>
     */
    @Nullable
    private static RestoredSubLevel loadFromMemory(final ServerSubLevelContainer container,
                                                   final SubLevelHoldingChunkMap holdingChunkMap,
                                                   final UUID subLevelId,
                                                   final int targetPlotX,
                                                   final int targetPlotZ) {
        if (StoredSubLevelLookup.findInMemory(holdingChunkMap, subLevelId, targetPlotX, targetPlotZ) == null) {
            return null;
        }

        try {
            final SubLevelHoldingChunkMapAccessor accessor = (SubLevelHoldingChunkMapAccessor) holdingChunkMap;
            for (final SubLevelHoldingChunk holdingChunk : accessor.waystonessable$getLoadedHoldingChunks().values()) {
                final Collection<HoldingSubLevel> snatched =
                        ((SubLevelHoldingChunkAccessor) holdingChunk).waystonessable$snatch(subLevelId);
                if (snatched == null) {
                    continue;
                }

                accessor.waystonessable$setDirty(holdingChunk.getChunkPos());
                for (final HoldingSubLevel holdingSubLevel : snatched) {
                    holdingChunkMap.loadHoldingSubLevel(holdingSubLevel);
                }

                if (container.getSubLevel(subLevelId) instanceof ServerSubLevel loaded) {
                    // The holding entry may still carry Sable's previous disk
                    // pointer. It is not a new serialized location.
                    return new RestoredSubLevel(loaded, null);
                }
            }
        } catch (final RuntimeException exception) {
            WaystonesSable.LOGGER.warn("Failed to restore in-memory SubLevel {} for Waystone teleport", subLevelId, exception);
        }

        return null;
    }

    @Nullable
    private static SubLevelRestorer.RestoredSubLevel loadFromPointer(final ServerSubLevelContainer container,
                                                                     final SubLevelHoldingChunkMap holdingChunkMap,
                                                                     @Nullable final GlobalSavedSubLevelPointer pointer,
                                                                     final UUID subLevelId,
                                                                     final int targetPlotX,
                                                                     final int targetPlotZ) {
        if (pointer == null) {
            return null;
        }

        final SubLevelStorage storage = holdingChunkMap.getStorage();
        if (StoredSubLevelLookup.findAtPointer(storage, pointer, subLevelId, targetPlotX, targetPlotZ) == null) {
            return null;
        }

        // Sable caches holding chunks. A valid disk pointer can therefore refer
        // to data that is not present in the already-loaded chunk. Avoid calling
        // snatchAndLoad in that case; it would emit a misleading ERROR and the
        // storage scan below can locate the current pointer instead.
        final SubLevelHoldingChunkMapAccessor mapAccessor = (SubLevelHoldingChunkMapAccessor) holdingChunkMap;
        final SubLevelHoldingChunk loadedChunk = mapAccessor.waystonessable$getLoadedHoldingChunks()
                .get(pointer.chunkPos().toLong());
        if (loadedChunk != null
                && !((SubLevelHoldingChunkAccessor) loadedChunk)
                .waystonessable$getLoadedHoldingSubLevels().containsKey(subLevelId)) {
            return null;
        }
        if (loadedChunk == null) {
            final SubLevelHoldingChunk diskChunk = storage.attemptLoadHoldingChunk(pointer.chunkPos());
            if (diskChunk == null || !diskChunk.getSubLevelPointers().contains(pointer.local())) {
                return null;
            }
        }

        holdingChunkMap.snatchAndLoad(pointer, subLevelId);
        if (container.getSubLevel(subLevelId) instanceof ServerSubLevel loaded) {
            return new RestoredSubLevel(loaded, pointer);
        }

        return null;
    }

    public record RestoredSubLevel(ServerSubLevel subLevel, @Nullable GlobalSavedSubLevelPointer pointer) {
    }
}
