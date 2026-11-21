package com.dirtrobes.gauntlet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Arme de module du gantelet : Lame (corps à corps), Poing (rapide, recul) ou Canon (tirs d'énergie).
 * Elle vit dans le gantelet et n'en sort que dans la main (touche B). Incassable.
 */
public class GauntletWeaponItem extends Item {
    private final String kind;
    @org.jetbrains.annotations.Nullable
    private final WeaponSpec spec;

    public GauntletWeaponItem(String kind, Properties props) {
        this(kind, null, props);
    }

    /** Arme alimentée par un Spectrobe évolué (Spectrobes Origins). */
    public GauntletWeaponItem(WeaponSpec spec, Properties props) {
        this(spec.kind(), spec, props);
    }

    private GauntletWeaponItem(String kind, @org.jetbrains.annotations.Nullable WeaponSpec spec, Properties props) {
        super(props.stacksTo(1).attributes(attributes(kind, spec)).component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                new net.minecraft.world.item.component.Unbreakable(false)));
        this.kind = kind;
        this.spec = spec;
    }

    public String kind() {
        return kind;
    }

    @org.jetbrains.annotations.Nullable
    public WeaponSpec spec() {
        return spec;
    }

    public WeaponEffect effect() {
        return spec == null ? WeaponEffect.NONE : spec.effect();
    }

    /** Dégâts d'un tir de canon. */
    public float shotDamage() {
        return spec != null && "cannon".equals(kind) ? spec.damage() : GauntletShot.DAMAGE;
    }

    private static ItemAttributeModifiers attributes(String kind, @org.jetbrains.annotations.Nullable WeaponSpec spec) {
        double damage = spec != null && !"cannon".equals(kind) ? spec.damage() : switch (kind) {
            case "blade" -> 6.0;
            case "fist" -> 4.0;
            default -> 1.0;
        };
        double speed = spec != null && !"cannon".equals(kind) ? spec.speed() : switch (kind) {
            case "blade" -> -2.4;
            case "fist" -> -1.2;
            default -> -2.0;
        };
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(ResourceLocation.fromNamespaceAndPath("dirtrobes", "gauntlet_knockback"),
                        "fist".equals(kind) && spec == null ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** Canon : clic droit pour tirer. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!"cannon".equals(kind)) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            GauntletShot shot = new GauntletShot(level, player);
            Vec3 look = player.getLookAngle();
            shot.shoot(look.x, look.y, look.z, 2.2f, 0.5f);
            level.addFreshEntity(shot);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 2.0f);
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    }


}
