package com.dirtrobes.mixin.bettercombat;

import com.dirtrobes.compat.BetterCombatCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Délimite le traitement des touches (attaque) pour le crochet du registre d'armes de Better Combat. */
@Mixin(Minecraft.class)
public abstract class MinecraftKeysMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"), require = 1)
    private void dirtrobes$keysStart(CallbackInfo ci) {
        BetterCombatCompat.handlingKeys = true;
    }

    @Inject(method = "handleKeybinds", at = @At("RETURN"), require = 1)
    private void dirtrobes$keysEnd(CallbackInfo ci) {
        BetterCombatCompat.handlingKeys = false;
    }
}
