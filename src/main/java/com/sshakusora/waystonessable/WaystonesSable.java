package com.sshakusora.waystonessable;

import com.mojang.logging.LogUtils;
import com.sshakusora.waystonessable.command.ActivateAllWaystonesCommand;
import com.sshakusora.waystonessable.network.ModPayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(WaystonesSable.MOD_ID)
public class WaystonesSable {
    public static final String MOD_ID = "waystonessable";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WaystonesSable(IEventBus modEventBus, ModContainer modContainer) {
        ModPayloads.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ActivateAllWaystonesCommand.register(event.getDispatcher());
    }
}
