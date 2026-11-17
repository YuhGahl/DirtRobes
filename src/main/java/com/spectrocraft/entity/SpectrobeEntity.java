package com.spectrocraft.entity;

import com.spectrocraft.item.MineralItem;
import com.spectrocraft.registry.ModRegistry;
import com.spectrocraft.species.Property;
import com.spectrocraft.species.Species;
import com.spectrocraft.species.SpeciesManager;
import com.spectrocraft.species.Stage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Le Spectrobe. Une seule entité pour toutes les espèces : l'espèce, le niveau, la propriété
 * et le stade sont des données synchronisées (même approche que Cobblemon).
 */
public class SpectrobeEntity extends TamableAnimal {
    private static final EntityDataAccessor<String> DATA_SPECIES = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PROPERTY = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_STAGE = SynchedEntityData.defineId(SpectrobeEntity.class, EntityDataSerializers.BYTE);
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
    private int koTicks;
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
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.1, 8.0f, 2.5f));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
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

    // =====================================================================
    // Espèce, propriété, stade
    // =====================================================================

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
        return getStage().fights() && !isKnockedOut() && !isInSittingPose();
    }

    // =====================================================================
    // Stats (façon Pokémon, sans IV)
    // =====================================================================

    public int hpStat() {
        Species s = getSpecies();
        return s == null ? 20 : StatCalc.hp(s.baseHp(), evHp, getSpectrobeLevel());
    }

    public int attackStat() {
        Species s = getSpecies();
        return s == null ? 10 : StatCalc.other(s.baseAttack(), evAtk, getSpectrobeLevel());
    }

    public int defenseStat() {
        Species s = getSpecies();
        return s == null ? 10 : StatCalc.other(s.baseDefense(), evDef, getSpectrobeLevel());
    }

    public void recomputeStats(boolean fullHeal) {
        Species s = getSpecies();
        if (s == null || level().isClientSide) return;
        setBase(Attributes.MAX_HEALTH, StatCalc.toMaxHealth(hpStat()));
        setBase(Attributes.ATTACK_DAMAGE, StatCalc.toAttackDamage(attackStat()));
        setBase(Attributes.ARMOR, StatCalc.toArmor(defenseStat()));
        setBase(Attributes.SCALE, s.scale());
        if (fullHeal) setHealth(getMaxHealth());
        else setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    public int totalEv() {
        return evHp + evAtk + evDef;
    }

    public void addEv(MineralItem.Stat stat, int amount) {
        int room = StatCalc.MAX_EV_TOTAL - totalEv();
        amount = Math.min(amount, room);
        if (amount <= 0) return;
        switch (stat) {
            case HEALTH -> evHp = Math.min(StatCalc.MAX_EV_PER_STAT, evHp + amount);
            case POWER -> evAtk = Math.min(StatCalc.MAX_EV_PER_STAT, evAtk + amount);
            case DEFENCE -> evDef = Math.min(StatCalc.MAX_EV_PER_STAT, evDef + amount);
        }
        recomputeStats(false);
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
            notifyOwner(Component.translatable("message.spectrocraft.level_up", getDisplayName(), getSpectrobeLevel()).withStyle(ChatFormatting.GREEN));
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
        notifyOwner(Component.translatable("message.spectrocraft.evolved", oldName, Component.translatable(next.translationKey()))
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
        if (specialCooldown > 0) specialCooldown--;
        if (isKnockedOut()) {
            getNavigation().stop();
            setTarget(null);
            if (--koTicks <= 0) {
                setFlag(FLAG_KO, false);
                setHealth(getMaxHealth() * 0.5f);
                notifyOwner(Component.translatable("message.spectrocraft.recovered", getDisplayName()));
            }
        }
        setFlag(FLAG_SLEEPING, isOrderedToSit() && level().isNight() && !isKnockedOut());
        setFlag(FLAG_BATTLE, getTarget() != null && getTarget().isAlive() && canFight());
        tickStrike();
        if (getStage() == Stage.CHILD && --searchCooldown <= 0) {
            searchCooldown = 40;
            searchForFossils();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
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
        notifyOwner(Component.translatable("message.spectrocraft.knocked_out", getDisplayName()).withStyle(ChatFormatting.RED));
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
        player.displayClientMessage(Component.translatable(sit ? "message.spectrocraft.sit" : "message.spectrocraft.follow", getDisplayName()), true);
    }

    // ----- Recherche de fossiles par l'enfant (portée de la fiche) -----
    private void searchForFossils() {
        if (!(level() instanceof ServerLevel server) || isOrderedToSit() || isKnockedOut()) return;
        if (!(getOwner() instanceof ServerPlayer owner) || owner.distanceToSqr(this) > 20 * 20) return;
        Species s = getSpecies();
        int radius = 4 + 2 * (s == null ? 3 : s.searchRange());
        BlockPos center = blockPosition();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        int shown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -6, -radius), center.offset(radius, 6, radius))) {
            BlockState state = server.getBlockState(pos);
            if (state.is(ModRegistry.FOSSIL_STONE.get()) || state.is(ModRegistry.DEEPSLATE_FOSSIL_STONE.get())) {
                if (shown++ < 8) {
                    server.sendParticles(owner, ParticleTypes.GLOW, true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                }
                double d = pos.distSqr(center);
                if (d < best) {
                    best = d;
                    nearest = pos.immutable();
                }
            }
        }
        if (nearest != null) {
            owner.displayClientMessage(Component.translatable("message.spectrocraft.fossil_found", getDisplayName(), (int) Math.sqrt(best))
                    .withStyle(ChatFormatting.YELLOW), true);
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
        specialCooldown = 100;
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
                float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.2f;
                strikeTarget.hurt(damageSources().mobAttack(this), damage);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(getProperty().particle(), strikeTarget.getX(), strikeTarget.getY() + strikeTarget.getBbHeight() / 2,
                            strikeTarget.getZ(), 30, 0.4, 0.5, 0.4, 0.08);
                }
                level().playSound(null, strikeTarget.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0f, 0.8f);
            }
            strikeTarget = null;
        }
    }

    // =====================================================================
    // Fiche (Prizmod), noms, messages
    // =====================================================================

    public void describeTo(Player player) {
        Species s = getSpecies();
        Property p = getProperty();
        player.sendSystemMessage(Component.literal("")
                .append(getDisplayName().copy().withStyle(ChatFormatting.BOLD))
                .append(Component.literal("  "))
                .append(Component.translatable("message.spectrocraft.card.level", getSpectrobeLevel()))
                .append(Component.literal("  ("))
                .append(Component.translatable(p.translationKey()).withStyle(st -> st.withColor(p.color())))
                .append(Component.literal(" · "))
                .append(Component.translatable(getStage().translationKey()))
                .append(Component.literal(")")));
        player.sendSystemMessage(Component.translatable("message.spectrocraft.card.stats",
                Mth.ceil(getHealth() * hpStat() / Math.max(1f, getMaxHealth())), hpStat(), attackStat(), defenseStat()));
        player.sendSystemMessage(Component.translatable("message.spectrocraft.card.ev", evHp, evAtk, evDef, totalEv(), StatCalc.MAX_EV_TOTAL)
                .withStyle(ChatFormatting.GRAY));
        int next = StatCalc.xpForLevel(getSpectrobeLevel() + 1);
        if (s != null && s.evolvesInto() != null && SpeciesManager.get(s.evolvesInto()) != null) {
            player.sendSystemMessage(Component.translatable("message.spectrocraft.card.xp_evo", xp, next,
                    Component.translatable(SpeciesManager.get(s.evolvesInto()).translationKey()), s.evolutionLevel()).withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.translatable("message.spectrocraft.card.xp", xp, next).withStyle(ChatFormatting.GRAY));
        }
        if (isKnockedOut()) {
            player.sendSystemMessage(Component.translatable("message.spectrocraft.card.ko", koTicks / 20).withStyle(ChatFormatting.RED));
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
        tag.putInt("KoTicks", isKnockedOut() ? koTicks : 0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_LEVEL, Math.max(1, tag.getInt("SpectrobeLevel")));
        xp = tag.getInt("Xp");
        evHp = tag.getInt("EvHp");
        evAtk = tag.getInt("EvAtk");
        evDef = tag.getInt("EvDef");
        koTicks = tag.getInt("KoTicks");
        setFlag(FLAG_KO, koTicks > 0);
        Species s = SpeciesManager.get(ResourceLocation.tryParse(tag.getString("Species")));
        if (s != null) {
            float health = getHealth();
            setSpecies(s, false);
            setHealth(Math.min(health, getMaxHealth()));
        }
    }

    // =====================================================================
    // Pas de reproduction en v0.1
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
        SpectrobeEntity entity = new SpectrobeEntity(ModRegistry.SPECTROBE.get(), level);
        entity.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360f, 0f);
        entity.setSpecies(species, true);
        entity.setSpectrobeLevel(spectrobeLevel);
        if (owner != null) entity.tame(owner);
        level.addFreshEntity(entity);
        level.broadcastEntityEvent(entity, EVENT_CRY);
        return entity;
    }
}
