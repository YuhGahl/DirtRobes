package com.dirtrobes.gauntlet;

import com.dirtrobes.DirtRobes;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Statistiques de l'arme du gantelet : quand la lame est déployée et la main vide, le joueur reçoit les attributs de
 * l'arme (dégâts, vitesse d'attaque, enchantements à attributs), ses enchantements de dégâts (Tranchant, Châtiment…)
 * et d'après-coup (Aura de feu…), et l'épée s'use normalement dans le gantelet.
 */
public final class GauntletCombat {
    private GauntletCombat() {}

    private record Applied(Holder<Attribute> attribute, ResourceLocation id) {}

    private static final Map<UUID, List<Applied>> APPLIED = new HashMap<>();

    public static void syncAttributes(ServerPlayer player) {
        ItemStack weapon = GauntletActions.activeWeapon(player);
        List<Applied> now = new ArrayList<>();
        if (!weapon.isEmpty()) {
            weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, mod) -> {
                AttributeInstance inst = player.getAttribute(attribute);
                if (inst == null) return;
                ResourceLocation id = DirtRobes.id("gauntlet/" + mod.id().getNamespace() + "/" + mod.id().getPath());
                inst.addOrUpdateTransientModifier(new AttributeModifier(id, mod.amount(), mod.operation()));
                now.add(new Applied(attribute, id));
            });
        }
        List<Applied> old = APPLIED.getOrDefault(player.getUUID(), List.of());
        for (Applied a : old) {
            if (now.contains(a)) continue;
            AttributeInstance inst = player.getAttribute(a.attribute());
            if (inst != null) inst.removeModifier(a.id());
        }
        if (now.isEmpty()) APPLIED.remove(player.getUUID());
        else APPLIED.put(player.getUUID(), now);
    }

    public static void forget(ServerPlayer player) {
        APPLIED.remove(player.getUUID());
    }

    /** Coup au corps à corps main vide : bonus des enchantements de dégâts de l'arme. */
    public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
        if (!(source.getDirectEntity() instanceof ServerPlayer player) || !source.is(DamageTypes.PLAYER_ATTACK)) return amount;
        ItemStack weapon = GauntletActions.activeWeapon(player);
        if (weapon.isEmpty()) return amount;
        if (weapon.getItem() instanceof GauntletWeaponItem gw) amount *= gw.effect().damageMultiplier(target);
        return EnchantmentHelper.modifyDamage(player.serverLevel(), weapon, target, source, amount);
    }

    /** Après le coup : effets d'enchantements (Aura de feu…) et usure de l'épée rangée dans le gantelet. */
    public static void afterHit(LivingEntity target, DamageSource source) {
        if (!(source.getDirectEntity() instanceof ServerPlayer player) || !source.is(DamageTypes.PLAYER_ATTACK)) return;
        ItemStack gauntlet = GauntletAccess.find(player);
        ItemStack weapon = GauntletActions.activeWeapon(player);
        if (weapon.isEmpty()) return;
        ServerLevel level = player.serverLevel();
        EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
        if (weapon.getItem() instanceof GauntletWeaponItem gw && gw.effect() != WeaponEffect.NONE) {
            if (gw.effect() == WeaponEffect.DOUBLE) {
                // deuxième coup enchaîné (Lame Maya)
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().playerAttack(player), (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.5f);
            } else {
                gw.effect().apply(target, player);
            }
        }
        if (!weapon.isDamageableItem()) return;
        weapon.hurtAndBreak(1, level, player, item -> {});
        GauntletWeapon.set(gauntlet, weapon);
        if (weapon.isEmpty()) {
            gauntlet.set(com.dirtrobes.registry.ModRegistry.GAUNTLET_MODULES.get(), GauntletItem.modules(gauntlet).withDrawn(false));
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        syncAttributes(player);
    }
}
