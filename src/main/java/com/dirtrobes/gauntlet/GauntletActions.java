package com.dirtrobes.gauntlet;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.team.PrizmodTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Ce que fait le gantelet côté serveur (touches B, Z et P). */
public final class GauntletActions {
    private GauntletActions() {}

    public static final ResourceKey<LootTable> EXTRACTION_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, DirtRobes.id("gameplay/fossil_extraction"));

    /** Gantelet porté avec le Prizmod inséré (sinon message et null). */
    @Nullable
    private static GauntletModules ready(ServerPlayer player) {
        ItemStack gauntlet = GauntletAccess.find(player);
        if (gauntlet.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.gauntlet_none"), true);
            return null;
        }
        GauntletModules m = GauntletItem.modules(gauntlet);
        if (!m.prizmod()) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.gauntlet_no_prizmod").withStyle(ChatFormatting.RED), true);
            return null;
        }
        return m;
    }

    /**
     * L'arme active du joueur : gantelet avec Prizmod, lame déployée (touche B) et main principale vide.
     * L'arme reste dans le gantelet : c'est un effet visuel + ses statistiques appliquées au joueur.
     */
    public static ItemStack activeWeapon(net.minecraft.world.entity.player.Player player) {
        if (!player.getMainHandItem().isEmpty()) return ItemStack.EMPTY;
        ItemStack gauntlet = GauntletAccess.find(player);
        if (gauntlet.isEmpty()) return ItemStack.EMPTY;
        GauntletModules m = GauntletItem.modules(gauntlet);
        if (!m.drawn() || !m.prizmod()) return ItemStack.EMPTY;
        return GauntletWeapon.get(gauntlet);
    }

    /** Touche B : déployer ou ranger la lame. */
    public static boolean toggleWeapon(ServerPlayer player) {
        ItemStack gauntlet = GauntletAccess.find(player);
        if (!gauntlet.isEmpty() && GauntletItem.modules(gauntlet).drawn()) {
            gauntlet.set(ModRegistry.GAUNTLET_MODULES.get(), GauntletItem.modules(gauntlet).withDrawn(false));
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);
            GauntletCombat.syncAttributes(player);
            return true;
        }
        GauntletModules m = ready(player);
        if (m == null) return false;
        if (GauntletWeapon.get(gauntlet).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.gauntlet_no_weapon"), true);
            return false;
        }
        gauntlet.set(ModRegistry.GAUNTLET_MODULES.get(), m.withDrawn(true));
        if (!player.getMainHandItem().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.gauntlet_hand_full"), true);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);
        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.1, player.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
        }
        GauntletCombat.syncAttributes(player);
        return true;
    }

    /** Clic droit main vide avec le Canon déployé : tir d'énergie. */
    public static boolean fireCannon(ServerPlayer player) {
        if (!activeWeapon(player).is(ModRegistry.GAUNTLET_CANNON.get())) return false;
        if (player.getCooldowns().isOnCooldown(ModRegistry.GAUNTLET_CANNON.get())) return false;
        GauntletShot shot = new GauntletShot(player.level(), player);
        Vec3 look = player.getLookAngle();
        shot.shoot(look.x, look.y, look.z, 2.2f, 0.5f);
        player.level().addFreshEntity(shot);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 2.0f);
        player.getCooldowns().addCooldown(ModRegistry.GAUNTLET_CANNON.get(), 10);
        return true;
    }

    /** Anciennes versions : une épée « liée » sortie du gantelet retourne dedans. */
    public static void sheathe(ServerPlayer player, ItemStack gauntlet) {
        ItemStack found = ItemStack.EMPTY;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (GauntletWeapon.isBound(s)) {
                if (found.isEmpty()) found = s.copy();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (!found.isEmpty()) storeBack(player, gauntlet, found);
    }

    public static void storeBack(ServerPlayer player, ItemStack gauntlet, ItemStack weapon) {
        if (!weapon.isEmpty() && GauntletWeapon.get(gauntlet).isEmpty()) GauntletWeapon.set(gauntlet, GauntletWeapon.unmark(weapon));
    }

    /** Vérification régulière (anciennes épées liées). */
    public static void tick(ServerPlayer player) {
        ItemStack gauntlet = GauntletAccess.find(player);
        if (!gauntlet.isEmpty()) sheathe(player, gauntlet);
    }

    /**
     * Touche Z : extrait le fossile de la pierre à fossile visée (5 blocs max). C'est le seul moyen
     * d'obtenir les fossiles : à la pioche, la pierre ne donne que de la pierre.
     */
    public static boolean extract(ServerPlayer player) {
        GauntletModules m = ready(player);
        if (m == null) return false;
        if (!m.extractor()) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.gauntlet_no_extractor"), true);
            return false;
        }
        Vec3 eye = player.getEyePosition();
        HitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(5)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return false;
        return extractAt(player, bhr.getBlockPos());
    }

    public static boolean extractAt(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        boolean deep = state.is(ModRegistry.DEEPSLATE_FOSSIL_STONE.get());
        if (!deep && !state.is(ModRegistry.FOSSIL_STONE.get())) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.gauntlet_not_fossil"), true);
            return false;
        }
        if (player.getCooldowns().isOnCooldown(ModRegistry.GAUNTLET.get())) return false;
        player.getCooldowns().addCooldown(ModRegistry.GAUNTLET.get(), 20);
        level.setBlock(pos, deep ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), 3);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(EXTRACTION_LOOT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        for (ItemStack drop : table.getRandomItems(params)) {
            if (!player.getInventory().add(drop)) player.drop(drop, false);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                30, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("message.dirtrobes.gauntlet_extracted").withStyle(ChatFormatting.YELLOW), true);
        return true;
    }

    /** Touche P : écran d'équipe du Prizmod, si le Prizmod est dans le gantelet. */
    public static void openTeam(ServerPlayer player) {
        if (ready(player) != null) PrizmodTeam.sync(player, true);
    }
}
