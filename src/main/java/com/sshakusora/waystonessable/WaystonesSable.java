package com.sshakusora.waystonessable;

import com.mojang.logging.LogUtils;
import net.blay09.mods.balm.Balmstrap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(WaystonesSable.MOD_ID)
public class WaystonesSable {
    public static final String MOD_ID = "waystonessable";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WaystonesSable(IEventBus modEventBus, ModContainer modContainer) {
        final WaystonesSableRuntime runtime = new WaystonesSableRuntime();
        modEventBus.addListener(runtime::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(runtime::onContainerReady);
        NeoForge.EVENT_BUS.addListener(runtime::onServerStopping);
        Balmstrap.onRuntimeAvailable(runtime::register);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
