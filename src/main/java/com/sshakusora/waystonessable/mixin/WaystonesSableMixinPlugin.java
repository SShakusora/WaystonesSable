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
    private static final String SABLE_SNAPSHOT_DUAL_PACKET_MIXIN =
            "com.sshakusora.waystonessable.mixin.client.ClientboundSableSnapshotDualPacketMixin";
    private static final String WAYSTONE_IMPL_MIXIN =
            "com.sshakusora.waystonessable.mixin.WaystoneImplMixin";

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

    private static final String SABLE_SNAPSHOT_DUAL_PACKET =
            "dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket";
    private static final String SABLE_PACKET_RECEIVE_MODE =
            "dev.ryanhcode.sable.network.packets.PacketReceiveMode";
    private static final String SABLE_SNAPSHOT_HANDLE_DESCRIPTOR =
            "(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V";

    private static final String WAYSTONE_IMPL =
            "net.blay09.mods.waystones.core.WaystoneImpl";
    private static final String WAYSTONE_IS_VALID_IN_LEVEL_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerLevel;)Z";

    private static final String FORCE_SABLE_ASSEMBLY_MIXIN_PROPERTY =
            "waystonessable.forceSableAssemblyMixin";
    private static final String DISABLE_SABLE_ASSEMBLY_MIXIN_PROPERTY =
            "waystonessable.disableSableAssemblyMixin";
    private static final String DISABLE_SABLE_SNAPSHOT_GUARD_MIXIN_PROPERTY =
            "waystonessable.disableSableSnapshotGuardMixin";
    private static final String DISABLE_WAYSTONE_VALIDITY_MIXIN_PROPERTY =
            "waystonessable.disableWaystoneValidityMixin";

    private static final boolean CREATE_ATTACHED_CHECK_API_COMPATIBLE = isCreateAttachedCheckApiCompatible();
    private static final boolean SABLE_SNAPSHOT_PACKET_COMPATIBLE = isSableSnapshotPacketCompatible();
    private static final boolean WAYSTONE_VALIDITY_API_COMPATIBLE = isWaystoneValidityApiCompatible();

    @Override
    public void onLoad(String mixinPackage) {
        log("loaded: Create attached-check API bytecode compatible=" + CREATE_ATTACHED_CHECK_API_COMPATIBLE
                + ", Sable snapshot packet bytecode compatible=" + SABLE_SNAPSHOT_PACKET_COMPATIBLE
                + ", Waystone validity API bytecode compatible=" + WAYSTONE_VALIDITY_API_COMPATIBLE
                + ", forceFallback=" + Boolean.getBoolean(FORCE_SABLE_ASSEMBLY_MIXIN_PROPERTY)
                + ", disableFallback=" + Boolean.getBoolean(DISABLE_SABLE_ASSEMBLY_MIXIN_PROPERTY)
                + ", disableSnapshotGuard=" + Boolean.getBoolean(DISABLE_SABLE_SNAPSHOT_GUARD_MIXIN_PROPERTY)
                + ", disableWaystoneValidityGuard=" + Boolean.getBoolean(DISABLE_WAYSTONE_VALIDITY_MIXIN_PROPERTY));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (SUB_LEVEL_ASSEMBLY_HELPER_MIXIN.equals(mixinClassName)) {
            boolean applyFallbackMixin = shouldApplySableAssemblyFallbackMixin();
            log((applyFallbackMixin ? "Applying" : "Skipping")
                    + " Sable assembleBlocks fallback mixin " + mixinClassName
                    + " because Create attached-check API bytecode compatible=" + CREATE_ATTACHED_CHECK_API_COMPATIBLE);
            return applyFallbackMixin;
        }

        if (SABLE_SNAPSHOT_DUAL_PACKET_MIXIN.equals(mixinClassName)) {
            boolean applySnapshotGuardMixin = shouldApplySableSnapshotGuardMixin();
            log((applySnapshotGuardMixin ? "Applying" : "Skipping")
                    + " Sable snapshot guard mixin " + mixinClassName
                    + " because Sable snapshot packet bytecode compatible=" + SABLE_SNAPSHOT_PACKET_COMPATIBLE);
            return applySnapshotGuardMixin;
        }

        if (WAYSTONE_IMPL_MIXIN.equals(mixinClassName)) {
            boolean applyWaystoneValidityGuardMixin = shouldApplyWaystoneValidityGuardMixin();
            log((applyWaystoneValidityGuardMixin ? "Applying" : "Skipping")
                    + " Waystone validity guard mixin " + mixinClassName
                    + " because WaystoneImpl#isValidInLevel bytecode compatible=" + WAYSTONE_VALIDITY_API_COMPATIBLE);
            return applyWaystoneValidityGuardMixin;
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
        if (Boolean.getBoolean(FORCE_SABLE_ASSEMBLY_MIXIN_PROPERTY)) {
            return true;
        }
        if (Boolean.getBoolean(DISABLE_SABLE_ASSEMBLY_MIXIN_PROPERTY)) {
            return false;
        }
        return !CREATE_ATTACHED_CHECK_API_COMPATIBLE;
    }

    private static boolean shouldApplySableSnapshotGuardMixin() {
        return SABLE_SNAPSHOT_PACKET_COMPATIBLE && !Boolean.getBoolean(DISABLE_SABLE_SNAPSHOT_GUARD_MIXIN_PROPERTY);
    }

    private static boolean shouldApplyWaystoneValidityGuardMixin() {
        return WAYSTONE_VALIDITY_API_COMPATIBLE && !Boolean.getBoolean(DISABLE_WAYSTONE_VALIDITY_MIXIN_PROPERTY);
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
            log("Create attached-check API bytecode probe failed; enabling Sable fallback mixin. Cause: "
                    + exception.getClass().getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private static boolean isSableSnapshotPacketCompatible() {
        try {
            ClassNode snapshotPacket = readClassNode(SABLE_SNAPSHOT_DUAL_PACKET);
            ClassNode packetReceiveMode = readClassNode(SABLE_PACKET_RECEIVE_MODE);

            return snapshotPacket != null
                    && packetReceiveMode != null
                    && hasField(snapshotPacket, "entries", "Ljava/util/List;")
                    && hasMethod(snapshotPacket, "handleClient", SABLE_SNAPSHOT_HANDLE_DESCRIPTOR);
        } catch (IOException | RuntimeException exception) {
            log("Sable snapshot packet bytecode probe failed; disabling snapshot guard mixin. Cause: "
                    + exception.getClass().getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private static boolean isWaystoneValidityApiCompatible() {
        try {
            ClassNode waystoneImpl = readClassNode(WAYSTONE_IMPL);
            return waystoneImpl != null
                    && hasMethod(waystoneImpl, "isValidInLevel", WAYSTONE_IS_VALID_IN_LEVEL_DESCRIPTOR);
        } catch (IOException | RuntimeException exception) {
            log("Waystones validity API bytecode probe failed; disabling Waystone validity guard mixin. Cause: "
                    + exception.getClass().getName() + ": " + exception.getMessage());
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

    private static void log(String message) {
        System.out.println("[WaystonesSable/MixinPlugin] " + message);
    }
}
