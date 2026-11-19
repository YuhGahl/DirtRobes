package com.dirtrobes.lab;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Délimiteur d'enclos : clic droit sur un terminal pour le choisir, clic droit sur un bloc pour le
 * 1er coin, accroupi + clic droit pour le 2e coin. L'enclos est enregistré dès que les trois sont choisis.
 * Tant qu'on l'a en main, le contour de la zone est affiché.
 */
public class PenMarkerItem extends Item {
    public PenMarkerItem(Properties props) {
        super(props);
    }

    public static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Nullable
    public static BlockPos pos(CompoundTag tag, String key) {
        Optional<BlockPos> p = NbtUtils.readBlockPos(tag, key);
        return p.orElse(null);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        CompoundTag tag = data(stack);
        if (level.getBlockEntity(clicked) instanceof LabTerminalBlockEntity) {
            tag.put("Terminal", NbtUtils.writeBlockPos(clicked));
            player.displayClientMessage(Component.translatable("message.dirtrobes.pen_terminal").withStyle(ChatFormatting.AQUA), true);
        } else if (player.isShiftKeyDown()) {
            tag.put("B", NbtUtils.writeBlockPos(clicked));
            player.displayClientMessage(Component.translatable("message.dirtrobes.pen_corner", 2, clicked.toShortString()), true);
        } else {
            tag.put("A", NbtUtils.writeBlockPos(clicked));
            player.displayClientMessage(Component.translatable("message.dirtrobes.pen_corner", 1, clicked.toShortString()), true);
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);

        BlockPos t = pos(tag, "Terminal"), a = pos(tag, "A"), b = pos(tag, "B");
        if (t != null && a != null && b != null && level.getBlockEntity(t) instanceof LabTerminalBlockEntity terminal) {
            Pen pen = Pen.of(a, b);
            if (!pen.isValidSize()) {
                player.displayClientMessage(Component.translatable("message.dirtrobes.pen_bad_size", pen.sizeX(), pen.sizeY(), pen.sizeZ(),
                        Pen.MAX_XZ, Pen.MAX_Y, Pen.MAX_XZ).withStyle(ChatFormatting.RED), false);
            } else {
                terminal.setPen(pen);
                player.displayClientMessage(Component.translatable("message.dirtrobes.pen_set", pen.sizeX(), pen.sizeY(), pen.sizeZ())
                        .withStyle(ChatFormatting.GREEN), false);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dirtrobes.pen_marker.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dirtrobes.pen_marker.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dirtrobes.pen_marker.3").withStyle(ChatFormatting.GRAY));
    }
}
