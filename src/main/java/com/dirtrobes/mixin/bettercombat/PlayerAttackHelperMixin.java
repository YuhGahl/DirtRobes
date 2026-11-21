package com.dirtrobes.mixin.bettercombat;

import com.dirtrobes.compat.BetterCombatCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Better Combat lit l'arme dans la main principale : main vide avec la lame du gantelet déployée, on lui donne
 * l'arme rangée dans le gantelet (combos, arcs, portée, poses). Seulement dans les méthodes qui LISENT l'arme :
 * l'échange de mains (attaque main gauche) manipule l'inventaire et doit garder les vraies piles.
 */
@Mixin(targets = "net.bettercombat.logic.PlayerAttackHelper", remap = false)
public abstract class PlayerAttackHelperMixin {
    @Redirect(method = {"getCurrentAttack", "poseForPlayer", "isDualWielding", "isTwoHandedWielding", "shouldAttackWithOffHand",
            "getDualWieldingAttackDamageMultiplier", "getRangeWithWeapon", "getAttackCooldownTicksCapped"},
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"), require = 1)
    private static ItemStack dirtrobes$gauntletWeapon(Player player) {
        return BetterCombatCompat.attackStack(player);
    }

    /** Main gauche vide : jamais remplacée par la lame (sinon Better Combat croit au combat à deux armes). */
    @Redirect(method = {"getCurrentAttack", "poseForPlayer", "isDualWielding", "isTwoHandedWielding", "shouldAttackWithOffHand",
            "getDualWieldingAttackDamageMultiplier", "getRangeWithWeapon", "getAttackCooldownTicksCapped"},
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"), require = 0)
    private static ItemStack dirtrobes$realOffhand(Player player) {
        return BetterCombatCompat.offhandStack(player);
    }
}
