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
 * (voir {@link StatCalc#ivForIntegrity}).
 */
public class IncubatorBlockEntity extends BlockEntity implements MenuProvider {
    /** Durée de base de l'éveil : 2 minutes sans accélération. */
    public static final int DURATION = 20 * 120;
    /** Énergie FE consommée par tick pour accélérer (+{@link #FE_BONUS} vitesse). */
    public static final int FE_PER_TICK = 60;
    public static final float FE_BONUS = 2f;

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
                case 0 -> (int) progress;
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
    private float progress;
    private int integrity;
    @Nullable
    private net.minecraft.resources.ResourceLocation speciesId;
    /** Dernière vitesse calculée (1 = normale), pour l'affichage. */
    private float lastSpeed = 1f;
    private final Energy energy = new Energy();

    /** Réserve FE : se remplit de l'extérieur, se vide seulement par l'incubateur lui-même. */
    private final class Energy extends net.neoforged.neoforge.energy.EnergyStorage {
        Energy() {
            super(20_000, 1_000, 0);
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int r = super.receiveEnergy(toReceive, simulate);
            if (r > 0 && !simulate) setChanged();
            return r;
        }

        boolean consume(int amount) {
            if (energy < amount) return false;
            energy -= amount;
            return true;
        }

        void set(int value) {
            energy = Math.max(0, Math.min(capacity, value));
        }
    }

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.INCUBATOR_BE.get(), pos, state);
    }

    /** L'incubateur à cette position : le bloc incubateur, ou celui porté par un autre bloc (incubateur à engrenage). */
    @Nullable
    public static IncubatorBlockEntity at(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IncubatorBlockEntity i) return i;
        if (be instanceof IncubatorHolder h) return h.incubator();
        return null;
    }

    /** Tout l'état de l'incubateur (contenu compris), pour le porter dans un autre bloc. */
    public CompoundTag writeData(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    /** Allume ou éteint le bloc réellement posé à cette position (incubateur ou incubateur à engrenage). */
    private void setActiveState(boolean active) {
        if (level == null) return;
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(IncubatorBlock.ACTIVE) && state.getValue(IncubatorBlock.ACTIVE) != active)
            level.setBlock(worldPosition, state.setValue(IncubatorBlock.ACTIVE, active), 3);
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public boolean isBusy() {
        return property != null;
    }

    public int progress() {
        return (int) progress;
    }

    /** Tests : avance l'éveil pour qu'il reste {@code remainingTicks} ticks. */
    public void skipAhead(int remainingTicks) {
        if (property != null) progress = Math.max(progress, DURATION - remainingTicks);
    }

    public float speed() {
        return lastSpeed;
    }

    public net.neoforged.neoforge.energy.IEnergyStorage energy() {
        return energy;
    }

    @Nullable
    public net.minecraft.resources.ResourceLocation speciesId() {
        return speciesId;
    }

    public int integrity() {
        return integrity;
    }

    @javax.annotation.Nullable
    public Property property() {
        return property;
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
        net.minecraft.resources.ResourceLocation fossilSpecies = stack.get(com.dirtrobes.registry.ModRegistry.FOSSIL_SPECIES.get());
        stack.shrink(1);
        inventory.setChanged();
        if (integrity <= 0) {
            level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1.0f, 0.8f);
            player.displayClientMessage(Component.translatable("message.dirtrobes.fossil_broken").withStyle(ChatFormatting.RED), false);
            return;
        }
        start(p, player, Math.min(100, integrity));
        this.speciesId = fossilSpecies;
        player.displayClientMessage(Component.translatable("message.dirtrobes.incubator_start", Math.min(100, integrity)), true);
    }

    public void start(Property property, Player player, int integrity) {
        this.property = property;
        this.owner = player.getUUID();
        this.progress = 0;
        this.integrity = integrity;
        this.speciesId = null;
        if (level != null) {
            setActiveState(true);
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IncubatorBlockEntity be) {
        if (be.property == null || !(level instanceof ServerLevel server)) return;
        float speed = 1f;
        if (be.energy.consume(FE_PER_TICK)) speed += FE_BONUS;
        // accélération portée par le bloc lui-même (engrenage de Create) ou par un voisin
        if (level.getBlockEntity(pos) instanceof IncubatorBooster self) speed += Math.max(0f, self.incubatorBonus());
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(pos.relative(d)) instanceof IncubatorBooster booster) speed += Math.max(0f, booster.incubatorBonus());
        }
        be.lastSpeed = speed;
        int before = (int) be.progress;
        be.progress += speed;
        if ((int) be.progress / 10 != before / 10) {
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.25, 0.2, 0.25, 0.01);
        }
        if (be.progress >= DURATION) be.hatch(server);
        be.setChanged();
    }

    private void hatch(ServerLevel server) {
        Player player = owner == null ? null : server.getPlayerByUUID(owner);
        Species species = speciesId != null ? SpeciesManager.get(speciesId) : null;
        if (species == null && property != null) species = SpeciesManager.rollFossilSpecies(property, server.random);
        if (species != null) {
            SpectrobeEntity.create(server, species, 1, StatCalc.rollIvs(integrity, server.random), player, Vec3.atBottomCenterOf(worldPosition.above()));
            server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5,
                    40, 0.4, 0.5, 0.4, 0.25);
            server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (player != null) {
                Component msg = Component.translatable("message.dirtrobes.hatched", Component.translatable(species.translationKey()),
                            StatCalc.ivForIntegrity(integrity)).withStyle(ChatFormatting.AQUA);
                if (species.rarity() != com.dirtrobes.species.Rarity.COMMON) {
                    msg = Component.empty().append(msg).append(Component.literal(" ")).append(species.rarity().displayName().withStyle(ChatFormatting.BOLD));
                }
                player.sendSystemMessage(msg);
            }
        } else if (player != null) {
            player.sendSystemMessage(Component.translatable("message.dirtrobes.hatch_failed"));
        }
        property = null;
        owner = null;
        progress = 0;
        integrity = 0;
        setActiveState(false);
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
        tag.putFloat("Progress", progress);
        tag.putInt("Integrity", integrity);
        tag.putInt("Energy", energy.getEnergyStored());
        if (speciesId != null) tag.putString("Species", speciesId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.fromTag(tag.getList("Inventory", 10), registries);
        property = tag.contains("Property") ? Property.byId(tag.getString("Property")) : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        progress = tag.getFloat("Progress");
        speciesId = tag.contains("Species") ? net.minecraft.resources.ResourceLocation.tryParse(tag.getString("Species")) : null;
        energy.set(tag.getInt("Energy"));
        integrity = tag.contains("Integrity") ? tag.getInt("Integrity") : 50;
    }
}
