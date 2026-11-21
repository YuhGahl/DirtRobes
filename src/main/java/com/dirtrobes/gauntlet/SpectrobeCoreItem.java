package com.dirtrobes.gauntlet;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.species.Species;
import com.dirtrobes.species.Stage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Noyau Spectrobe : vide, on l'utilise sur son Spectrobe évolué pour le charger de son énergie (le Spectrobe n'est pas
 * consommé). Chargé, il se pose à la table de forge : module + gantelet + noyau → l'arme de ce Spectrobe.
 */
public class SpectrobeCoreItem extends Item {
    public SpectrobeCoreItem(Properties props) {
        super(props);
    }

    public static WeaponSpec spec(ItemStack stack) {
        ResourceLocation id = stack.get(ModRegistry.FOSSIL_SPECIES.get());
        return stack.getItem() instanceof SpectrobeCoreItem && id != null ? WeaponSpec.forSpecies(id.getPath()) : null;
    }

    public static ItemStack charged(WeaponSpec spec) {
        ItemStack stack = new ItemStack(ModRegistry.SPECTROBE_CORE.get());
        stack.set(ModRegistry.FOSSIL_SPECIES.get(), DirtRobes.id(spec.species()));
        return stack;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof SpectrobeEntity spectrobe) || !spectrobe.isOwnedBy(player) || stack.has(ModRegistry.FOSSIL_SPECIES.get())) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        Species sp = spectrobe.getSpecies();
        WeaponSpec spec = sp == null || sp.stage() == Stage.CHILD || sp.stage() == Stage.ADULT ? null : WeaponSpec.forSpecies(sp.id().getPath());
        if (spec == null) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.core_no_weapon").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        ItemStack charged = charged(spec);
        stack.shrink(1);
        if (!player.getInventory().add(charged)) player.drop(charged, false);
        player.displayClientMessage(Component.translatable("message.dirtrobes.core_charged", spectrobe.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = stack.get(ModRegistry.FOSSIL_SPECIES.get());
        if (id == null) return Component.translatable("item.dirtrobes.spectrobe_core");
        return Component.translatable("item.dirtrobes.spectrobe_core.charged", Component.translatable("species.dirtrobes." + id.getPath()));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistry.FOSSIL_SPECIES.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WeaponSpec spec = spec(stack);
        if (spec != null) {
            tooltip.add(Component.translatable("tooltip.dirtrobes.core_weapon", Component.translatable("item.dirtrobes." + spec.itemId()),
                    Component.translatable("item.dirtrobes.module_" + spec.kind())).withStyle(ChatFormatting.GRAY));
        }
    }
}
