package com.dirtrobes.compat;

import com.dirtrobes.gauntlet.GauntletActions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLEnvironment;

/** Pont avec Better Combat (sans référence à ses classes : utilisable même s'il n'est pas installé). */
public final class BetterCombatCompat {
    private BetterCombatCompat() {}

    /** « Main vide, pas de lame » : pile vide distincte, pour que le mixin du registre ne la remplace pas. */
    private static ItemStack noWeapon;

    private static ItemStack noWeapon() {
        if (noWeapon == null) noWeapon = new ItemStack(Items.AIR);
        return noWeapon;
    }

    public static boolean isNoWeapon(ItemStack stack) {
        return stack == noWeapon;
    }

    /** L'objet avec lequel le joueur attaque pour Better Combat : sa main, ou l'arme du gantelet si la main est vide. */
    public static ItemStack attackStack(Player player) {
        ItemStack hand = player.getInventory().getSelected();
        if (!hand.isEmpty()) return hand;
        ItemStack weapon = GauntletActions.activeWeapon(player);
        return weapon.isEmpty() ? noWeapon() : withCombo(weapon);
    }

    /**
     * Épées et Lame du gantelet : enchaînement DirtRobes (deux coups horizontaux, un coup vertical, un estoc).
     * Better Combat lit ce préréglage via son composant de données sur la copie de l'arme.
     */
    public static ItemStack withCombo(ItemStack weapon) {
        if (!weapon.is(net.minecraft.tags.ItemTags.SWORDS) && !weapon.is(com.dirtrobes.registry.ModRegistry.GAUNTLET_BLADE.get())) return weapon;
        ItemStack copy = weapon.copy();
        copy.set(net.bettercombat.api.component.BetterCombatDataComponents.WEAPON_PRESET_ID, com.dirtrobes.DirtRobes.id("gauntlet_combo"));
        return copy;
    }

    public static ItemStack offhandStack(Player player) {
        ItemStack off = player.getInventory().offhand.get(0);
        return off.isEmpty() ? noWeapon() : off;
    }

    /**
     * Vrai seulement pendant le traitement des touches du client (clic d'attaque) : c'est là que Better Combat
     * consulte directement la main principale du joueur local.
     */
    public static volatile boolean handlingKeys;

    /** Arme du gantelet du joueur local (thread client seulement), sinon vide. */
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

    public static ItemStack localWeapon() {
        if (!FMLEnvironment.dist.isClient() || !handlingKeys || BUSY.get()) return ItemStack.EMPTY;
        BUSY.set(true);
        try {
            return com.dirtrobes.client.compat.ClientCompat.localWeapon();
        } finally {
            BUSY.set(false);
        }
    }
}
