package com.sshakusora.waystonessable.selection;

import com.sshakusora.waystonessable.WaystonesSable;
import net.blay09.mods.waystones.Waystones;
import net.blay09.mods.waystones.api.WaystoneGroup;
import net.blay09.mods.waystones.api.event.CollectDefaultWaystoneGroupsEvent;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WaystoneGroupImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class SubLevelWaystoneGroup {
    public static final WaystoneGroup SABLE = new WaystoneGroupImpl(
            WaystonesSable.asResource("sable"),
            Component.translatable("waystones.groups.sable"),
            ResourceLocation.fromNamespaceAndPath(Waystones.MOD_ID, "groups/sable"),
            0xFFFFFF, true, false, 0);

    private SubLevelWaystoneGroup() {
    }

    public static void collectDefaults(final CollectDefaultWaystoneGroupsEvent event) {
        event.addGroup(SABLE);
    }

    public static void syncForMenu(final Player player) {
        PlayerWaystoneManager.ensureWaystoneGroups(player, List.of(SABLE));
        WaystoneSyncManager.sendWaystoneGroups(player);
    }
}
