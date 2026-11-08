package com.sshakusora.waystonessable.association;

import com.mojang.serialization.DataResult;
import com.sshakusora.waystonessable.WaystonesSable;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    private WaystoneBindingSavedData() {
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

    private static WaystoneBindingSavedData load(final CompoundTag tag) {
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

                index.entries.put(waystoneId, new WaystoneSubLevelBinding(dimension, subLevelId, plotX, plotZ, localPos, pointer));
            } catch (final IllegalArgumentException exception) {
                WaystonesSable.LOGGER.warn("Ignoring malformed Waystones/Sable association {}", key, exception);
            }
        }

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
        return tag;
    }

    @Nullable
    public WaystoneSubLevelBinding get(final UUID waystoneId) {
        return entries.get(waystoneId);
    }

    void remove(final UUID waystoneId) {
        if (entries.remove(waystoneId) != null) {
            setDirty();
        }
    }

    void put(final UUID waystoneId, final WaystoneSubLevelBinding entry) {
        final WaystoneSubLevelBinding previous = entries.put(waystoneId, entry);
        if (!entry.equals(previous)) {
            setDirty();
        }
    }

    boolean updatePointer(final UUID subLevelId, final GlobalSavedSubLevelPointer pointer) {
        boolean changed = false;
        for (final Map.Entry<UUID, WaystoneSubLevelBinding> mapEntry : entries.entrySet()) {
            final WaystoneSubLevelBinding entry = mapEntry.getValue();
            if (!subLevelId.equals(entry.subLevelId()) || Objects.equals(pointer, entry.pointer())) {
                continue;
            }

            mapEntry.setValue(new WaystoneSubLevelBinding(
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

    public static void updateSubLevelPointer(final ServerLevel level,
                                             final UUID subLevelId,
                                             final GlobalSavedSubLevelPointer pointer) {
        if (level.getServer() != null) {
            WaystoneBindingSavedData.getOrLoad(level.getServer()).updatePointer(subLevelId, pointer);
        }
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
