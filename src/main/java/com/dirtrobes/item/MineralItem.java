package com.dirtrobes.item;

import java.util.Locale;

/**
 * Stats que touchent les minéraux, gemmes et natures. Les anciens minéraux « S1 » (Puissance / Défense / Santé,
 * grades C à A+) ont été retirés : ce sont les minéraux d'Origins qui donnent EV et expérience
 * (voir {@link OriginsMineralItem}). Les objets restés dans les mondes sont convertis (alias du registre).
 */
public final class MineralItem {
    private MineralItem() {}

    public enum Stat {
        POWER, DEFENCE, HEALTH, SPEED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
