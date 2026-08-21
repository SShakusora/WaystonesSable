package com.sshakusora.waystonessable.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

public final class WaystonesSableMixinPlugin implements IMixinConfigPlugin {
    private static final String SUB_LEVEL_ASSEMBLY_HELPER_MIXIN =
            "com.sshakusora.waystonessable.mixin.SubLevelAssemblyHelperMixin";
    private static final String CLIENTBOUND_START_TRACKING_SUB_LEVEL_PACKET_MIXIN =
            "com.sshakusora.waystonessable.mixin.client.ClientboundStartTrackingSubLevelPacketMixin";
    private static final String CLIENTBOUND_STOP_TRACKING_SUB_LEVEL_PACKET_MIXIN =
            "com.sshakusora.waystonessable.mixin.client.ClientboundStopTrackingSubLevelPacketMixin";
    private static final String HOLDING_SUB_LEVEL_MIXIN =
            "com.sshakusora.waystonessable.mixin.HoldingSubLevelMixin";
    private static final String SUB_LEVEL_HOLDING_CHUNK_MAP_MIXIN =
            "com.sshakusora.waystonessable.mixin.SubLevelHoldingChunkMapMixin";

    private static final String CREATE_BLOCK_MOVEMENT_CHECKS =
            "com.simibubi.create.api.contraption.BlockMovementChecks";
    private static final String CREATE_ATTACHED_CHECK =
            "com.simibubi.create.api.contraption.BlockMovementChecks$AttachedCheck";
    private static final String CREATE_CHECK_RESULT =
            "com.simibubi.create.api.contraption.BlockMovementChecks$CheckResult";
    private static final String CREATE_ATTACHED_CHECK_DESCRIPTOR =
            "Lcom/simibubi/create/api/contraption/BlockMovementChecks$AttachedCheck;";
    private static final String CREATE_CHECK_RESULT_DESCRIPTOR =
            "Lcom/simibubi/create/api/contraption/BlockMovementChecks$CheckResult;";
    private static final String REGISTER_ATTACHED_CHECK_DESCRIPTOR =
            "(" + CREATE_ATTACHED_CHECK_DESCRIPTOR + ")V";

    private static final String SUB_LEVEL_ASSEMBLY_HELPER =
            "dev.ryanhcode.sable.api.SubLevelAssemblyHelper";
    private static final String ASSEMBLE_BLOCKS_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/Iterable;"
                    + "Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;";

    private static final String CLIENTBOUND_START_TRACKING_SUB_LEVEL_PACKET =
            "dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket";
    private static final String CLIENTBOUND_STOP_TRACKING_SUB_LEVEL_PACKET =
            "dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket";
    private static final String PACKET_HANDLE_DESCRIPTOR =
            "(Lfoundry/veil/api/network/handler/PacketContext;)V";
    private static final String PLOT_COORDINATE_DESCRIPTOR = "()J";

    private static final String HOLDING_SUB_LEVEL =
            "dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel";
    private static final String GLOBAL_SAVED_SUB_LEVEL_POINTER =
            "dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer";
    private static final String SET_POINTER_DESCRIPTOR =
            "(L" + GLOBAL_SAVED_SUB_LEVEL_POINTER.replace('.', '/') + ";)V";
    private static final String SUB_LEVEL_HOLDING_CHUNK_MAP =
            "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap";
    private static final String SAVE_ALL_DESCRIPTOR = "()V";

    private static final String FORCE_SABLE_ASSEMBLY_MIXIN_PROPERTY =
            "waystonessable.forceSableAssemblyMixin";
    private static final String DISABLE_SABLE_ASSEMBLY_MIXIN_PROPERTY =
            "waystonessable.disableSableAssemblyMixin";
    private static final String DISABLE_SABLE_TRACKING_PACKET_MIXINS_PROPERTY =
            "waystonessable.disableSableTrackingPacketMixins";

    private static final boolean CREATE_ATTACHED_CHECK_API_COMPATIBLE = isCreateAttachedCheckApiCompatible();
    private static final boolean SABLE_ASSEMBLY_HELPER_COMPATIBLE = isSableAssemblyHelperCompatible();
    private static final boolean START_TRACKING_PACKET_COMPATIBLE =
            isSableTrackingPacketCompatible(CLIENTBOUND_START_TRACKING_SUB_LEVEL_PACKET);
    private static final boolean STOP_TRACKING_PACKET_COMPATIBLE =
            isSableTrackingPacketCompatible(CLIENTBOUND_STOP_TRACKING_SUB_LEVEL_PACKET);
    private static final boolean SABLE_POINTER_SYNC_COMPATIBLE = isSablePointerSyncCompatible();

    @Override
    public void onLoad(String mixinPackage) {
        log("loaded: Create attached-check API compatible=" + CREATE_ATTACHED_CHECK_API_COMPATIBLE
                + ", Sable assembleBlocks compatible=" + SABLE_ASSEMBLY_HELPER_COMPATIBLE
                + ", Sable start-tracking packet compatible=" + START_TRACKING_PACKET_COMPATIBLE
                + ", Sable stop-tracking packet compatible=" + STOP_TRACKING_PACKET_COMPATIBLE
                + ", Sable pointer-sync API compatible=" + SABLE_POINTER_SYNC_COMPATIBLE
                + ", forceAssemblyMixin=" + Boolean.getBoolean(FORCE_SABLE_ASSEMBLY_MIXIN_PROPERTY)
                + ", disableAssemblyMixin=" + Boolean.getBoolean(DISABLE_SABLE_ASSEMBLY_MIXIN_PROPERTY)
                + ", disableTrackingPacketMixins=" + Boolean.getBoolean(DISABLE_SABLE_TRACKING_PACKET_MIXINS_PROPERTY));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (SUB_LEVEL_ASSEMBLY_HELPER_MIXIN.equals(mixinClassName)) {
            boolean apply = shouldApplySableAssemblyFallbackMixin();
            log((apply ? "Applying" : "Skipping") + " " + mixinClassName
                    + " (Create attached-check API compatible=" + CREATE_ATTACHED_CHECK_API_COMPATIBLE
                    + ", Sable assembleBlocks compatible=" + SABLE_ASSEMBLY_HELPER_COMPATIBLE + ")");
            return apply;
        }

        if (CLIENTBOUND_START_TRACKING_SUB_LEVEL_PACKET_MIXIN.equals(mixinClassName)) {
            boolean apply = shouldApplySableTrackingPacketMixin(START_TRACKING_PACKET_COMPATIBLE);
            log((apply ? "Applying" : "Skipping") + " " + mixinClassName
                    + " (target packet compatible=" + START_TRACKING_PACKET_COMPATIBLE + ")");
            return apply;
        }

        if (CLIENTBOUND_STOP_TRACKING_SUB_LEVEL_PACKET_MIXIN.equals(mixinClassName)) {
            boolean apply = shouldApplySableTrackingPacketMixin(STOP_TRACKING_PACKET_COMPATIBLE);
            log((apply ? "Applying" : "Skipping") + " " + mixinClassName
                    + " (target packet compatible=" + STOP_TRACKING_PACKET_COMPATIBLE + ")");
            return apply;
        }

        if (HOLDING_SUB_LEVEL_MIXIN.equals(mixinClassName)
                || SUB_LEVEL_HOLDING_CHUNK_MAP_MIXIN.equals(mixinClassName)) {
            log((SABLE_POINTER_SYNC_COMPATIBLE ? "Applying" : "Skipping") + " " + mixinClassName
                    + " (Sable pointer-sync API compatible=" + SABLE_POINTER_SYNC_COMPATIBLE + ")");
            return SABLE_POINTER_SYNC_COMPATIBLE;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean shouldApplySableAssemblyFallbackMixin() {
        if (!SABLE_ASSEMBLY_HELPER_COMPATIBLE) {
            return false;
        }
        if (Boolean.getBoolean(FORCE_SABLE_ASSEMBLY_MIXIN_PROPERTY)) {
            return true;
        }
        if (Boolean.getBoolean(DISABLE_SABLE_ASSEMBLY_MIXIN_PROPERTY)) {
            return false;
        }
        return !CREATE_ATTACHED_CHECK_API_COMPATIBLE;
    }

    private static boolean shouldApplySableTrackingPacketMixin(boolean targetCompatible) {
        return targetCompatible && !Boolean.getBoolean(DISABLE_SABLE_TRACKING_PACKET_MIXINS_PROPERTY);
    }

    private static boolean isCreateAttachedCheckApiCompatible() {
        try {
            ClassNode movementChecks = readClassNode(CREATE_BLOCK_MOVEMENT_CHECKS);
            ClassNode attachedCheck = readClassNode(CREATE_ATTACHED_CHECK);
            ClassNode checkResult = readClassNode(CREATE_CHECK_RESULT);

            return movementChecks != null
                    && attachedCheck != null
                    && checkResult != null
                    && hasMethod(movementChecks, "registerAttachedCheck", REGISTER_ATTACHED_CHECK_DESCRIPTOR)
                    && hasField(checkResult, "SUCCESS", CREATE_CHECK_RESULT_DESCRIPTOR)
                    && hasField(checkResult, "PASS", CREATE_CHECK_RESULT_DESCRIPTOR);
        } catch (IOException | RuntimeException exception) {
            logProbeFailure("Create attached-check API", exception);
            return false;
        }
    }

    private static boolean isSableAssemblyHelperCompatible() {
        try {
            ClassNode assemblyHelper = readClassNode(SUB_LEVEL_ASSEMBLY_HELPER);
            return assemblyHelper != null && hasMethod(assemblyHelper, "assembleBlocks", ASSEMBLE_BLOCKS_DESCRIPTOR);
        } catch (IOException | RuntimeException exception) {
            logProbeFailure("Sable assembleBlocks API", exception);
            return false;
        }
    }

    private static boolean isSableTrackingPacketCompatible(String className) {
        try {
            ClassNode packet = readClassNode(className);
            return packet != null
                    && hasMethod(packet, "handle", PACKET_HANDLE_DESCRIPTOR)
                    && hasMethod(packet, "plotCoordinate", PLOT_COORDINATE_DESCRIPTOR);
        } catch (IOException | RuntimeException exception) {
            logProbeFailure(className, exception);
            return false;
        }
    }

    private static boolean isSablePointerSyncCompatible() {
        try {
            ClassNode holdingSubLevel = readClassNode(HOLDING_SUB_LEVEL);
            ClassNode holdingChunkMap = readClassNode(SUB_LEVEL_HOLDING_CHUNK_MAP);
            return holdingSubLevel != null
                    && holdingChunkMap != null
                    && hasMethod(holdingSubLevel, "setPointer", SET_POINTER_DESCRIPTOR)
                    && hasMethod(holdingChunkMap, "saveAll", SAVE_ALL_DESCRIPTOR);
        } catch (IOException | RuntimeException exception) {
            logProbeFailure("Sable pointer-sync API", exception);
            return false;
        }
    }

    private static ClassNode readClassNode(String className) throws IOException {
        String resourceName = className.replace('.', '/') + ".class";
        InputStream stream = openResource(resourceName);
        if (stream == null) {
            return null;
        }

        try (InputStream input = stream) {
            ClassNode classNode = new ClassNode();
            new ClassReader(input).accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return classNode;
        }
    }

    private static InputStream openResource(String resourceName) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = contextClassLoader == null ? null : contextClassLoader.getResourceAsStream(resourceName);
        if (stream != null) {
            return stream;
        }

        ClassLoader pluginClassLoader = WaystonesSableMixinPlugin.class.getClassLoader();
        if (pluginClassLoader != null && pluginClassLoader != contextClassLoader) {
            return pluginClassLoader.getResourceAsStream(resourceName);
        }
        return null;
    }

    private static boolean hasMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasField(ClassNode classNode, String name, String descriptor) {
        for (FieldNode field : classNode.fields) {
            if (name.equals(field.name) && descriptor.equals(field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static void logProbeFailure(String probeName, Exception exception) {
        log(probeName + " bytecode probe failed. Cause: "
                + exception.getClass().getName() + ": " + exception.getMessage());
    }

    private static void log(String message) {
        System.out.println("[WaystonesSable/MixinPlugin] " + message);
    }
}
