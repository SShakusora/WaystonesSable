package com.sshakusora.waystonessable.compat;

import com.sshakusora.waystonessable.WaystonesSable;
import net.blay09.mods.waystones.api.WaystoneGroup;
import net.blay09.mods.waystones.api.WaystoneGroups;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SableWaystoneGroups {

    public static final ResourceLocation SABLE_GROUP_ID = ResourceLocation.fromNamespaceAndPath(WaystonesSable.MOD_ID, "sable");

    private static final WaystoneGroup DEFAULT_SABLE_GROUP = new SableGroup(
            SABLE_GROUP_ID,
            Component.translatable("gui.waystonessable.waystone_group.sable"),
            WaystoneGroups.DIMENSION_ICON,
            0xFFFFFF,
            true,
            false,
            0
    );

    private SableWaystoneGroups() {
    }

    public static WaystoneGroup defaultGroup() {
        return DEFAULT_SABLE_GROUP;
    }

    private record SableGroup(
            ResourceLocation identifier,
            Component name,
            ResourceLocation icon,
            int color,
            boolean inbuilt,
            boolean hidden,
            int sortIndex
    ) implements WaystoneGroup {
    }
}
