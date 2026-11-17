package com.dirtrobes.krawl;

import com.dirtrobes.entity.BedrockPosable;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.entity.StatCalc;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.species.Property;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Les Krawl : ennemis du mod. Une seule entité pour toutes les espèces (données synchronisées),
 * comme pour les Spectrobes. Ils attaquent les joueurs et les Spectrobes.
 */
public class KrawlEntity extends Monster implements BedrockPosable {
    private static final EntityDataAccessor<String> DATA_SPECIES = SynchedEntityData.defineId(KrawlEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(KrawlEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PROPERTY = SynchedEntityData.defineId(KrawlEntity.class, EntityDataSerializers.BYTE);

    public static final byte EVENT_CRY = 110;

    private int sporeCooldown = 80;
    private int rallyCooldown = 20;
    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(Component.empty(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(false);
    @Nullable
    private Object clientAnimState;
    private int lastCryTick = -1000;

    public KrawlEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPECIES, "");
        builder.define(DATA_LEVEL, 1);
        builder.define(DATA_PROPERTY, (byte) Property.ECLIPSE.ordinal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, KrawlEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, SpectrobeEntity.class, true,
                s -> s instanceof SpectrobeEntity sp && !sp.isKnockedOut()));
    }

    // =====================================================================
    // Espèce et stats
    // =====================================================================

    @Override
    @Nullable
    public ResourceLocation getSpeciesId() {
        String s = this.entityData.get(DATA_SPECIES);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }

    @Nullable
    public KrawlSpecies getSpecies() {
        return KrawlManager.get(getSpeciesId());
    }

    public Property getProperty() {
        return Property.byOrdinal(this.entityData.get(DATA_PROPERTY));
    }

    public int getKrawlLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    public boolean isLeader() {
        KrawlSpecies k = getSpecies();
        return k != null && k.leader();
    }

    public void setSpecies(KrawlSpecies species, int level) {
        this.entityData.set(DATA_SPECIES, species.id().toString());
        this.entityData.set(DATA_PROPERTY, (byte) species.property().ordinal());
        this.entityData.set(DATA_LEVEL, Mth.clamp(level, 1, StatCalc.MAX_LEVEL));
        recomputeStats();
        setHealth(getMaxHealth());
    }

    private void recomputeStats() {
        KrawlSpecies k = getSpecies();
        if (k == null || level().isClientSide) return;
        int lvl = getKrawlLevel();
        setBase(Attributes.MAX_HEALTH, StatCalc.toMaxHealth(StatCalc.hp(k.baseHp(), 0, lvl)));
        setBase(Attributes.ATTACK_DAMAGE, StatCalc.toAttackDamage(StatCalc.other(k.baseAttack(), 0, lvl)));
        setBase(Attributes.ARMOR, StatCalc.toArmor(StatCalc.other(k.baseDefense(), 0, lvl)));
        setBase(Attributes.MOVEMENT_SPEED, k.speed());
        setBase(Attributes.SCALE, k.scale());
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        if (getSpeciesId() == null) {
            KrawlSpecies k = KrawlManager.random(level.getRandom());
            if (k != null) setSpecies(k, Mth.nextInt(level.getRandom(), k.minLevel(), k.maxLevel()));
        }
        return data;
    }

    /** Fait apparaître un Krawl d'une espèce donnée (vortex, commande, spores). */
    public static KrawlEntity create(ServerLevel level, KrawlSpecies species, int krawlLevel, Vec3 pos) {
        KrawlEntity entity = new KrawlEntity(ModRegistry.KRAWL.get(), level);
        entity.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360f, 0f);
        entity.setSpecies(species, krawlLevel);
        level.addFreshEntity(entity);
        return entity;
    }

    // =====================================================================
    // Comportement
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 6 == 0) {
                level().addParticle(ParticleTypes.SMOKE, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.02, 0);
            }
            return;
        }
        if (getSpeciesId() == null) {
            KrawlSpecies k = KrawlManager.random(random);
            if (k != null) setSpecies(k, Mth.nextInt(random, k.minLevel(), k.maxLevel()));
        }
        KrawlSpecies k = getSpecies();
        if (k != null && k.leader()) {
            bossEvent.setName(Component.translatable("message.dirtrobes.krawl_leader", Component.translatable(k.translationKey()), getKrawlLevel()));
            bossEvent.setProgress(getHealth() / getMaxHealth());
            if (--rallyCooldown <= 0) {
                rallyCooldown = 40;
                rally((ServerLevel) level());
            }
        }
        if (k != null && k.isSpore() && --sporeCooldown <= 0) {
            sporeCooldown = 100 + random.nextInt(60);
            releaseSpores((ServerLevel) level());
        }
    }

    /** Krawl chef : renforce les Krawl proches (force et résistance). */
    private void rally(ServerLevel server) {
        List<KrawlEntity> allies = server.getEntitiesOfClass(KrawlEntity.class, getBoundingBox().inflate(12), k -> k != this && k.isAlive());
        for (KrawlEntity ally : allies) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, true));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, true));
            server.sendParticles(ParticleTypes.WITCH, ally.getX(), ally.getY() + ally.getBbHeight(), ally.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (isLeader()) bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    /** Krawl « spore » : soigne les Krawl proches et peut en faire apparaître un nouveau. */
    private void releaseSpores(ServerLevel server) {
        List<KrawlEntity> allies = server.getEntitiesOfClass(KrawlEntity.class, getBoundingBox().inflate(8), k -> k != this && k.isAlive());
        for (KrawlEntity ally : allies) ally.heal(ally.getMaxHealth() * 0.15f);
        server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + getBbHeight(), getZ(), 30, 2.5, 1.0, 2.5, 0.0);
        level().broadcastEntityEvent(this, EVENT_CRY);
        if (getTarget() != null && allies.size() < 4 && random.nextFloat() < 0.35f) {
            KrawlSpecies spawn = KrawlManager.all().stream().filter(s -> !s.isSpore()).findAny().orElse(null);
            if (spawn != null) {
                Vec3 pos = position().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                create(server, spawn, Math.max(1, getKrawlLevel() - 2), pos);
                server.sendParticles(ParticleTypes.SQUID_INK, pos.x, pos.y + 0.5, pos.z, 20, 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) level().broadcastEntityEvent(this, EVENT_CRY);
        return hit;
    }

    @Override
    protected Component getTypeName() {
        KrawlSpecies k = getSpecies();
        return k == null ? super.getTypeName() : Component.translatable(k.translationKey());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SCULK_CLICKING;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SCULK_SHRIEKER_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SCULK_CATALYST_BLOOM;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_CRY) lastCryTick = tickCount;
        else super.handleEntityEvent(id);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + getBbHeight() / 2, getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            server.playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0f, 0.8f);
        }
    }

    // ----- BedrockPosable -----
    @Override
    public String resolverStage() {
        return "krawl";
    }

    @Override
    public int tintColor() {
        return getProperty().color();
    }

    @Override
    public boolean isDown() {
        return deathTime > 0;
    }

    @Override
    public boolean isPoseSleeping() {
        return false;
    }

    @Override
    public boolean isInBattle() {
        return getTarget() != null;
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

    // ----- Sauvegarde -----
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Species", this.entityData.get(DATA_SPECIES));
        tag.putInt("KrawlLevel", getKrawlLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        KrawlSpecies k = KrawlManager.get(ResourceLocation.tryParse(tag.getString("Species")));
        if (k != null) {
            float health = getHealth();
            setSpecies(k, Math.max(1, tag.getInt("KrawlLevel")));
            setHealth(Math.min(health, getMaxHealth()));
        }
    }
}
