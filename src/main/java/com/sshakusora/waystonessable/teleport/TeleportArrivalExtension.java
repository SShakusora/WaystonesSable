package com.sshakusora.waystonessable.teleport;

import org.jetbrains.annotations.Nullable;

/**
 * Arrival state belongs to an entity instance, never to its network id.
 */
public interface TeleportArrivalExtension {
    @Nullable TeleportMotion.Arrival waystonessable$getArrival();

    void waystonessable$setArrival(@Nullable TeleportMotion.Arrival arrival);
}
