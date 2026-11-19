package com.dirtrobes.entity;

import com.dirtrobes.item.MineralItem;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.species.Property;
import com.dirtrobes.species.Species;
import com.dirtrobes.species.SpeciesManager;
import com.dirtrobes.species.Nature;
import com.dirtrobes.species.Stage;
import com.dirtrobes.team.PrizmodTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Le Spectrobe. Une seule entité pour toutes les espèces : l'espèce, le niveau, la propriété
 * et le stade sont des données synchronisées (même approche que Cobblemon).
 */
public class SpectrobeEntity extends TamableAnimal implements BedrockPosable {
    private static final EntityDataAccessor<String> DATA_SPECIES = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PROPERTY = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_STAGE = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_COLOR = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.BYTE);

    private static final int FLAG_SLEEPING = 1;
    private static final int FLAG_BATTLE = 2;
    private static final int FLAG_KO = 4;

    public static final byte EVENT_CRY = 110;
    public static final byte EVENT_SPECIAL = 111;

    private int xp;
    private int evHp;
    private int evAtk;
    private int evDef;
    private int evSpd;
    private Nature nature = Nature.BALANCED;
    private int koTicks;
    /** Qualité des stats en % (50 à 100), fixée à l'éclosion selon l'intégrité du fossile. */
    private int quality = 100;
    private int specialCooldown;
    private int strikeTicks;
    @Nullable
    private LivingEntity strikeTarget;
    private int searchCooldown = 40;

    /** État d'animation côté client, géré par le moteur de rendu. */
    @Nullable
    public Object clientAnimState;
    /** Tick client du dernier cri / attaque spéciale reçu (pour les animations ponctuelles). */
    public int lastCryTick = -1000;

    public SpectrobeEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPECIES, "");
        builder.define(DATA_LEVEL, 1);
        builder.define(DATA_PROPERTY, (byte) Property.PRISMA.ordinal());
        builder.define(DATA_STAGE, (byte) Stage.CHILD.ordinal());
        builder.define(DATA_COLOR, (byte) 0);
        builder.define(DATA_FLAGS, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.25, true) {
            @Override
            public boolean canUse() {
                return canFight() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return canFight() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(6, new SpectrobeFollowOwnerGoal(this));
        this.goalSelector.addGoal(8, new SpectrobeStrollGoal(this, 0.6));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f, 0.05f));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return canFight() && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this) {
            @Override
            public boolean canUse() {
                return canFight() && super.canUse();
            }
        });
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return canFight() && super.canUse();
            }
        });
    }

    // ----- Pensionnaire de l'enclos du laboratoire (copie visible d'un Spectrobe rangé au PC) -----
    @Nullable
    private BlockPos penTerminal;
    @Nullable
    private com.dirtrobes.lab.Pen pen;

    public void setPenResident(BlockPos terminal, com.dirtrobes.lab.Pen pen) {
        this.penTerminal = terminal;
        this.pen = pen;
        setOrderedToSit(false);
        setInSittingPose(false);
        setTarget(null);
    }

    public boolean isPenResident() {
        return penTerminal != null;
    }

    @Nullable
    public com.dirtrobes.lab.Pen pen() {
        return pen;
    }

    /** Les pensionnaires ne sont pas sauvegardés avec le monde : le terminal les recrée depuis le PC. */
    @Override
    public boolean shouldBeSaved() {
        return !isPenResident() && super.shouldBeSaved();
    }

    private void tickPenResident(ServerLevel server) {
        if (pen == null || penTerminal == null) return;
        if (tickCount % 40 == 0 && !(server.isLoaded(penTerminal)
                && server.getBlockEntity(penTerminal) instanceof com.dirtrobes.lab.LabTerminalBlockEntity t && t.holds(getUUID()) && pen.equals(t.pen()))) {
            discard();
            return;
        }
        if (!pen.contains(position())) {
            Vec3 c = Vec3.atBottomCenterOf(pen.spawn());
            if (!pen.box().inflate(6).contains(position())) {
                Vec3 spot = pen.spawnSpot(server, this, random);
                if (spot != null) moveTo(spot.x, spot.y, spot.z, getYRot(), getXRot());
            } else if (getNavigation().isDone()) {
                getNavigation().moveTo(c.x, c.y, c.z, 0.8);
            }
        }
    }

    /** Pour les tests : comportements (goals) en cours d'exécution. */
    public String debugRunningGoals() {
        StringBuilder sb = new StringBuilder();
        this.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                .forEach(g -> sb.append(g.getGoal().getClass().getName()).append(' '));
        this.targetSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                .forEach(g -> sb.append("cible:").append(g.getGoal().getClass().getName()).append(' '));
        return sb.toString().trim();
    }

    // =====================================================================
    // Espèce, propriété, stade
    // =====================================================================

    /** Mannequin côté client pour afficher le modèle d'une espèce dans une interface (pas ajouté au monde). */
    public static SpectrobeEntity preview(Level level, String speciesId, int property, int stage, int color) {
        SpectrobeEntity e = new SpectrobeEntity(ModRegistry.SPECTROBE.get(), level);
        e.entityData.set(DATA_COLOR, (byte) color);
        e.entityData.set(DATA_SPECIES, speciesId);
        e.entityData.set(DATA_PROPERTY, (byte) property);
        e.entityData.set(DATA_STAGE, (byte) stage);
        return e;
    }

    // ----- BedrockPosable (rendu) -----
    @Override
    public int colorVariant() {
        return this.entityData.get(DATA_COLOR);
    }

    /** Minéral Chroma : 0 = couleur normale, 1 et 2 = couleurs spéciales. Renvoie false si rien ne change. */
    public boolean feedChroma(int color, Player player) {
        if (colorVariant() == color) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.chroma_same", getDisplayName()), true);
            return false;
        }
        this.entityData.set(DATA_COLOR, (byte) color);
        player.displayClientMessage(Component.translatable("message.dirtrobes.chroma_changed", getDisplayName(),
                Component.translatable("chroma.dirtrobes." + color)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 40, 0.4, 0.6, 0.4, 0.1);
        }
        level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0f, 1.6f);
        return true;
    }

    @Override
    public String resolverStage() {
        return getStage().id();
    }

    @Override
    public int tintColor() {
        return getProperty().color();
    }

    @Override
    public boolean isDown() {
        return isKnockedOut() || deathTime > 0;
    }

    @Override
    public boolean isPoseSleeping() {
        return isSpectrobeSleeping();
    }

    @Override
    public int lastCryTick() {
        return lastCryTick;
    }

    @Override
    @Nullable
    public Object clientAnimState() {
        return clientAnimState;
    }

    @Override
    public void setClientAnimState(Object state) {
        this.clientAnimState = state;
    }

    @Override
    @Nullable
    public ResourceLocation getSpeciesId() {
        String s = this.entityData.get(DATA_SPECIES);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }

    @Nullable
    public Species getSpecies() {
        return SpeciesManager.get(getSpeciesId());
    }

    public Property getProperty() {
        return Property.byOrdinal(this.entityData.get(DATA_PROPERTY));
    }

    public Stage getStage() {
        return Stage.byOrdinal(this.entityData.get(DATA_STAGE));
    }

    public int getSpectrobeLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    public void setSpecies(Species species, boolean fullHeal) {
        this.entityData.set(DATA_SPECIES, species.id().toString());
        this.entityData.set(DATA_PROPERTY, (byte) species.property().ordinal());
        this.entityData.set(DATA_STAGE, (byte) species.stage().ordinal());
        recomputeStats(fullHeal);
    }

    public void setSpectrobeLevel(int level) {
        this.entityData.set(DATA_LEVEL, Mth.clamp(level, 1, StatCalc.MAX_LEVEL));
        this.xp = Math.max(this.xp, StatCalc.xpForLevel(getSpectrobeLevel()));
        recomputeStats(true);
    }

    private boolean getFlag(int flag) {
        return (this.entityData.get(DATA_FLAGS) & flag) != 0;
    }

    private void setFlag(int flag, boolean value) {
        byte b = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, (byte) (value ? (b | flag) : (b & ~flag)));
    }

    public boolean isSpectrobeSleeping() {
        return getFlag(FLAG_SLEEPING);
    }

    public boolean isInBattle() {
        return getFlag(FLAG_BATTLE);
    }

    public boolean isKnockedOut() {
        return getFlag(FLAG_KO);
    }

    public boolean canFight() {
        if (isPenResident()) return false;
        return getStage().fights() && !isKnockedOut() && !isInSittingPose();
    }

    // =====================================================================
    // Stats (façon Pokémon, sans IV)
    // =====================================================================

    public int hpStat() {
        Species s = getSpecies();
        return s == null ? 20 : StatCalc.applyQuality(StatCalc.applyNature(StatCalc.hp(s.baseHp(), evHp, getSpectrobeLevel()), nature, MineralItem.Stat.HEALTH), quality);
    }

    public int attackStat() {
        Species s = getSpecies();
        return s == null ? 10 : StatCalc.applyQuality(StatCalc.applyNature(StatCalc.other(s.baseAttack(), evAtk, getSpectrobeLevel()), nature, MineralItem.Stat.POWER), quality);
    }

    public int defenseStat() {
        Species s = getSpecies();
        return s == null ? 10 : StatCalc.applyQuality(StatCalc.applyNature(StatCalc.other(s.baseDefense(), evDef, getSpectrobeLevel()), nature, MineralItem.Stat.DEFENCE), quality);
    }

    public int speedStat() {
        Species s = getSpecies();
        return s == null ? 10 : StatCalc.applyQuality(StatCalc.applyNature(StatCalc.other(s.baseSpeed(), evSpd, getSpectrobeLevel()), nature, MineralItem.Stat.SPEED), quality);
    }

    public Nature getNature() {
        return nature;
    }

    public void setNature(Nature nature) {
        this.nature = nature;
        recomputeStats(false);
    }

    public int getEv(MineralItem.Stat stat) {
        return switch (stat) {
            case HEALTH -> evHp;
            case POWER -> evAtk;
            case DEFENCE -> evDef;
            case SPEED -> evSpd;
        };
    }

    /**
     * Repos dans le Prizmod : récupère 5 % de ses PV max par tranche de 100 ticks, et se remet d'un K.O.
     * deux fois plus vite que dehors.
     */
    public void restInPrizmod(int ticks) {
        if (isKnockedOut()) {
            koTicks -= ticks * 2;
            if (koTicks <= 0) {
                koTicks = 0;
                setFlag(FLAG_KO, false);
                setHealth(Math.max(getHealth(), getMaxHealth() * 0.5f));
            }
        } else {
            setHealth(Math.min(getMaxHealth(), getHealth() + getMaxHealth() * 0.05f * ticks / 100f));
        }
    }

    public int getXp() {
        return xp;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = Math.max(50, Math.min(100, quality));
        recomputeStats(true);
    }

    public void recomputeStats(boolean fullHeal) {
        Species s = getSpecies();
        if (s == null || level().isClientSide) return;
        setBase(Attributes.MAX_HEALTH, StatCalc.toMaxHealth(hpStat()));
        setBase(Attributes.ATTACK_DAMAGE, StatCalc.toAttackDamage(attackStat()));
        setBase(Attributes.ARMOR, StatCalc.toArmor(defenseStat()));
        setBase(Attributes.MOVEMENT_SPEED, StatCalc.toMovementSpeed(speedStat()));
        setBase(Attributes.SCALE, s.scale());
        if (fullHeal) setHealth(getMaxHealth());
        else setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    public int totalEv() {
        return evHp + evAtk + evDef + evSpd;
    }

    /** Ajoute (ou retire si négatif) des EV, dans les limites 0–252 par stat et 510 au total. Renvoie le changement réel. */
    public int addEv(MineralItem.Stat stat, int amount) {
        if (amount > 0) amount = Math.min(amount, StatCalc.MAX_EV_TOTAL - totalEv());
        if (amount == 0) return 0;
        int before = getEv(stat);
        int after = Math.max(0, Math.min(StatCalc.MAX_EV_PER_STAT, before + amount));
        switch (stat) {
            case HEALTH -> evHp = after;
            case POWER -> evAtk = after;
            case DEFENCE -> evDef = after;
            case SPEED -> evSpd = after;
        }
        if (after != before) recomputeStats(false);
        return after - before;
    }

    /** Objet de combat sur ce Spectrobe. Renvoie false (objet non consommé) s'il n'a aucun effet. */
    public boolean useCombatItem(com.dirtrobes.item.CombatItem.Kind kind, Player player) {
        boolean ok = switch (kind) {
            case SERUM -> {
                if (isKnockedOut() || getHealth() >= getMaxHealth()) yield false;
                heal(getMaxHealth() * 0.5f);
                yield true;
            }
            case REVIVE -> {
                if (!isKnockedOut()) yield false;
                koTicks = 0;
                setFlag(FLAG_KO, false);
                setHealth(Math.max(1f, getMaxHealth() * 0.3f));
                yield true;
            }
            case ANTIDOTE -> {
                var bad = getActiveEffects().stream().filter(e -> !e.getEffect().value().isBeneficial()).map(e -> e.getEffect()).toList();
                bad.forEach(this::removeEffect);
                yield !bad.isEmpty();
            }
            default -> false;
        };
        if (!ok) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.item_no_effect", getDisplayName()), true);
            return false;
        }
        player.displayClientMessage(Component.translatable("message.dirtrobes.item_used." + kind.id(), getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 5, 0.4, 0.3, 0.4, 0.0);
        }
        level().playSound(null, blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.8f, 1.4f);
        return true;
    }

    /** Gemme de nature : remplace la nature. Renvoie false (gemme non consommée) si c'est déjà la sienne. */
    public boolean feedNatureGem(com.dirtrobes.item.NatureGemItem gem, Player player) {
        if (gem.nature() == nature) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.nature_same", getDisplayName(),
                    Component.translatable(nature.translationKey())), true);
            return false;
        }
        setNature(gem.nature());
        player.displayClientMessage(Component.translatable("message.dirtrobes.nature_changed", getDisplayName(),
                Component.translatable(nature.translationKey())).withStyle(ChatFormatting.AQUA), true);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + getBbHeight(), getZ(), 30, 0.4, 0.4, 0.4, 0.5);
        }
        level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0f, 1.2f);
        return true;
    }

    /** Gemme : monte ou baisse les EV d'une stat, plus un peu d'XP. */
    public void feedGem(com.dirtrobes.item.GemItem gem, Player player) {
        int changed = addEv(gem.stat(), gem.evChange());
        addXp(gem.xp());
        player.displayClientMessage(Component.translatable(changed == 0 ? "message.dirtrobes.gem_no_effect" : "message.dirtrobes.gem_fed",
                getDisplayName(), Component.translatable("stat.dirtrobes." + gem.stat().id()), (changed > 0 ? "+" : "") + changed), true);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(changed >= 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE, getX(), getY() + getBbHeight(), getZ(), 8, 0.4, 0.3, 0.4, 0.0);
        }
        level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9f, changed >= 0 ? 1.4f : 0.7f);
    }

    public void addXp(int amount) {
        if (level().isClientSide || amount <= 0) return;
        xp += amount;
        boolean leveled = false;
        while (getSpectrobeLevel() < StatCalc.MAX_LEVEL && xp >= StatCalc.xpForLevel(getSpectrobeLevel() + 1)) {
            this.entityData.set(DATA_LEVEL, getSpectrobeLevel() + 1);
            leveled = true;
        }
        if (leveled) {
            float missing = getMaxHealth() - getHealth();
            recomputeStats(false);
            setHealth(getMaxHealth() - missing);
            notifyOwner(Component.translatable("message.dirtrobes.level_up", getDisplayName(), getSpectrobeLevel()).withStyle(ChatFormatting.GREEN));
            level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.4f);
            tryEvolve();
        }
    }

    private void tryEvolve() {
        Species s = getSpecies();
        if (s == null || s.evolvesInto() == null || getSpectrobeLevel() < s.evolutionLevel()) return;
        Species next = SpeciesManager.get(s.evolvesInto());
        if (next == null) return;
        Component oldName = getDisplayName();
        setSpecies(next, true);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 60, 0.6, 0.8, 0.6, 0.15);
            server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + getBbHeight() / 2, getZ(), 40, 0.5, 0.8, 0.5, 0.3);
        }
        level().playSound(null, blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.7f, 1.2f);
        level().broadcastEntityEvent(this, EVENT_CRY);
        notifyOwner(Component.translatable("message.dirtrobes.evolved", oldName, Component.translatable(next.translationKey()))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        tryEvolve();
    }

    public void feedMineral(MineralItem mineral, Player player) {
        addEv(mineral.stat(), mineral.grade().ev);
        addXp(mineral.grade().xp);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight(), getZ(), 8, 0.4, 0.3, 0.4, 0.0);
        }
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 1.3f);
    }

    /** Appelé quand ce Spectrobe met un ennemi K.O. */
    public void onKill(LivingEntity victim) {
        int gained = Math.max(10, Math.round(victim.getMaxHealth() * 5));
        addXp(gained);
        MineralItem.Stat ev = MineralItem.Stat.HEALTH;
        if (victim.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) && victim.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 4) {
            ev = MineralItem.Stat.POWER;
        } else if (victim.getArmorValue() >= 4) {
            ev = MineralItem.Stat.DEFENCE;
        } else if (victim.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED) && victim.getAttributeValue(Attributes.MOVEMENT_SPEED) >= 0.3) {
            ev = MineralItem.Stat.SPEED;
        }
        addEv(ev, 2);
    }

    // =====================================================================
    // Comportement
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (getSpeciesId() == null) {
            Species fallback = SpeciesManager.all().stream().findFirst().orElse(null);
            if (fallback != null) setSpecies(fallback, true);
        }
        if (isPenResident()) {
            tickPenResident((ServerLevel) level());
            return;
        }
        if (specialCooldown > 0) specialCooldown--;
        if (isKnockedOut()) {
            getNavigation().stop();
            setTarget(null);
            if (--koTicks <= 0) {
                setFlag(FLAG_KO, false);
                setHealth(getMaxHealth() * 0.5f);
                notifyOwner(Component.translatable("message.dirtrobes.recovered", getDisplayName()));
            }
        }
        setFlag(FLAG_SLEEPING, isOrderedToSit() && level().isNight() && !isKnockedOut());
        setFlag(FLAG_BATTLE, getTarget() != null && getTarget().isAlive() && canFight());
        tickStrike();
        if (getStage() == Stage.CHILD && --searchCooldown <= 0) {
            searchCooldown = 40;
            searchForTreasure();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isPenResident() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (isKnockedOut() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (!level().isClientSide && isTame() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && amount >= getHealth()) {
            boolean result = super.hurt(source, Math.max(0f, getHealth() - 1f));
            knockOut();
            return result;
        }
        return super.hurt(source, amount);
    }

    private void knockOut() {
        setHealth(1f);
        setFlag(FLAG_KO, true);
        koTicks = 1200;
        setTarget(null);
        strikeTarget = null;
        notifyOwner(Component.translatable("message.dirtrobes.knocked_out", getDisplayName()).withStyle(ChatFormatting.RED));
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target == owner) return false;
        if (target instanceof TamableAnimal tamable && tamable.isTame() && tamable.getOwner() == owner) return false;
        if (target instanceof Player other && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(other)) return false;
        return super.wantsToAttack(target, owner);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isPenResident()) {
            if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) describeTo(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isOwnedBy(player) && stack.isEmpty() && player.isShiftKeyDown()) {
            if (!level().isClientSide) toggleSit(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    public void toggleSit(Player player) {
        boolean sit = !isOrderedToSit();
        setOrderedToSit(sit);
        setInSittingPose(sit);
        getNavigation().stop();
        setTarget(null);
        player.displayClientMessage(Component.translatable(sit ? "message.dirtrobes.sit" : "message.dirtrobes.follow", getDisplayName()), true);
    }

    // ----- Recherche de l'enfant (portée de la fiche) : fossiles en jaune, minéraux en bleu -----

    /** Résultat d'une recherche : bloc le plus proche de chaque sorte (null si rien). */
    public record SearchResult(@Nullable BlockPos fossil, @Nullable BlockPos mineral) {}

    private static final DustParticleOptions FOSSIL_SIGNAL = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.2f), 1.4f);
    private static final DustParticleOptions MINERAL_SIGNAL = new DustParticleOptions(new Vector3f(0.3f, 0.65f, 1.0f), 1.4f);

    public int searchRadius() {
        Species s = getSpecies();
        return 4 + 2 * (s == null ? 3 : s.searchRange());
    }

    /** Cherche fossiles et gisements autour du Spectrobe, sans effet visible. */
    public SearchResult scanForTreasure() {
        int radius = searchRadius();
        BlockPos center = blockPosition();
        BlockPos fossil = null, mineral = null;
        double bestFossil = Double.MAX_VALUE, bestMineral = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -6, -radius), center.offset(radius, 6, radius))) {
            BlockState state = level().getBlockState(pos);
            if (state.is(ModRegistry.FOSSIL_STONE.get()) || state.is(ModRegistry.DEEPSLATE_FOSSIL_STONE.get())) {
                double d = pos.distSqr(center);
                if (d < bestFossil) {
                    bestFossil = d;
                    fossil = pos.immutable();
                }
            } else if (state.is(ModRegistry.MINERAL_DEPOSIT.get()) || state.is(ModRegistry.DEEPSLATE_MINERAL_DEPOSIT.get())) {
                double d = pos.distSqr(center);
                if (d < bestMineral) {
                    bestMineral = d;
                    mineral = pos.immutable();
                }
            }
        }
        return new SearchResult(fossil, mineral);
    }

    private void searchForTreasure() {
        if (!(level() instanceof ServerLevel server) || isOrderedToSit() || isKnockedOut()) return;
        if (!(getOwner() instanceof ServerPlayer owner) || owner.distanceToSqr(this) > 20 * 20) return;
        SearchResult result = scanForTreasure();
        if (result.fossil() != null) signal(server, owner, result.fossil(), FOSSIL_SIGNAL);
        if (result.mineral() != null) signal(server, owner, result.mineral(), MINERAL_SIGNAL);
        BlockPos shown = result.fossil() != null ? result.fossil() : result.mineral();
        if (shown != null) {
            int dist = (int) Math.sqrt(shown.distSqr(owner.blockPosition()));
            int dy = shown.getY() - owner.getBlockY();
            Component where = Component.translatable(dy > 1 ? "direction.dirtrobes.above" : dy < -1 ? "direction.dirtrobes.below" : "direction.dirtrobes.level");
            owner.displayClientMessage(Component.translatable(result.fossil() != null ? "message.dirtrobes.fossil_found" : "message.dirtrobes.mineral_found",
                    getDisplayName(), dist, where).withStyle(result.fossil() != null ? ChatFormatting.YELLOW : ChatFormatting.AQUA), true);
            getLookControl().setLookAt(shown.getX() + 0.5, shown.getY() + 0.5, shown.getZ() + 0.5);
            if (tickCount % 120 < 40) level().broadcastEntityEvent(this, EVENT_CRY);
        }
    }

    /** Signal coloré, visible seulement par le dresseur : une colonne au-dessus du bloc et un nuage autour. */
    private static void signal(ServerLevel server, ServerPlayer owner, BlockPos pos, DustParticleOptions color) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        server.sendParticles(owner, color, true, x, y, z, 10, 0.35, 0.35, 0.35, 0.0);
        for (int i = 1; i <= 6; i++) {
            server.sendParticles(owner, color, true, x, y + i * 0.5, z, 1, 0.02, 0.05, 0.02, 0.0);
        }
    }

    // ----- Attaque spéciale (touche R) -----
    public boolean startSpecialAttack(@Nullable LivingEntity lookTarget) {
        if (!canFight() || specialCooldown > 0 || isOrderedToSit()) return false;
        LivingEntity target = getTarget() != null && getTarget().isAlive() ? getTarget() : lookTarget;
        if (target == null || target == getOwner() || !target.isAlive()) return false;
        if (getOwner() != null && !wantsToAttack(target, getOwner())) return false;
        setTarget(target);
        strikeTarget = target;
        strikeTicks = 10;
        Vec3 dir = target.position().subtract(position());
        if (dir.length() > 2.5) {
            Vec3 dash = dir.normalize().scale(Math.min(1.6, dir.length() * 0.25));
            setDeltaMovement(dash.x, 0.35, dash.z);
            hasImpulse = true;
        }
        level().broadcastEntityEvent(this, EVENT_SPECIAL);
        specialCooldown = 30;
        return true;
    }

    private float strikeMultiplier = 2.2f;
    private boolean strikeCombo;

    /** Partie d'une attaque combo : comme l'attaque spéciale, mais ignore le temps de recharge et frappe plus fort. */
    public boolean startComboStrike(LivingEntity target, float multiplier) {
        if (!canFight() || isOrderedToSit() || !target.isAlive()) return false;
        int saved = specialCooldown;
        specialCooldown = 0;
        boolean ok = startSpecialAttack(target);
        if (!ok) {
            specialCooldown = saved;
            return false;
        }
        strikeTarget = target;
        strikeMultiplier = multiplier;
        strikeCombo = true;
        return true;
    }

    private void tickStrike() {
        if (strikeTarget == null) return;
        strikeTicks--;
        if (!strikeTarget.isAlive()) {
            strikeTarget = null;
            return;
        }
        if (distanceTo(strikeTarget) < 3.0 || strikeTicks <= 0) {
            if (distanceTo(strikeTarget) < 4.5) {
                float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * strikeMultiplier;
                strikeTarget.hurt(damageSources().mobAttack(this), damage);
                if (strikeCombo) com.dirtrobes.combat.ComboAttack.splash(this, strikeTarget, damage);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(getProperty().particle(), strikeTarget.getX(), strikeTarget.getY() + strikeTarget.getBbHeight() / 2,
                            strikeTarget.getZ(), 30, 0.4, 0.5, 0.4, 0.08);
                }
                level().playSound(null, strikeTarget.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0f, 0.8f);
            }
            strikeTarget = null;
            strikeMultiplier = 2.2f;
            strikeCombo = false;
        }
    }

    // =====================================================================
    // Fiche (Prizmod), noms, messages
    // =====================================================================

    public net.minecraft.network.chat.MutableComponent natureLine() {
        if (nature.raised == null) return Component.translatable("message.dirtrobes.card.nature_neutral", Component.translatable(nature.translationKey()));
        return Component.translatable("message.dirtrobes.card.nature", Component.translatable(nature.translationKey()),
                Component.translatable("stat.dirtrobes." + nature.raised.id()), Component.translatable("stat.dirtrobes." + nature.lowered.id()));
    }

    public void describeTo(Player player) {
        Species s = getSpecies();
        Property p = getProperty();
        player.sendSystemMessage(Component.literal("")
                .append(getDisplayName().copy().withStyle(ChatFormatting.BOLD))
                .append(Component.literal("  "))
                .append(Component.translatable("message.dirtrobes.card.level", getSpectrobeLevel()))
                .append(Component.literal("  ("))
                .append(Component.translatable(p.translationKey()).withStyle(st -> st.withColor(p.color())))
                .append(Component.literal(" · "))
                .append(Component.translatable(getStage().translationKey()))
                .append(Component.literal(")")));
        player.sendSystemMessage(Component.translatable("message.dirtrobes.card.stats",
                Mth.ceil(getHealth() * hpStat() / Math.max(1f, getMaxHealth())), hpStat(), attackStat(), defenseStat(), speedStat()));
        player.sendSystemMessage(natureLine().withStyle(ChatFormatting.GRAY));
        if (quality < 100) {
            player.sendSystemMessage(Component.translatable("message.dirtrobes.card.quality", quality).withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.translatable("message.dirtrobes.card.ev", evHp, evAtk, evDef, evSpd, totalEv(), StatCalc.MAX_EV_TOTAL)
                .withStyle(ChatFormatting.GRAY));
        int next = StatCalc.xpForLevel(getSpectrobeLevel() + 1);
        if (s != null && s.evolvesInto() != null && SpeciesManager.get(s.evolvesInto()) != null) {
            player.sendSystemMessage(Component.translatable("message.dirtrobes.card.xp_evo", xp, next,
                    Component.translatable(SpeciesManager.get(s.evolvesInto()).translationKey()), s.evolutionLevel()).withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.translatable("message.dirtrobes.card.xp", xp, next).withStyle(ChatFormatting.GRAY));
        }
        if (isKnockedOut()) {
            player.sendSystemMessage(Component.translatable("message.dirtrobes.card.ko", koTicks / 20).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    protected Component getTypeName() {
        ResourceLocation id = getSpeciesId();
        return id == null ? super.getTypeName() : Component.translatable("species." + id.getNamespace() + "." + id.getPath());
    }

    private void notifyOwner(Component message) {
        if (getOwner() instanceof Player owner) owner.sendSystemMessage(message);
    }

    // =====================================================================
    // Animations ponctuelles côté client
    // =====================================================================

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_CRY || id == EVENT_SPECIAL) {
            lastCryTick = tickCount;
        } else {
            super.handleEntityEvent(id);
        }
    }

    // =====================================================================
    // Sauvegarde
    // =====================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Species", this.entityData.get(DATA_SPECIES));
        tag.putInt("SpectrobeLevel", getSpectrobeLevel());
        tag.putInt("Xp", xp);
        tag.putInt("EvHp", evHp);
        tag.putInt("EvAtk", evAtk);
        tag.putInt("EvDef", evDef);
        tag.putInt("EvSpd", evSpd);
        tag.putString("Nature", nature.id());
        tag.putInt("KoTicks", isKnockedOut() ? koTicks : 0);
        tag.putInt("Quality", quality);
        tag.putInt("Color", colorVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_LEVEL, Math.max(1, tag.getInt("SpectrobeLevel")));
        xp = tag.getInt("Xp");
        evHp = tag.getInt("EvHp");
        evAtk = tag.getInt("EvAtk");
        evDef = tag.getInt("EvDef");
        evSpd = tag.getInt("EvSpd");
        nature = tag.contains("Nature") ? Nature.byId(tag.getString("Nature")) : Nature.random(random);
        koTicks = tag.getInt("KoTicks");
        quality = tag.contains("Quality") ? Math.max(50, Math.min(100, tag.getInt("Quality"))) : 100;
        this.entityData.set(DATA_COLOR, (byte) Math.max(0, Math.min(2, tag.getInt("Color"))));
        setFlag(FLAG_KO, koTicks > 0);
        Species s = SpeciesManager.get(ResourceLocation.tryParse(tag.getString("Species")));
        if (s != null) {
            float health = getHealth();
            setSpecies(s, false);
            setHealth(Math.min(health, getMaxHealth()));
        }
    }

    // =====================================================================
    // Pas de reproduction pour l'instant
    // =====================================================================

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    /** Crée un Spectrobe d'une espèce donnée (incubateur, commande). */
    public static SpectrobeEntity create(ServerLevel level, Species species, int spectrobeLevel, @Nullable Player owner, Vec3 pos) {
        return create(level, species, spectrobeLevel, 100, owner, pos);
    }

    /** @param quality qualité des stats en % (50 à 100), voir {@link StatCalc#qualityForIntegrity}. */
    public static SpectrobeEntity create(ServerLevel level, Species species, int spectrobeLevel, int quality, @Nullable Player owner, Vec3 pos) {
        SpectrobeEntity entity = new SpectrobeEntity(ModRegistry.SPECTROBE.get(), level);
        entity.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360f, 0f);
        entity.quality = Math.max(50, Math.min(100, quality));
        entity.nature = Nature.random(level.random);
        entity.setSpecies(species, true);
        entity.setSpectrobeLevel(spectrobeLevel);
        if (owner != null) entity.tame(owner);
        level.addFreshEntity(entity);
        level.broadcastEntityEvent(entity, EVENT_CRY);
        if (owner instanceof ServerPlayer serverOwner) PrizmodTeam.onObtained(serverOwner, entity);
        return entity;
    }
}
