package com.dirtrobes.gauntlet;

import com.dirtrobes.registry.ModRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Table de forge, sur le gantelet (base) :
 * <ul>
 *   <li>mode « module » : module (gabarit) + minéral (ajout) → installe l'arme ou l'extraction du module ;</li>
 *   <li>mode « addition_weapon » : Module de lame (gabarit) + une épée (ajout) → l'épée devient l'arme du gantelet.</li>
 * </ul>
 * L'arme qui était déjà installée est rendue au joueur quand il récupère le gantelet.
 */
public record GauntletModuleRecipe(Ingredient template, Ingredient base, Ingredient addition, String mode) implements SmithingRecipe {

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        if (!template.test(input.template()) || !base.test(input.base()) || !addition.test(input.addition())) return false;
        if ("addition_weapon".equals(mode)) return true;
        return input.template().getItem() instanceof ModuleItem m && !("extractor".equals(m.module()) && GauntletItem.modules(input.base()).extractor());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        ItemStack result = input.base().copyWithCount(1);
        ItemStack newWeapon = ItemStack.EMPTY;
        if ("addition_weapon".equals(mode)) {
            newWeapon = input.addition().copyWithCount(1);
        } else if (input.template().getItem() instanceof ModuleItem m) {
            if ("extractor".equals(m.module())) {
                result.set(ModRegistry.GAUNTLET_MODULES.get(), GauntletItem.modules(result).withExtractor());
                return result;
            }
            newWeapon = new ItemStack(switch (m.module()) {
                case "cannon" -> ModRegistry.GAUNTLET_CANNON.get();
                case "fist" -> ModRegistry.GAUNTLET_FIST.get();
                default -> ModRegistry.GAUNTLET_BLADE.get();
            });
        }
        ItemStack old = GauntletWeapon.get(result);
        GauntletWeapon.set(result, newWeapon);
        // l'ancienne arme sera rendue au joueur quand il prendra le gantelet (GauntletItem#onCraftedBy)
        if (!old.isEmpty() && !old.is(ModRegistry.GAUNTLET_BLADE.get()) && !old.is(ModRegistry.GAUNTLET_CANNON.get())
                && !old.is(ModRegistry.GAUNTLET_FIST.get())) {
            result.set(ModRegistry.GAUNTLET_RETURN.get(), ItemContainerContents.fromItems(List.of(old)));
        }
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
                Ingredient.CODEC.fieldOf("addition").forGetter(GauntletModuleRecipe::addition),
                Codec.STRING.optionalFieldOf("mode", "module").forGetter(GauntletModuleRecipe::mode)
        ).apply(i, GauntletModuleRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, GauntletModuleRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, GauntletModuleRecipe::template,
                Ingredient.CONTENTS_STREAM_CODEC, GauntletModuleRecipe::base,
                Ingredient.CONTENTS_STREAM_CODEC, GauntletModuleRecipe::addition,
                ByteBufCodecs.STRING_UTF8, GauntletModuleRecipe::mode,
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
