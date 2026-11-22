package com.dirtrobes.entity;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Créature dessinée par le moteur de rendu maison (modèle Bedrock + poser), comme les Spectrobes
 * et les Krawl. Le moteur trouve le resolver « &lt;espèce&gt; », sinon « default:&lt;stade&gt; ».
 */
public interface BedrockPosable {
    @Nullable
    ResourceLocation getSpeciesId();

    /** Clé du resolver par défaut (child, adult, evolved, krawl…). */
    String resolverStage();

    /** Couleur RGB utilisée pour teinter le modèle provisoire et ses parties lumineuses. */
    int tintColor();

    /** À terre : K.O. ou en train de mourir (animation « faint »). */
    boolean isDown();

    boolean isPoseSleeping();

    boolean isInBattle();

    /** Tick client du dernier cri / attaque (animation ponctuelle « cry »). */
    int lastCryTick();

    @Nullable
    Object clientAnimState();

    /** En vol (espèce volante, pas au sol) : le moteur cherche les poses « FLY » puis « HOVER ». */
    default boolean isFlyingNow() {
        return false;
    }

    /** Sur l'épaule d'un joueur : le moteur cherche la pose « SHOULDER » (ou SHOULDER_LEFT/RIGHT façon Cobblemon). */
    default boolean isOnShoulder() {
        return false;
    }

    /** Étiquette affichée au-dessus de la créature quand le joueur la vise (nom + niveau), ou null. */
    @Nullable
    default net.minecraft.network.chat.Component nameTag() {
        return null;
    }

    /** Couleur Chroma : 0 = normale, 1 = spéciale 1, 2 = spéciale 2. */
    default int colorVariant() {
        return 0;
    }

    void setClientAnimState(Object state);
}
