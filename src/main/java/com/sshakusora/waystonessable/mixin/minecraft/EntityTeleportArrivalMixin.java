package com.sshakusora.waystonessable.mixin.minecraft;

import com.sshakusora.waystonessable.teleport.TeleportArrivalExtension;
import com.sshakusora.waystonessable.teleport.TeleportMotion;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityTeleportArrivalMixin implements TeleportArrivalExtension {
    @Unique
    private @Nullable TeleportMotion.Arrival waystonessable$arrival;

    @Override
    public @Nullable TeleportMotion.Arrival waystonessable$getArrival() {
        return waystonessable$arrival;
    }

    @Override
    public void waystonessable$setArrival(final @Nullable TeleportMotion.Arrival arrival) {
        waystonessable$arrival = arrival;
    }
}
