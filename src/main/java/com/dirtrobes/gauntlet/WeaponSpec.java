package com.dirtrobes.gauntlet;

import java.util.List;

/**
 * Armes du gantelet alimentées par un Spectrobe évolué, reprises de Spectrobes Origins (table ItemTable du disque).
 * Les épées, lances et haches du jeu deviennent des lames ; les gants des poings ; blasters, zappers et canons des canons.
 *
 * @param species   espèce évoluée qui alimente l'arme (chemin de l'identifiant)
 * @param shortName nom court du jeu (Zaza, Ryga…)
 * @param kind      blade, fist ou cannon (module du gantelet)
 * @param style     sword, lance, axe, glove, shooter, laser ou bazooka (enchaînement Better Combat, rendu)
 * @param damage    dégâts ajoutés (corps à corps) ou dégâts d'un tir (canon)
 * @param speed     vitesse d'attaque (corps à corps)
 * @param shots     nombre de tirs par salve (canon)
 * @param spread    écart en degrés entre les tirs d'une salve
 */
public record WeaponSpec(String species, String shortName, String kind, String style, WeaponEffect effect,
                         float damage, float speed, int shots, float spread) {

    public String itemId() {
        return kind + "_" + species;
    }

    public static final List<WeaponSpec> ALL = List.of(
            new WeaponSpec("zazanero", "Zaza", "blade", "sword", WeaponEffect.BURN_KNOCKBACK, 6f, -2.4f, 0, 0f),
            new WeaponSpec("rydrake", "Ryga", "blade", "sword", WeaponEffect.FREEZE, 5f, -1.8f, 0, 0f),
            new WeaponSpec("pepethraz", "Pepe", "blade", "sword", WeaponEffect.NONE, 5f, -1.4f, 0, 0f),
            new WeaponSpec("butobasu", "Buto", "blade", "sword", WeaponEffect.ANTI_AIR, 6f, -2.4f, 0, 0f),
            new WeaponSpec("yagizora", "Yagi", "blade", "sword", WeaponEffect.NONE, 5f, -1.6f, 0, 0f),
            new WeaponSpec("torgallup", "Torga", "blade", "lance", WeaponEffect.LAUNCH, 6f, -2.6f, 0, 0f),
            new WeaponSpec("zenipyra", "Zeni", "blade", "lance", WeaponEffect.NONE, 8f, -2.8f, 0, 0f),
            new WeaponSpec("mayazerra", "Maya", "blade", "lance", WeaponEffect.DOUBLE, 5f, -2.4f, 0, 0f),
            new WeaponSpec("zappizor", "Zapp", "blade", "lance", WeaponEffect.AREA_STUN, 6f, -2.6f, 0, 0f),
            new WeaponSpec("komadoros", "Koma", "blade", "axe", WeaponEffect.NONE, 8f, -2.8f, 0, 0f),
            new WeaponSpec("shakoblad", "Shaka", "blade", "axe", WeaponEffect.NONE, 7f, -2.2f, 0, 0f),
            new WeaponSpec("spikanor", "Spika", "blade", "axe", WeaponEffect.SLOW, 8f, -2.9f, 0, 0f),
            new WeaponSpec("dongiga", "Dongiga", "blade", "axe", WeaponEffect.LAUNCH, 9f, -3.0f, 0, 0f),
            new WeaponSpec("bombzar", "Bombza", "blade", "axe", WeaponEffect.KNOCKBACK, 9f, -3.0f, 0, 0f),
            new WeaponSpec("totsuzen", "Totsu", "fist", "glove", WeaponEffect.BURN, 5f, -1.2f, 0, 0f),
            new WeaponSpec("ukazeer", "Uka", "fist", "glove", WeaponEffect.LAUNCH, 4f, -1.0f, 0, 0f),
            new WeaponSpec("zuwakrid", "Zuwa", "fist", "glove", WeaponEffect.FREEZE, 5f, -1.2f, 0, 0f),
            new WeaponSpec("shimadoros", "Shima", "fist", "glove", WeaponEffect.SLOW, 6f, -1.6f, 0, 0f),
            new WeaponSpec("totegarda", "Tote", "fist", "glove", WeaponEffect.ANTI_AIR, 5f, -1.2f, 0, 0f),
            new WeaponSpec("pagadori", "Paga", "cannon", "shooter", WeaponEffect.FREEZE, 4f, 0f, 3, 12f),
            new WeaponSpec("florami", "Flora", "cannon", "shooter", WeaponEffect.POISON, 4f, 0f, 3, 12f),
            new WeaponSpec("aobasar", "Aoba", "cannon", "shooter", WeaponEffect.LAUNCH, 3f, 0f, 5, 10f),
            new WeaponSpec("flamerax", "Flame", "cannon", "shooter", WeaponEffect.BURN, 3f, 0f, 6, 3f),
            new WeaponSpec("optoger", "Opto", "cannon", "shooter", WeaponEffect.STUN, 4f, 0f, 3, 12f),
            new WeaponSpec("leozar", "Leo", "cannon", "laser", WeaponEffect.BURN, 6f, 0f, 1, 0f),
            new WeaponSpec("gorberus", "Gorb", "cannon", "laser", WeaponEffect.SLOW, 5f, 0f, 2, 4f),
            new WeaponSpec("mizusar", "Mizu", "cannon", "laser", WeaponEffect.FREEZE, 6f, 0f, 1, 0f),
            new WeaponSpec("hikolossa", "Hiko", "cannon", "bazooka", WeaponEffect.EXPLOSIVE, 8f, 0f, 1, 0f),
            new WeaponSpec("gokazoa", "Goka", "cannon", "bazooka", WeaponEffect.NONE, 10f, 0f, 1, 0f),
            new WeaponSpec("daidongu", "Dongu", "cannon", "bazooka", WeaponEffect.POISON, 8f, 0f, 1, 0f),
            new WeaponSpec("scarazook", "Scara", "cannon", "bazooka", WeaponEffect.SLOW, 8f, 0f, 1, 0f)
    );

    public static WeaponSpec forSpecies(String speciesPath) {
        for (WeaponSpec s : ALL) if (s.species.equals(speciesPath)) return s;
        return null;
    }
}
