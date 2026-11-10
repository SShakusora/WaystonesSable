package com.sshakusora.waystonessable.teleport;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.core.TwinboundFeatherTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class PlayerTeleportTarget extends WaystoneDelegate {
    private final ServerPlayer requester;
    private final ServerLevel level;
    private final @Nullable ServerSubLevel subLevel;
    private Vec3 worldPosition;
    private boolean destinationResolved;
    private @Nullable Requirements requirements;

    private PlayerTeleportTarget(final Waystone original, final ServerPlayer requester, final ServerPlayer target) {
        super(original);
        this.requester = requester;
        this.level = target.serverLevel();
        this.subLevel = trackingSubLevel(target);
        this.worldPosition = target.position();
    }

    public static Optional<PlayerTeleportTarget> create(final ServerPlayer requester, final Waystone original) {
        final ServerPlayer target = requester.serverLevel().getServer().getPlayerList().getPlayer(original.getWaystoneUid());
        if (!available(target) || TwinboundFeatherTargets.findTarget(requester, original.getWaystoneUid()).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PlayerTeleportTarget(original, requester, target));
    }

    @Nullable
    private static ServerSubLevel trackingSubLevel(final ServerPlayer player) {
        final var tracking = Sable.HELPER.getTrackingSubLevel(player);
        return tracking instanceof ServerSubLevel subLevel && !subLevel.isRemoved()
                && subLevel.getLevel() == player.level()
                && subLevel.getPlot().contains(subLevel.logicalPose().transformPositionInverse(player.position()))
                ? subLevel : null;
    }

    private static boolean available(@Nullable final ServerPlayer player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.hasDisconnected();
    }

    @Nullable
    private ServerPlayer currentTarget() {
        final ServerPlayer target = level.getServer().getPlayerList().getPlayer(getWaystoneUid());
        return available(target) && target.level() == level && trackingSubLevel(target) == subLevel
                && TwinboundFeatherTargets.findTarget(requester, getWaystoneUid()).isPresent() ? target : null;
    }

    public boolean refresh() {
        final ServerPlayer target = currentTarget();
        if (target == null) {
            return false;
        }
        if (!destinationResolved) {
            worldPosition = target.position();
        }
        return true;
    }

    public Vec3 worldPosition() {
        return worldPosition;
    }

    void initializeRequirements(final WaystoneTeleportContext context) {
        requirements = new Requirements(WaystonesAPI.resolveRequirements(context));
        context.setRequirements(requirements);
    }

    void refreshRequirements(final WaystoneTeleportContext context) {
        if (requirements != null) {
            requirements.delegate = WaystonesAPI.resolveRequirements(context);
        }
    }

    @Nullable
    public ServerSubLevel subLevel() {
        return subLevel;
    }

    @Override
    public ResourceKey<Level> getDimension() {
        return level.dimension();
    }

    @Override
    public BlockPos getPos() {
        return BlockPos.containing(worldPosition);
    }

    @Override
    public boolean isValid() {
        return currentTarget() != null;
    }

    @Override
    public boolean isValidInLevel(final ServerLevel candidate) {
        return candidate == level && isValid();
    }

    @Override
    public Optional<TeleportDestination> resolveDestination(final ServerLevel candidate) {
        if (candidate != level || !refresh() || (subLevel == null
                && !level.getChunkSource().hasChunk(getPos().getX() >> 4, getPos().getZ() >> 4))) {
            return Optional.empty();
        }
        destinationResolved = true;
        return Optional.of(new TeleportDestination(level, worldPosition, Direction.NORTH));
    }

    @Override
    public boolean isTransient() {
        return true;
    }

    private static final class Requirements implements WarpRequirement {
        private WarpRequirement delegate;

        private Requirements(final WarpRequirement delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean canAfford(final Player player) {
            return delegate.canAfford(player);
        }

        @Override
        public void consume(final Player player) {
            delegate.consume(player);
        }

        @Override
        public void rollback(final Player player) {
            delegate.rollback(player);
        }

        @Override
        public void appendHoverText(final Player player, final List<Component> tooltip) {
            delegate.appendHoverText(player, tooltip);
        }
    }
}
