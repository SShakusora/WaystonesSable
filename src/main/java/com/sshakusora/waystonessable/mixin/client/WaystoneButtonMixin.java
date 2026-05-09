package com.sshakusora.waystonessable.mixin.client;

import com.sshakusora.waystonessable.client.WaystoneSubLevelClientCache;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystoneVisibility;
import net.blay09.mods.waystones.client.gui.widget.WaystoneButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaystoneButton.class)
public abstract class WaystoneButtonMixin {

    @Shadow
    @Final
    private Waystone waystone;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void waystonesSable$updateWaystoneLabel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ((Button) (Object) this).setMessage(waystonesSable$getDisplayName());
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double waystonesSable$renderVisibleDistance(LocalPlayer player, Vec3 rawWaystonePos) {
        return SableWaystoneCompat.getWaystoneDistance(player, this.waystone);
    }

    @Unique
    private Component waystonesSable$getDisplayName() {
        Component name = waystonesSable$getBaseWaystoneName();
        if (!waystonesSable$isVisibleSubLevelWaystone()) {
            return name;
        }

        return name.copy().append(Component.translatable("gui.waystonessable.waystone.sublevel").withStyle(ChatFormatting.AQUA));
    }

    @Unique
    private Component waystonesSable$getBaseWaystoneName() {
        Component effectiveName = this.waystone.getName().copy();
        if (effectiveName.getString().isEmpty()) {
            effectiveName = Component.translatable("gui.waystones.waystone_selection.unnamed_waystone");
        }
        if (this.waystone.getVisibility() == WaystoneVisibility.GLOBAL && this.waystone.getWaystoneType().equals(WaystoneTypes.WAYSTONE)) {
            effectiveName = effectiveName.copy().withStyle(ChatFormatting.YELLOW);
        }
        return effectiveName;
    }

    @Unique
    private boolean waystonesSable$isVisibleSubLevelWaystone() {
        return WaystoneSubLevelClientCache.isOnSubLevel(this.waystone);
    }
}
