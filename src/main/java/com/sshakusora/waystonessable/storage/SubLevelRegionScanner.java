package com.sshakusora.waystonessable.storage;

import com.sshakusora.waystonessable.WaystonesSable;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans region files only as a fallback when a known address cannot restore the target.
 */
final class SubLevelRegionScanner {
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.slvlr");

    private SubLevelRegionScanner() {
    }

    @Nullable
    static <T> T scan(final SubLevelStorage storage, final int targetPlotX, final int targetPlotZ,
                      @Nullable final UUID expectedId,
                      final BiFunction<GlobalSavedSubLevelPointer, SubLevelData, T> candidateLoader) {
        final Path folder = storage.getFolder();
        if (!Files.isDirectory(folder)) {
            return null;
        }

        try (Stream<Path> paths = Files.list(folder)) {
            final List<Path> regionFiles = paths
                    .filter(path -> REGION_FILE.matcher(path.getFileName().toString()).matches())
                    .toList();

            for (final Path regionPath : regionFiles) {
                final Matcher matcher = REGION_FILE.matcher(regionPath.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }

                final int regionX = Integer.parseInt(matcher.group(1));
                final int regionZ = Integer.parseInt(matcher.group(2));
                final int minChunkX = regionX << 5;
                final int minChunkZ = regionZ << 5;

                for (int localX = 0; localX < 32; localX++) {
                    for (int localZ = 0; localZ < 32; localZ++) {
                        final ChunkPos holdingChunkPos = new ChunkPos(minChunkX + localX, minChunkZ + localZ);
                        final SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(holdingChunkPos);
                        if (holdingChunk == null) {
                            continue;
                        }

                        for (final SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
                            final SubLevelData data = storage.attemptLoadSubLevel(holdingChunkPos, pointer);
                            if (data == null
                                    || !StoredSubLevelLookup.matchesPlot(data, targetPlotX, targetPlotZ)
                                    || (expectedId != null && !expectedId.equals(data.uuid()))) {
                                continue;
                            }

                            final GlobalSavedSubLevelPointer globalPointer = new GlobalSavedSubLevelPointer(
                                    holdingChunkPos, pointer.storageIndex(), pointer.subLevelIndex());
                            final T loaded = candidateLoader.apply(globalPointer, data);
                            if (loaded != null) {
                                return loaded;
                            }
                        }
                    }
                }
            }
        } catch (final IOException | RuntimeException exception) {
            WaystonesSable.LOGGER.warn("Failed to locate serialized SubLevel for Waystone teleport", exception);
        }

        return null;
    }
}
