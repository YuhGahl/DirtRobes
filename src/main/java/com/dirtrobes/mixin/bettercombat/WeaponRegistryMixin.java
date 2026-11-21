package com.dirtrobes.mixin.bettercombat;

import com.dirtrobes.compat.BetterCombatCompat;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Côté client, Better Combat décide de lancer une attaque d'après la main du joueur local : main vide + lame
 * déployée, on répond avec les attributs de l'arme du gantelet.
 */
@Mixin(value = WeaponRegistry.class, remap = false)
public abstract class WeaponRegistryMixin {
    @Inject(method = "getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void dirtrobes$emptyHand(ItemStack stack, CallbackInfoReturnable<WeaponAttributes> cir) {
        if (stack == null || !stack.isEmpty() || BetterCombatCompat.isNoWeapon(stack)) return;
        ItemStack weapon = BetterCombatCompat.localWeapon();
        if (!weapon.isEmpty()) cir.setReturnValue(WeaponRegistry.getAttributes(weapon));
    }
}
