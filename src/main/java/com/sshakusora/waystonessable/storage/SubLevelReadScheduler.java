package com.sshakusora.waystonessable.storage;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.mixin.sable.SubLevelHoldingChunkAccessor;
import com.sshakusora.waystonessable.mixin.sable.SubLevelHoldingChunkMapAccessor;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * All work and completions run on the server thread; Sable's caches are never shared with workers.
 */
public final class SubLevelReadScheduler {
    private static final Map<MinecraftServer, SubLevelReadScheduler> SERVERS = new IdentityHashMap<>();
    private static final long FAILURE_BACKOFF_NANOS = 5_000_000_000L;
    private static final int MAX_PENDING_CHUNKS = 16;
    private final TickWorkQueue queue = new TickWorkQueue(8, 2_000_000L);
    private final Map<ChunkKey, Warmup> pending = new HashMap<>();
    private final Map<ChunkKey, Failure> failures = new LinkedHashMap<>();
    private final Map<TargetChunkKey, Failure> targetFailures = new LinkedHashMap<>();

    private SubLevelReadScheduler() {
    }

    public static CompletableFuture<@Nullable SubLevelHoldingChunk> load(MinecraftServer server,
                                                                         SubLevelHoldingChunkMap map, ChunkPos pos) {
        return SERVERS.computeIfAbsent(server, ignored -> new SubLevelReadScheduler()).request(map, pos, null);
    }

    public static CompletableFuture<@Nullable SubLevelHoldingChunk> loadForTarget(MinecraftServer server,
                                                                                  SubLevelHoldingChunkMap map, ChunkPos pos, UUID id, int plotX, int plotZ) {
        return SERVERS.computeIfAbsent(server, ignored -> new SubLevelReadScheduler())
                .request(map, pos, new Target(id, plotX, plotZ));
    }

    public static void tick(MinecraftServer server) {
        final var scheduler = SERVERS.get(server);
        if (scheduler != null) scheduler.queue.tick();
    }

    public static void release(MinecraftServer server) {
        final var scheduler = SERVERS.remove(server);
        if (scheduler != null) {
            scheduler.queue.close();
            scheduler.pending.clear();
            scheduler.failures.clear();
            scheduler.targetFailures.clear();
        }
    }

    private CompletableFuture<@Nullable SubLevelHoldingChunk> request(SubLevelHoldingChunkMap map, ChunkPos pos,
                                                                      @Nullable Target target) {
        final var existing = ((SubLevelHoldingChunkMapAccessor) map).waystonessable$getLoadedHoldingChunks().get(pos.toLong());
        if (existing != null) return CompletableFuture.completedFuture(existing);
        final var key = new ChunkKey(map, pos);
        final var task = pending.get(key);
        if (task != null) {
            task.expect(target);
            return task.result;
        }
        final var failure = failures.get(key);
        if (failure != null && failure.revision == revision(map)
                && System.nanoTime() - failure.time < FAILURE_BACKOFF_NANOS)
            return CompletableFuture.completedFuture(null);
        if (pending.size() >= MAX_PENDING_CHUNKS) return CompletableFuture.completedFuture(null);
        final var targetFailure = target == null ? null : targetFailures.get(new TargetChunkKey(key, target));
        if (targetFailure != null && targetFailure.revision == revision(map)
                && System.nanoTime() - targetFailure.time < FAILURE_BACKOFF_NANOS)
            return CompletableFuture.completedFuture(null);
        final var warmup = new Warmup(key);
        warmup.expect(target);
        pending.put(key, warmup);
        queue.add(warmup);
        return warmup.result;
    }

    private static long revision(SubLevelHoldingChunkMap map) {
        return ((StorageRevision) map.getStorage()).waystonessable$getRevision();
    }

    private record ChunkKey(SubLevelHoldingChunkMap map, ChunkPos pos) {
    }

    private record Failure(long revision, long time) {
    }

    private record Target(UUID id, int plotX, int plotZ) {
    }

    private record TargetChunkKey(ChunkKey chunk, Target target) {
    }

    private final class Warmup implements TickWorkQueue.Work {
        private final ChunkKey key;
        private final CompletableFuture<@Nullable SubLevelHoldingChunk> result = new CompletableFuture<>();
        private @Nullable SubLevelHoldingChunk staged;
        private long observedRevision;
        private int nextPointer;
        private int restarts;
        private final Set<Target> targets = new HashSet<>();
        private boolean unrestricted;

        private Warmup(ChunkKey key) {
            this.key = key;
        }

        private void expect(@Nullable Target target) {
            if (target == null) unrestricted = true;
            else targets.add(target);
        }

        @Override
        public boolean step() {
            try {
                final var accessor = (SubLevelHoldingChunkMapAccessor) key.map;
                final var existing = accessor.waystonessable$getLoadedHoldingChunks().get(key.pos.toLong());
                if (existing != null) return finish(existing);
                if (staged != null && observedRevision != revision(key.map)) {
                    if (++restarts > 3) return finish(null);
                    staged = null;
                    nextPointer = 0;
                }
                if (staged == null) {
                    observedRevision = revision(key.map);
                    staged = key.map.getStorage().attemptLoadHoldingChunk(key.pos);
                    if (staged == null) return finish(null);
                    return false;
                }
                final var pointers = staged.getSubLevelPointers();
                if (nextPointer < pointers.size()) {
                    final var pointer = pointers.get(nextPointer++);
                    final var data = key.map.getStorage().attemptLoadSubLevel(key.pos, pointer);
                    // Never publish a partial chunk when a record is corrupt or vanished.
                    if (data == null) return finish(null);
                    if (((SubLevelHoldingChunkAccessor) staged)
                            .waystonessable$getLoadedHoldingSubLevels().containsKey(data.uuid())) return finish(null);
                    final var global = new GlobalSavedSubLevelPointer(
                            key.pos, pointer.storageIndex(), pointer.subLevelIndex());
                    staged.acceptHoldingSubLevel(new HoldingSubLevel(data, global));
                    return false;
                }
                // Publication is atomic with respect to server ticks and other Sable operations.
                final var stagedEntries = ((SubLevelHoldingChunkAccessor) staged).waystonessable$getLoadedHoldingSubLevels();
                boolean anyMatchingTarget = false;
                boolean conflictingPlot = false;
                for (final var target : targets) {
                    final var entry = stagedEntries.get(target.id());
                    final var targetKey = new TargetChunkKey(key, target);
                    if (entry != null && StoredSubLevelLookup.matches(entry.data(), target.id(), target.plotX(), target.plotZ())) {
                        anyMatchingTarget = true;
                        targetFailures.remove(targetKey);
                    } else {
                        conflictingPlot |= entry != null;
                        if (targetFailures.size() >= 256)
                            targetFailures.remove(targetFailures.keySet().iterator().next());
                        targetFailures.put(targetKey, new Failure(revision(key.map), System.nanoTime()));
                    }
                }
                if (conflictingPlot || !unrestricted && !anyMatchingTarget) {
                    // An incorrect hint must not publish a conflicting UUID and poison the valid fallback.
                    pending.remove(key, this);
                    staged = null;
                    result.complete(null);
                    return true;
                }
                final var held = accessor.waystonessable$getAllHoldingSubLevels();
                for (final var entry : staged.getLoadedHoldingSubLevels()) {
                    if (held.containsKey(entry.data().uuid())) return finish(null);
                }
                for (final var entry : staged.getLoadedHoldingSubLevels()) held.put(entry.data().uuid(), entry);
                accessor.waystonessable$getLoadedHoldingChunks().put(key.pos.toLong(), staged);
                return finish(staged);
            } catch (RuntimeException exception) {
                WaystonesSable.LOGGER.warn("Failed to prepare holding chunk {} for Waystone teleport", key.pos, exception);
                return finish(null);
            }
        }

        private boolean finish(@Nullable SubLevelHoldingChunk value) {
            pending.remove(key, this);
            if (value == null) {
                if (failures.size() >= 256) failures.remove(failures.keySet().iterator().next());
                failures.put(key, new Failure(revision(key.map), System.nanoTime()));
            } else failures.remove(key);
            staged = null;
            result.complete(value);
            return true;
        }

        @Override
        public void cancel() {
            pending.remove(key, this);
            staged = null;
            result.complete(null);
        }
    }
}
