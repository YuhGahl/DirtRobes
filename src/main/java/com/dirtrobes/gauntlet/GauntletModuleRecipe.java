package com.dirtrobes.gauntlet;

import com.dirtrobes.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Table de forge : module (gabarit) + gantelet (base) + minéral (ajout) → gantelet avec le module installé.
 * Une arme remplace l'arme précédente ; le module d'extraction s'ajoute.
 */
public record GauntletModuleRecipe(Ingredient template, Ingredient base, Ingredient addition) implements SmithingRecipe {

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return template.test(input.template()) && base.test(input.base()) && addition.test(input.addition())
                && input.template().getItem() instanceof ModuleItem module
                && !current(input.base()).equals(current(input.base()).with(module.module()));
    }

    private static GauntletModules current(ItemStack gauntlet) {
        return gauntlet.getOrDefault(ModRegistry.GAUNTLET_MODULES.get(), GauntletModules.NONE);
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        if (!(input.template().getItem() instanceof ModuleItem module)) return ItemStack.EMPTY;
        ItemStack result = input.base().copyWithCount(1);
        result.set(ModRegistry.GAUNTLET_MODULES.get(), current(input.base()).with(module.module()));
        return result;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ModRegistry.GAUNTLET.get());
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRegistry.GAUNTLET_MODULE_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<GauntletModuleRecipe> {
        private static final MapCodec<GauntletModuleRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC.fieldOf("template").forGetter(GauntletModuleRecipe::template),
                Ingredient.CODEC.fieldOf("base").forGetter(GauntletModuleRecipe::base),
                Ingredient.CODEC.fieldOf("addition").forGetter(GauntletModuleRecipe::addition)
        ).apply(i, GauntletModuleRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, GauntletModuleRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, GauntletModuleRecipe::template,
                Ingredient.CONTENTS_STREAM_CODEC, GauntletModuleRecipe::base,
                Ingredient.CONTENTS_STREAM_CODEC, GauntletModuleRecipe::addition,
                GauntletModuleRecipe::new);

        @Override
        public MapCodec<GauntletModuleRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GauntletModuleRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
