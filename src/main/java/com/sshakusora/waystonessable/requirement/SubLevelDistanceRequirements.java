package com.sshakusora.waystonessable.requirement;

import com.sshakusora.waystonessable.WaystonesSable;
import com.sshakusora.waystonessable.spatial.WaystoneWorldPositionResolver;

import dev.ryanhcode.sable.Sable;
import net.blay09.mods.waystones.Waystones;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.requirement.ConditionResolver;
import net.blay09.mods.waystones.api.requirement.VariableResolver;
import net.blay09.mods.waystones.requirement.RequirementRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;

public final class SubLevelDistanceRequirements {
    private SubLevelDistanceRequirements() {
    }

    public static void register() {
        WaystonesAPI.registerVariableResolver(new VariableResolver() {
            @Override
            public ResourceLocation getId() {
                return id("distance");
            }

            @Override
            public float resolve(final WaystoneTeleportContext context) {
                if (!context.getTargetWaystone().isValid()) {
                    return 0f;
                }
                return distance(context);
            }
        });
        WaystonesAPI.registerConditionPredicate(withinDistance("is_within_distance", false));
        WaystonesAPI.registerConditionPredicate(withinDistance("is_not_within_distance", true));
        WaystonesSable.LOGGER.debug("Registered SubLevel-aware Waystones distance resolvers");
    }

    private static ConditionResolver<RequirementRegistry.FloatParameter> withinDistance(final String name, final boolean negate) {
        return new ConditionResolver<>() {
            @Override
            public ResourceLocation getId() {
                return id(name);
            }

            @Override
            public Class<RequirementRegistry.FloatParameter> getParameterType() {
                return RequirementRegistry.FloatParameter.class;
            }

            @Override
            public boolean matches(final WaystoneTeleportContext context, final RequirementRegistry.FloatParameter parameters) {
                if (!context.getTargetWaystone().isValid()) {
                    return false;
                }
                return (distance(context) <= parameters.value()) != negate;
            }
        };
    }

    private static float distance(final WaystoneTeleportContext context) {
        final Entity entity = context.getEntity();
        return (float) Math.sqrt(entity.distanceToSqr(worldCenter(entity, context.getTargetWaystone())));
    }

    private static Vec3 worldCenter(final Entity entity, final Waystone waystone) {
        final Vec3 center = waystone.getPos().getCenter();
        final Level level = entity.level();
        if (!level.isClientSide()) {
            final var server = level.getServer();
            return server == null ? center
                    : WaystoneWorldPositionResolver.resolve(server, waystone, new HashMap<>()).orElse(center);
        }

        if (!level.dimension().equals(waystone.getDimension())) {
            return center;
        }
        return Sable.HELPER.projectOutOfSubLevel(level, center);
    }

    private static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(Waystones.MOD_ID, path);
    }
}
