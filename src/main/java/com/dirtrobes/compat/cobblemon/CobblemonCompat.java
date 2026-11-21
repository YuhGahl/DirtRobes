package com.dirtrobes.compat.cobblemon;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Branchement de l'intégration Cobblemon (appelé seulement si Cobblemon est installé). */
public final class CobblemonCompat {
    private CobblemonCompat() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(CobblemonCompat::onCommands);
    }

    /** /dirtrobes cobblemon check : vérifie que les espèces sont chargées côté Cobblemon (opérateurs, utilisé par la CI). */
    private static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dirtrobes")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("cobblemon")
                        .then(Commands.literal("check").executes(ctx -> {
                            String msg = CobblemonBridge.selfCheck(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                            return msg.contains("aller-retour OK") ? 1 : 0;
                        }))));
    }
}
