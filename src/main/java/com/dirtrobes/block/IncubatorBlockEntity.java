package com.dirtrobes.block;

import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.entity.StatCalc;
import com.dirtrobes.item.FossilItem;
import com.dirtrobes.menu.IncubatorMenu;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.species.Property;
import com.dirtrobes.species.Species;
import com.dirtrobes.species.SpeciesManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Incubateur : un emplacement pour un fossile. Le joueur dégage le fossile de sa gangue dans le
 * mini-jeu de fouille ; si le fossile survit, l'éveil démarre et un Spectrobe enfant apparaît
 * au bout de {@link #DURATION} ticks. Plus le fossile est intact, meilleures sont ses stats
 * (voir {@link StatCalc#qualityForIntegrity}).
 */
public class IncubatorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int DURATION = 400; // 20 secondes

    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            IncubatorBlockEntity.this.setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof FossilItem;
        }
    };

    /** Données synchronisées avec l'écran : 0 = progression, 1 = durée, 2 = intégrité du fossile en cours. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> property == null ? 0 : DURATION;
                case 2 -> integrity;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    @Nullable
    private Property property;
    @Nullable
    private UUID owner;
    private int progress;
    private int integrity;

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.INCUBATOR_BE.get(), pos, state);
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public boolean isBusy() {
        return property != null;
    }

    /**
     * Résultat du mini-jeu de fouille, validé côté serveur.
     * @param integrity état du fossile (0 = brisé, 100 = intact)
     */
    public void finishExcavation(Player player, int integrity) {
        if (isBusy() || level == null) return;
        ItemStack stack = inventory.getItem(0);
        if (!(stack.getItem() instanceof FossilItem fossil)) return;
        Property p = fossil.property();
        stack.shrink(1);
        inventory.setChanged();
        if (integrity <= 0) {
            level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1.0f, 0.8f);
            player.displayClientMessage(Component.translatable("message.dirtrobes.fossil_broken").withStyle(ChatFormatting.RED), false);
            return;
        }
        start(p, player, Math.min(100, integrity));
        player.displayClientMessage(Component.translatable("message.dirtrobes.incubator_start", Math.min(100, integrity)), true);
    }

    public void start(Property property, Player player, int integrity) {
        this.property = property;
        this.owner = player.getUUID();
        this.progress = 0;
        this.integrity = integrity;
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(IncubatorBlock.ACTIVE, true), 3);
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IncubatorBlockEntity be) {
        if (be.property == null || !(level instanceof ServerLevel server)) return;
        be.progress++;
        if (be.progress % 10 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.25, 0.2, 0.25, 0.01);
        }
        if (be.progress >= DURATION) be.hatch(server);
        be.setChanged();
    }

    private void hatch(ServerLevel server) {
        Player player = owner == null ? null : server.getPlayerByUUID(owner);
        Species species = property == null ? null : SpeciesManager.randomFossilSpecies(property, server.random);
        if (species != null) {
            SpectrobeEntity.create(server, species, 1, StatCalc.qualityForIntegrity(integrity), player, Vec3.atBottomCenterOf(worldPosition.above()));
            server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5,
                    40, 0.4, 0.5, 0.4, 0.25);
            server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.dirtrobes.hatched", Component.translatable(species.translationKey()),
                            StatCalc.qualityForIntegrity(integrity))
                        .withStyle(ChatFormatting.AQUA));
            }
        } else if (player != null) {
            player.sendSystemMessage(Component.translatable("message.dirtrobes.hatch_failed"));
        }
        property = null;
        owner = null;
        progress = 0;
        integrity = 0;
        server.setBlock(worldPosition, getBlockState().setValue(IncubatorBlock.ACTIVE, false), 3);
    }

    // ----- Interface -----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.dirtrobes.incubator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new IncubatorMenu(containerId, playerInventory, inventory, data, worldPosition);
    }

    // ----- Sauvegarde -----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.createTag(registries));
        if (property != null) tag.putString("Property", property.id());
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Progress", progress);
        tag.putInt("Integrity", integrity);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.fromTag(tag.getList("Inventory", 10), registries);
        property = tag.contains("Property") ? Property.byId(tag.getString("Property")) : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        progress = tag.getInt("Progress");
        integrity = tag.contains("Integrity") ? tag.getInt("Integrity") : 50;
    }
}
