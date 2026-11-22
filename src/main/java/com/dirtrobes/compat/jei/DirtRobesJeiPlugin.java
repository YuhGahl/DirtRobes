package com.dirtrobes.compat.jei;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.gauntlet.GauntletModuleRecipe;
import com.dirtrobes.item.CombatItem;
import com.dirtrobes.registry.ModRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.ItemLike;

/** Intégration JEI (chargée par JEI seulement s'il est installé). */
@JeiPlugin
public class DirtRobesJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return DirtRobes.id("jei");
    }

    /** Les recettes du gantelet sont des recettes de table de forge d'un type à nous : on explique à JEI comment les montrer. */
    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getSmithingCategory().addExtension(GauntletModuleRecipe.class, new ISmithingCategoryExtension<GauntletModuleRecipe>() {
            @Override
            public <T extends IIngredientAcceptor<T>> void setTemplate(GauntletModuleRecipe recipe, T acceptor) {
                acceptor.addIngredients(recipe.template());
            }

            @Override
            public <T extends IIngredientAcceptor<T>> void setBase(GauntletModuleRecipe recipe, T acceptor) {
                acceptor.addIngredients(recipe.base());
            }

            @Override
            public <T extends IIngredientAcceptor<T>> void setAddition(GauntletModuleRecipe recipe, T acceptor) {
                if ("core_weapon".equals(recipe.mode())) {
                    for (var spec : coreSpecs(recipe)) acceptor.addItemStack(com.dirtrobes.gauntlet.SpectrobeCoreItem.charged(spec));
                    return;
                }
                acceptor.addIngredients(recipe.addition());
            }

            @Override
            public <T extends IIngredientAcceptor<T>> void setOutput(GauntletModuleRecipe recipe, T acceptor) {
                if ("core_weapon".equals(recipe.mode())) {
                    for (var spec : coreSpecs(recipe)) acceptor.addItemStack(new ItemStack(ModRegistry.SPECTROBE_WEAPONS.get(spec.itemId()).get()));
                    return;
                }
                ItemStack[] templates = recipe.template().getItems();
                ItemStack[] additions = recipe.addition().getItems();
                if (templates.length == 0 || additions.length == 0) return;
                for (ItemStack addition : additions) {
                    SmithingRecipeInput input = new SmithingRecipeInput(templates[0], new ItemStack(ModRegistry.GAUNTLET.get()), addition);
                    ItemStack out = recipe.assemble(input, RegistryAccess.EMPTY);
                    out.remove(ModRegistry.GAUNTLET_RETURN.get());
                    acceptor.addItemStack(out);
                    if (!"addition_weapon".equals(recipe.mode())) break; // un seul résultat pour les modules
                }
            }
        });
    }

    /** Armes possibles pour une recette « noyau » : celles du type du module gabarit. */
    private static java.util.List<com.dirtrobes.gauntlet.WeaponSpec> coreSpecs(GauntletModuleRecipe recipe) {
        ItemStack[] t = recipe.template().getItems();
        String kind = t.length > 0 && t[0].getItem() instanceof com.dirtrobes.gauntlet.ModuleItem m ? m.module() : "";
        return com.dirtrobes.gauntlet.WeaponSpec.ALL.stream().filter(s -> s.kind().equals(kind)).toList();
    }

    /** Pages d'information : ce que faisaient les lignes d'aide des infobulles. */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        info(registration, "gauntlet", ModRegistry.GAUNTLET.get());
        info(registration, "module", ModRegistry.MODULE_BLADE.get(), ModRegistry.MODULE_CANNON.get(), ModRegistry.MODULE_FIST.get(),
                ModRegistry.MODULE_EXTRACTOR.get());
        info(registration, "prizmod", ModRegistry.PRIZMOD.get());
        info(registration, "spectrobe_core", ModRegistry.SPECTROBE_CORE.get());
        for (var item : ModRegistry.COMBAT_ITEMS) {
            registration.addIngredientInfo(item.get(), Component.translatable("jei.dirtrobes.info.combat_item", CombatItem.SHARED_COOLDOWN / 20));
        }
        for (var fossil : ModRegistry.FOSSILS.values()) info(registration, "fossil", fossil.get());
        registration.addIngredientInfo(ModRegistry.INCUBATOR_ITEM.get(), Component.translatable("jei.dirtrobes.info.incubator",
                com.dirtrobes.block.IncubatorBlockEntity.FE_PER_TICK, 32));
    }

    private static void info(IRecipeRegistration registration, String key, ItemLike... items) {
        for (ItemLike item : items) registration.addIngredientInfo(item, Component.translatable("jei.dirtrobes.info." + key));
    }
}
