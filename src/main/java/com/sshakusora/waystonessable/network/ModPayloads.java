package com.sshakusora.waystonessable.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPayloads {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, ModPayloads::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                SableTeleportPayload.TYPE,
                SableTeleportPayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        SableTeleportPayload.handle(payload, context.player());
                    });
                }
        );
        registrar.playToClient(
                SubLevelGuardPayload.TYPE,
                SubLevelGuardPayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        SubLevelGuardPayload.handle(payload, context.player());
                    });
                }
        );
        registrar.playToClient(
                WaystoneSubLevelStatePayload.TYPE,
                WaystoneSubLevelStatePayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> WaystoneSubLevelStatePayload.handle(payload, context.player()));
                }
        );
        registrar.playToClient(
                WaystoneSubLevelRemovalPayload.TYPE,
                WaystoneSubLevelRemovalPayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> WaystoneSubLevelRemovalPayload.handle(payload, context.player()));
                }
        );
    }
}
