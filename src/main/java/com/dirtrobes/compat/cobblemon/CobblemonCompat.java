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
        // CI : la console du serveur n'est plus lue une fois Cobblemon chargé ; la vérification se lance seule
        if ("1".equals(System.getenv("DIRTROBES_COBBLEMON_SELFCHECK"))) gameBus.addListener(CobblemonCompat::onServerTick);
    }

    private static int ticks;

    private static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (++ticks != 100) return;
        CobblemonBridge.selfCheck(event.getServer());
        event.getServer().halt(false);
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
