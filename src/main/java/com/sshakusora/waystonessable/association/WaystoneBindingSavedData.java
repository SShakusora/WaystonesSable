package com.sshakusora.waystonessable.association;

import com.mojang.serialization.DataResult;
import com.sshakusora.waystonessable.WaystonesSable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.blay09.mods.waystones.Waystones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.IOUtilities;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Persistent relationship between a Waystone and the Sable SubLevel that owns it.
 *
 * <p>Waystones stores the parent-level/storage position, while Sable may unload
 * the SubLevel that owns that position. Keeping the SubLevel UUID and last known
 * storage pointer lets the teleport bridge restore the right object without
 * relying on the current in-memory plot map.</p>
 */
public final class WaystoneBindingSavedData extends SavedData {
    private static final String FILE_ID = WaystonesSable.MOD_ID + "_sublevel_waystones";
    private static final String ENTRIES_TAG = Waystones.MOD_ID;

    private final Map<UUID, WaystoneSubLevelBinding> entries = new HashMap<>();
    private final Map<SubLevelKey, Set<UUID>> bySubLevel = new HashMap<>();
    private final Map<SubLevelKey, SubLevelMetadata> metadata = new HashMap<>();

    WaystoneBindingSavedData() {
    }

    public static WaystoneBindingSavedData getOrLoad(final MinecraftServer server) {
        final ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld is not available while loading the Waystones/Sable index");
        }

        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(WaystoneBindingSavedData::new, (tag, provider) -> load(tag), null),
                FILE_ID
        );
    }

    static WaystoneBindingSavedData load(final CompoundTag tag) {
        final WaystoneBindingSavedData index = new WaystoneBindingSavedData();
        final CompoundTag entriesTag = tag.getCompound(ENTRIES_TAG);

        for (final String key : entriesTag.getAllKeys()) {
            try {
                final UUID waystoneId = UUID.fromString(key);
                final CompoundTag entryTag = entriesTag.getCompound(key);
                final UUID subLevelId = entryTag.getUUID("sub_level");
                final String dimension = entryTag.getString("dimension");
                final int plotX = entryTag.getInt("plot_x");
                final int plotZ = entryTag.getInt("plot_z");
                final BlockPos localPos = new BlockPos(
                        entryTag.getInt("local_x"),
                        entryTag.getInt("local_y"),
                        entryTag.getInt("local_z")
                );
                final GlobalSavedSubLevelPointer pointer = readPointer(entryTag);

                index.put(waystoneId, new WaystoneSubLevelBinding(dimension, subLevelId, plotX, plotZ, localPos, pointer));
            } catch (final IllegalArgumentException exception) {
                WaystonesSable.LOGGER.warn("Ignoring malformed Waystones/Sable association {}", key, exception);
            }
        }

        final var dimensions = tag.getCompound("sublevels");
        for (final String dimension : dimensions.getAllKeys()) {
            final var levels = dimensions.getCompound(dimension);
            for (final String id : levels.getAllKeys()) {
                try {
                    final var data = levels.getCompound(id);
                    index.metadata.put(new SubLevelKey(dimension, UUID.fromString(id)), new SubLevelMetadata(
                            data.getInt("plot_x"), data.getInt("plot_z"),
                            SableNBTUtils.readPose3d(data.getCompound("pose")), readPointer(data)));
                } catch (IllegalArgumentException exception) {
                    WaystonesSable.LOGGER.warn("Ignoring malformed SubLevel metadata {}", id, exception);
                }
            }
        }
        index.setDirty(false);
        return index;
    }

    private static @Nullable GlobalSavedSubLevelPointer readPointer(final CompoundTag entryTag) {
        if (!entryTag.contains("pointer")) {
            return null;
        }

        final DataResult<GlobalSavedSubLevelPointer> result = GlobalSavedSubLevelPointer.CODEC.parse(
                NbtOps.INSTANCE,
                entryTag.get("pointer")
        );
        return result.resultOrPartial(error -> WaystonesSable.LOGGER.warn(
                "Ignoring malformed SubLevel pointer in Waystones/Sable association: {}", error
        )).orElse(null);
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider) {
        final CompoundTag entriesTag = new CompoundTag();

        for (final Map.Entry<UUID, WaystoneSubLevelBinding> mapEntry : entries.entrySet()) {
            final WaystoneSubLevelBinding entry = mapEntry.getValue();
            final CompoundTag entryTag = new CompoundTag();
            entryTag.putString("dimension", entry.dimension());
            entryTag.putUUID("sub_level", entry.subLevelId());
            entryTag.putInt("plot_x", entry.plotX());
            entryTag.putInt("plot_z", entry.plotZ());
            entryTag.putInt("local_x", entry.localPos().getX());
            entryTag.putInt("local_y", entry.localPos().getY());
            entryTag.putInt("local_z", entry.localPos().getZ());

            if (entry.pointer() != null) {
                entryTag.put("pointer", GlobalSavedSubLevelPointer.CODEC.encodeStart(
                        NbtOps.INSTANCE,
                        entry.pointer()
                ).getOrThrow());
            }

            entriesTag.put(mapEntry.getKey().toString(), entryTag);
        }

        tag.put(ENTRIES_TAG, entriesTag);
        final var dimensions = new CompoundTag();
        metadata.forEach((key, value) -> {
            final var levels = dimensions.getCompound(key.dimension());
            final var data = new CompoundTag();
            data.putInt("plot_x", value.plotX());
            data.putInt("plot_z", value.plotZ());
            data.put("pose", SableNBTUtils.writePose3d(value.pose()));
            if (value.pointer() != null) {
                data.put("pointer", GlobalSavedSubLevelPointer.CODEC.encodeStart(NbtOps.INSTANCE, value.pointer()).getOrThrow());
            }
            levels.put(key.id().toString(), data);
            dimensions.put(key.dimension(), levels);
        });
        tag.put("sublevels", dimensions);
        return tag;
    }

    @Nullable
    public WaystoneSubLevelBinding get(final UUID waystoneId) {
        return entries.get(waystoneId);
    }

    void remove(final UUID waystoneId) {
        final var previous = entries.remove(waystoneId);
        if (previous != null) {
            unindex(waystoneId, previous);
            setDirty();
        }
    }

    void put(final UUID waystoneId, final WaystoneSubLevelBinding entry) {
        final WaystoneSubLevelBinding previous = entries.put(waystoneId, entry);
        if (!entry.equals(previous)) {
            if (previous != null) unindex(waystoneId, previous);
            bySubLevel.computeIfAbsent(new SubLevelKey(entry.dimension(), entry.subLevelId()), key -> new HashSet<>())
                    .add(waystoneId);
            setDirty();
        }
    }

    private void unindex(UUID id, WaystoneSubLevelBinding entry) {
        final var key = new SubLevelKey(entry.dimension(), entry.subLevelId());
        final var ids = bySubLevel.get(key);
        if (ids != null && ids.remove(id) && ids.isEmpty()) bySubLevel.remove(key);
    }

    boolean updatePointer(final String dimension, final UUID subLevelId, final GlobalSavedSubLevelPointer pointer) {
        boolean changed = false;
        for (final UUID waystoneId : bySubLevel.getOrDefault(new SubLevelKey(dimension, subLevelId), Set.of())) {
            final WaystoneSubLevelBinding entry = entries.get(waystoneId);
            if (Objects.equals(pointer, entry.pointer())) {
                continue;
            }

            entries.put(waystoneId, new WaystoneSubLevelBinding(
                    entry.dimension(),
                    entry.subLevelId(),
                    entry.plotX(),
                    entry.plotZ(),
                    entry.localPos(),
                    pointer
            ));
            changed = true;
        }

        if (changed) {
            setDirty();
        }
        return changed;
    }

    public @Nullable SubLevelMetadata metadata(String dimension, UUID id) {
        return metadata.get(new SubLevelKey(dimension, id));
    }

    public void record(String dimension, UUID id, int plotX, int plotZ, Pose3dc pose,
                       @Nullable GlobalSavedSubLevelPointer pointer) {
        final var key = new SubLevelKey(dimension, id);
        final var previous = metadata.get(key);
        // null means "no new storage address", not deletion/invalidation of a known address.
        final var effectivePointer = pointer != null ? pointer : previous != null ? previous.pointer() : null;
        if (previous == null || !previous.matches(plotX, plotZ, pose, effectivePointer)) {
            metadata.put(key, new SubLevelMetadata(plotX, plotZ, pose, effectivePointer));
            setDirty();
        }
        // An unknown update cannot validate an old canonical address or discard a different recovery hint.
        if (pointer != null) updatePointer(dimension, id, pointer);
    }

    /**
     * Metadata is canonical; per-Waystone pointers are recovery hints until validated during restore.
     */
    public List<GlobalSavedSubLevelPointer> pointerCandidates(WaystoneSubLevelBinding binding) {
        final var candidates = new LinkedHashSet<GlobalSavedSubLevelPointer>();
        final var snapshot = metadata(binding.dimension(), binding.subLevelId());
        if (snapshot != null && snapshot.matchesPlot(binding.plotX(), binding.plotZ()) && snapshot.pointer() != null)
            candidates.add(snapshot.pointer());
        if (binding.pointer() != null) candidates.add(binding.pointer());
        return List.copyOf(candidates);
    }

    public @Nullable GlobalSavedSubLevelPointer knownPointer(String dimension, UUID id, int plotX, int plotZ) {
        final var snapshot = metadata(dimension, id);
        return snapshot != null && snapshot.matchesPlot(plotX, plotZ) ? snapshot.pointer() : null;
    }

    public void removeSubLevel(String dimension, UUID id) {
        final var key = new SubLevelKey(dimension, id);
        final var waystones = bySubLevel.remove(key);
        if (waystones != null) waystones.forEach(entries::remove);
        if (metadata.remove(key) != null || waystones != null) setDirty();
    }

    public static void recordSerialized(ServerLevel level, SubLevelData data,
                                        @Nullable GlobalSavedSubLevelPointer pointer) {
        final var plot = data.fullTag().getCompound("plot");
        getOrLoad(level.getServer()).record(level.dimension().location().toString(), data.uuid(),
                plot.getInt("plot_x"), plot.getInt("plot_z"), data.pose(), pointer);
    }

    public static void flush(final MinecraftServer server, final boolean waitForIo) {
        final ServerLevel overworld = server.overworld();
        if (overworld != null) {
            WaystoneBindingSavedData.getOrLoad(server);
            overworld.getChunkSource().getDataStorage().save();
            if (waitForIo) {
                IOUtilities.waitUntilIOWorkerComplete();
            }
        }
    }
}
