package com.dirtrobes.compat.jade;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.block.IncubatorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

/** Incubateur : ON/OFF, fossile en attente, barre d'éclosion (comme le four). */
public enum IncubatorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = DirtRobes.id("incubator");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof IncubatorBlockEntity be)) return;
        data.putBoolean("Active", be.isBusy());
        data.putInt("Progress", be.progress());
        data.putInt("Integrity", be.integrity());
        if (be.property() != null) data.putString("Property", be.property().id());
        data.putFloat("Speed", be.speed());
        data.putInt("Energy", be.energy().getEnergyStored());
        ItemStack waiting = be.inventory().getItem(0);
        if (!waiting.isEmpty()) data.put("Waiting", waiting.save(accessor.getLevel().registryAccess()));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("Active")) return;
        boolean active = data.getBoolean("Active");
        tooltip.add(Component.translatable(active ? "jade.dirtrobes.incubator.on" : "jade.dirtrobes.incubator.off")
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED));
        IElementHelper helper = IElementHelper.get();
        if (data.contains("Waiting")) {
            ItemStack waiting = ItemStack.parseOptional(accessor.getLevel().registryAccess(), data.getCompound("Waiting"));
            if (!waiting.isEmpty()) {
                tooltip.add(helper.smallItem(waiting));
                tooltip.append(Component.translatable("jade.dirtrobes.incubator.waiting", waiting.getHoverName()));
            }
        }
        if (active) {
            float ratio = Math.min(1f, data.getInt("Progress") / (float) IncubatorBlockEntity.DURATION);
            float spd = Math.max(1f, data.getFloat("Speed"));
            int seconds = Math.max(0, Math.round((IncubatorBlockEntity.DURATION - data.getInt("Progress")) / 20f / spd));
            Component label = Component.translatable("jade.dirtrobes.incubator.progress", Math.round(ratio * 100), seconds);
            tooltip.add(helper.progress(ratio, label, helper.progressStyle().color(0xFF35C8D8, 0xFF1A6A76).textColor(0xFFFFFFFF),
                    BoxStyle.getNestedBox(), true));
            tooltip.add(Component.translatable("jade.dirtrobes.incubator.integrity", data.getInt("Integrity")).withStyle(ChatFormatting.GRAY));
            float speed = data.getFloat("Speed");
            if (speed > 1.01f) {
                tooltip.add(Component.translatable("jade.dirtrobes.incubator.speed", String.format(java.util.Locale.ROOT, "%.1f", speed))
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
