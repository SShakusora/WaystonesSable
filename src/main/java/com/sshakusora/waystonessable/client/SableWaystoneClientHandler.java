package com.sshakusora.waystonessable.client;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class SableWaystoneClientHandler {

    private SableWaystoneClientHandler() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SableWaystoneClientHandler::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        SableWaystoneCompat.setClientWaystoneSubLevelProvider(SableWaystoneClientHandler::isWaystoneOnSubLevel);
    }

    private static boolean isWaystoneOnSubLevel(Waystone waystone) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null
                && level.dimension() == waystone.getDimension()
                && SableWaystoneCompat.isWaystoneOnSubLevel(level, waystone.getPos());
    }
}
