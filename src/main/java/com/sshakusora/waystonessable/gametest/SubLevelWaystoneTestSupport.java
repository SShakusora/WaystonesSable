package com.sshakusora.waystonessable.gametest;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.gametest.SableTestHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.block.ModBlocks;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.block.entity.WarpPlateBlockEntity;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.joml.Vector2i;
import org.joml.Vector3d;

final class SubLevelWaystoneTestSupport {

    private SubLevelWaystoneTestSupport() {
    }

    static ServerSubLevelContainer requireContainer(GameTestHelper helper) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container == null) {
            helper.fail("Missing ServerSubLevelContainer");
        }
        return container;
    }

    static SubLevelWaystone createSubLevelWaystone(GameTestHelper helper, ServerSubLevelContainer container, Vector3d position, String name) {
        Pose3d pose = new Pose3d();
        pose.position().set(position);

        ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        LevelPlot plot = subLevel.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        EmbeddedPlotLevelAccessor accessor = plot.getEmbeddedLevelAccessor();

        BlockState lowerState = ModBlocks.waystone.defaultBlockState()
                .setValue(WaystoneBlockBase.HALF, DoubleBlockHalf.LOWER)
                .setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.PLAYER);
        BlockState upperState = lowerState.setValue(WaystoneBlockBase.HALF, DoubleBlockHalf.UPPER);

        accessor.setBlock(BlockPos.ZERO, lowerState, 3, 512);
        accessor.setBlock(BlockPos.ZERO.above(), upperState, 3, 512);

        WaystoneBlockEntityBase lowerEntity = requireWaystone(accessor, BlockPos.ZERO);
        WaystoneBlockEntityBase upperEntity = requireWaystone(accessor, BlockPos.ZERO.above());
        lowerEntity.initializeWaystone(accessor, null, WaystoneOrigin.PLAYER);
        upperEntity.initializeFromBase(lowerEntity);

        Waystone waystone = lowerEntity.getWaystone();
        if (waystone instanceof MutableWaystone mutableWaystone) {
            mutableWaystone.setName(Component.literal(name));
        }

        subLevel.updateLastPose();
        trackSubLevelWaystone(helper.getLevel(), subLevel, waystone);
        return new SubLevelWaystone(subLevel, accessor, lowerEntity, upperEntity, waystone);
    }

    static SubLevelWarpPlate createSubLevelWarpPlate(GameTestHelper helper, ServerSubLevelContainer container, Vector3d position, String name) {
        Pose3d pose = new Pose3d();
        pose.position().set(position);

        ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        LevelPlot plot = subLevel.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        EmbeddedPlotLevelAccessor accessor = plot.getEmbeddedLevelAccessor();

        BlockState state = ModBlocks.warpPlate.defaultBlockState()
                .setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.PLAYER);
        accessor.setBlock(BlockPos.ZERO, state, 3, 512);

        if (!(accessor.getBlockEntity(BlockPos.ZERO) instanceof WarpPlateBlockEntity blockEntity)) {
            throw new IllegalStateException("Expected WarpPlateBlockEntity at " + BlockPos.ZERO);
        }
        blockEntity.initializeWaystone(accessor, null, WaystoneOrigin.PLAYER);

        Waystone waystone = blockEntity.getWaystone();
        if (waystone instanceof MutableWaystone mutableWaystone) {
            mutableWaystone.setName(Component.literal(name));
        }

        subLevel.updateLastPose();
        trackSubLevelWaystone(helper.getLevel(), subLevel, waystone);
        return new SubLevelWarpPlate(subLevel, accessor, blockEntity, waystone);
    }

    static SubLevelWaystone createSubLevelWaystone(GameTestHelper helper, ServerSubLevelContainer container, int index, int spacing, String prefix) {
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(index + 1)));
        int row = index / columns;
        int column = index % columns;
        Vector3d base = SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 4.0, 2.5));
        Vector3d pos = new Vector3d(base.x + column * spacing, base.y, base.z + row * spacing);
        return createSubLevelWaystone(helper, container, pos, prefix + "_" + index);
    }

    static NormalWaystone createNormalWaystone(GameTestHelper helper, BlockPos relativePos, String name) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relativePos);
        BlockState lowerState = ModBlocks.waystone.defaultBlockState()
                .setValue(WaystoneBlockBase.HALF, DoubleBlockHalf.LOWER)
                .setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.PLAYER);
        BlockState upperState = lowerState.setValue(WaystoneBlockBase.HALF, DoubleBlockHalf.UPPER);

        level.setBlock(pos, lowerState, 3);
        level.setBlock(pos.above(), upperState, 3);

        WaystoneBlockEntityBase lowerEntity = requireWaystone(level, pos);
        WaystoneBlockEntityBase upperEntity = requireWaystone(level, pos.above());
        lowerEntity.initializeWaystone(level, null, WaystoneOrigin.PLAYER);
        upperEntity.initializeFromBase(lowerEntity);

        Waystone waystone = lowerEntity.getWaystone();
        if (waystone instanceof MutableWaystone mutableWaystone) {
            mutableWaystone.setName(Component.literal(name));
        }

        SableWaystoneCompat.removeWaystoneTrackingPoint(level, waystone);
        return new NormalWaystone(lowerEntity, upperEntity, waystone);
    }

    static void trackSubLevelWaystone(ServerLevel level, ServerSubLevel subLevel, Waystone waystone) {
        Vector3d localPoint = new Vector3d(
                waystone.getPos().getX() + 0.5,
                waystone.getPos().getY() + 0.5,
                waystone.getPos().getZ() + 0.5
        );
        Vector3d globalPlaceholder = subLevel.getLastSerializationPointer() == null
                ? subLevel.logicalPose().transformPosition(new Vector3d(localPoint))
                : null;
        SubLevelTrackingPointSavedData.getOrLoad(level).setTrackingPoint(waystone.getWaystoneUid(), new TrackingPoint(
                true,
                subLevel.getUniqueId(),
                subLevel.getLastSerializationPointer(),
                localPoint,
                globalPlaceholder
        ));
        SableWaystoneCompat.updateWaystoneTrackingPoint(level, waystone);
    }

    static void clearSubLevelPlot(ServerSubLevelContainer container, ServerSubLevel subLevel) {
        Vector2i origin = container.getOrigin();
        int plotX = subLevel.getPlot().plotPos.x - origin.x;
        int plotZ = subLevel.getPlot().plotPos.z - origin.y;
        if (plotX >= 0 && plotZ >= 0) {
            if (container.getSubLevel(plotX, plotZ) != null) {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            } else {
                container.getOccupancy().clear(container.getIndex(plotX, plotZ));
            }
        }
    }

    private static WaystoneBlockEntityBase requireWaystone(EmbeddedPlotLevelAccessor accessor, BlockPos pos) {
        if (accessor.getBlockEntity(pos) instanceof WaystoneBlockEntityBase waystoneBlockEntity) {
            return waystoneBlockEntity;
        }
        throw new IllegalStateException("Expected WaystoneBlockEntityBase at " + pos);
    }

    private static WaystoneBlockEntityBase requireWaystone(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WaystoneBlockEntityBase waystoneBlockEntity) {
            return waystoneBlockEntity;
        }
        throw new IllegalStateException("Expected WaystoneBlockEntityBase at " + pos);
    }

    record SubLevelWaystone(
            ServerSubLevel subLevel,
            EmbeddedPlotLevelAccessor accessor,
            WaystoneBlockEntityBase lowerEntity,
            WaystoneBlockEntityBase upperEntity,
            Waystone waystone
    ) {
    }

    record SubLevelWarpPlate(
            ServerSubLevel subLevel,
            EmbeddedPlotLevelAccessor accessor,
            WarpPlateBlockEntity blockEntity,
            Waystone waystone
    ) {
    }

    record NormalWaystone(
            WaystoneBlockEntityBase lowerEntity,
            WaystoneBlockEntityBase upperEntity,
            Waystone waystone
    ) {
    }
}
