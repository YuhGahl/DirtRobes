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
 * Arme sortie du gantelet : liée au gantelet, elle disparaît si on la lâche, si on change d'objet en main
 * ou si on la range (touche B). Lame (corps à corps), Poing (rapide, recul) ou Canon (tirs d'énergie).
 */
public class GauntletWeaponItem extends Item {
    private final String kind;

    public GauntletWeaponItem(String kind, Properties props) {
        super(props.stacksTo(1).attributes(attributes(kind)));
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }

    private static ItemAttributeModifiers attributes(String kind) {
        double damage = switch (kind) {
            case "blade" -> 6.0;
            case "fist" -> 4.0;
            default -> 1.0;
        };
        double speed = switch (kind) {
            case "blade" -> -2.4;
            case "fist" -> -1.2;
            default -> -2.0;
        };
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(ResourceLocation.fromNamespaceAndPath("dirtrobes", "gauntlet_knockback"),
                        "fist".equals(kind) ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
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

    /** Disparaît dès qu'elle n'est plus dans la main principale. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && !(selected && player.getMainHandItem() == stack)) {
            stack.setCount(0);
        }
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        item.setCount(0);
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dirtrobes.gauntlet_weapon").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean isBound(ItemStack stack) {
        return stack.getItem() instanceof GauntletWeaponItem;
    }

    static void markUnbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
    }
}
