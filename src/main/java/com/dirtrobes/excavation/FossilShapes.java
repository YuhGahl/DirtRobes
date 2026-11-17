package com.dirtrobes.excavation;

import com.dirtrobes.species.Property;
import net.minecraft.util.RandomSource;

/** Silhouettes (originales) des fossiles à dégager, par propriété. '#' = fossile. */
public final class FossilShapes {
    private FossilShapes() {}

    // Corona : griffe recourbée
    private static final String[] CLAW = {
            "......##",
            ".....##.",
            "....###.",
            "..####..",
            ".####...",
            "####....",
            "##......",
    };
    // Corona : vertèbres en arc
    private static final String[] SPINE = {
            "..#...#...",
            ".###.###..",
            "##########",
            ".###.###.#",
            "..#...#...",
    };
    // Aurora : feuille fossilisée
    private static final String[] LEAF = {
            "....##....",
            "..######..",
            ".###.####.",
            "####.#####",
            ".###.####.",
            "..######..",
            "....##....",
            "....#.....",
    };
    // Aurora : graine à vrilles
    private static final String[] SEED = {
            ".#....#.",
            "..#..#..",
            "..####..",
            ".######.",
            ".######.",
            "..####..",
            "...##...",
    };
    // Flash : coquille spiralée
    private static final String[] SHELL = {
            "..#####..",
            ".##...##.",
            "##..#..##",
            "#..###..#",
            "#...#..##",
            "##....##.",
            ".######..",
    };
    // Flash : nageoire
    private static final String[] FIN = {
            "#.........",
            "##........",
            "####......",
            "#######...",
            "##########",
            "#######...",
            "####......",
    };
    // Eclipse : crâne à cornes
    private static final String[] SKULL = {
            "#........#",
            "##..##..##",
            ".########.",
            ".##.##.##.",
            ".########.",
            "..######..",
            "...#..#...",
    };
    // Eclipse : croc
    private static final String[] FANG = {
            "########",
            ".######.",
            "..####..",
            "..####..",
            "...##...",
            "...##...",
            "....#...",
    };
    // Halo : aile
    private static final String[] WING = {
            "##..........",
            "####........",
            ".#####......",
            "..#######...",
            "...#########",
            "....###.#.#.",
    };
    // Halo : plume
    private static final String[] FEATHER = {
            "......##",
            ".....###",
            "....###.",
            "...###..",
            "..###...",
            ".###....",
            "#.......",
    };
    // Prisma (secours) : cristal
    private static final String[] CRYSTAL = {
            "...##...",
            "..####..",
            ".######.",
            "########",
            ".######.",
            "..####..",
            "...##...",
    };

    public static String[] forProperty(Property property, RandomSource random) {
        String[][] options = switch (property) {
            case CORONA -> new String[][]{CLAW, SPINE};
            case AURORA -> new String[][]{LEAF, SEED};
            case FLASH -> new String[][]{SHELL, FIN};
            case ECLIPSE -> new String[][]{SKULL, FANG};
            case HALO -> new String[][]{WING, FEATHER};
            default -> new String[][]{CRYSTAL};
        };
        return options[random.nextInt(options.length)];
    }
}
