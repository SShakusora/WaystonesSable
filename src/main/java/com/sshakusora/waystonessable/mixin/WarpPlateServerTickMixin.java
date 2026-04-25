package com.sshakusora.waystonessable.mixin;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import net.blay09.mods.waystones.block.entity.WarpPlateBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

@Mixin(WarpPlateBlockEntity.class)
public abstract class WarpPlateServerTickMixin {
    @Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Entity> waystonesSable$findEntitiesOnWarpPlate(Level instance, Entity entity, AABB bounds, Predicate<? super Entity> predicate) {
        WarpPlateBlockEntity self = (WarpPlateBlockEntity) (Object) this;
        return SableWaystoneCompat.getEntitiesInsideBlock(self.getLevel(), self.getBlockPos());
    }
}
