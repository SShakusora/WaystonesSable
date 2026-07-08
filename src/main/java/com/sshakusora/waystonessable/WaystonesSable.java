package com.sshakusora.waystonessable;

import com.mojang.logging.LogUtils;
import com.sshakusora.waystonessable.client.SableWaystoneClientHandler;
import com.sshakusora.waystonessable.command.ActivateAllWaystonesCommand;
import com.sshakusora.waystonessable.compat.SableWaystoneEventHandler;
import com.sshakusora.waystonessable.network.ModPayloads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(WaystonesSable.MOD_ID)
public class WaystonesSable {
    public static final String MOD_ID = "waystonessable";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WaystonesSable(IEventBus modEventBus, ModContainer modContainer) {
        ModPayloads.register(modEventBus);
        SableWaystoneEventHandler.register();
        registerCreateAttachedCheckIfAvailable();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SableWaystoneClientHandler.register(modEventBus);
        }

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private static void registerCreateAttachedCheckIfAvailable() {
        if (!ModList.get().isLoaded("create")) {
            LOGGER.debug("Create is not loaded; Waystone SubLevel assembly will use the Sable assembleBlocks fallback mixin.");
            return;
        }

        try {
            Class.forName("com.sshakusora.waystonessable.compat.create.CreateAttachedCheckCompat")
                    .getMethod("register")
                    .invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn(
                    "Create is loaded, but Waystone Create attached-check registration failed. "
                            + "If Waystone halves split during Create Aeronautics assembly, restart with "
                            + "-Dwaystonessable.forceSableAssemblyMixin=true to force the Sable fallback mixin.",
                    exception
            );
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ActivateAllWaystonesCommand.register(event.getDispatcher());
    }
}
