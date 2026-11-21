package com.dirtrobes.client.compat;

import com.dirtrobes.gauntlet.GauntletActions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class ClientCompat {
    private ClientCompat() {}

    public static ItemStack localWeapon() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.isSameThread()) return ItemStack.EMPTY;
        return GauntletActions.activeWeapon(mc.player);
    }
}
