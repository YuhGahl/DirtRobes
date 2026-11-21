package com.dirtrobes.client.compat;

import com.dirtrobes.DirtRobes;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Animations du joueur avec Player Animator (classe chargée seulement si le mod est installé). */
public final class PlayerAnimCompat {
    private PlayerAnimCompat() {}

    private static final ResourceLocation LAYER = DirtRobes.id("actions");

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1500, player -> new ModifierLayer<>());
    }

    @SuppressWarnings("unchecked")
    public static void play(int entityId, String name) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(entityId) instanceof AbstractClientPlayer player)) return;
        IAnimation data = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (!(data instanceof ModifierLayer<?> raw)) return;
        IPlayable anim = PlayerAnimationRegistry.getAnimation(DirtRobes.id(name));
        if (!(anim instanceof KeyframeAnimation keyframes)) return;
        ((ModifierLayer<IAnimation>) raw).replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.INOUTSINE),
                new KeyframeAnimationPlayer(keyframes));
    }
}
