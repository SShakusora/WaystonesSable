package com.sshakusora.waystonessable.command;

import com.mojang.brigadier.CommandDispatcher;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * Registers the {@code /waystones activateall <targets>} command.
 * Activates every known waystone in the world for the specified players.
 */
public final class ActivateAllWaystonesCommand {

    private ActivateAllWaystonesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("waystones")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("activateall")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            if (targets.isEmpty()) {
                                                return 0;
                                            }
                                            List<Waystone> allWaystones = WaystonesAPI.getAllWaystones(
                                                    ctx.getSource().getServer()
                                            ).toList();

                                            int activated = 0;
                                            for (ServerPlayer player : targets) {
                                                for (Waystone waystone : allWaystones) {
                                                    if (!WaystonesAPI.isWaystoneActivated(player, waystone)) {
                                                        WaystonesAPI.activateWaystone(player, waystone);
                                                        activated++;
                                                    }
                                                }
                                                WaystoneSyncManager.sendActivatedWaystones(player);
                                            }

                                            int finalActivated = activated;
                                            if (targets.size() == 1) {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.translatable(
                                                                "commands.waystones.activateall.success.single",
                                                                allWaystones.size(),
                                                                targets.iterator().next().getDisplayName()
                                                        ), true);
                                            } else {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.translatable(
                                                                "commands.waystones.activateall.success.multiple",
                                                                allWaystones.size(),
                                                                targets.size()
                                                        ), true);
                                            }
                                            return finalActivated;
                                        }))
                        )
        );
    }
}
