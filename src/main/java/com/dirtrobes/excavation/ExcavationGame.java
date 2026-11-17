package com.dirtrobes.excavation;

import com.dirtrobes.species.Property;
import net.minecraft.util.RandomSource;

/**
 * Logique du mini-jeu de fouille (sans dépendance au rendu, pour pouvoir la tester).
 *
 * <p>Une grille de roche, de 1 à 3 couches par case, recouvre un fossile. Le joueur dégage les cases
 * avec deux outils :
 * <ul>
 *   <li><b>Foreuse</b> : retire une couche sur la case visée et ses 4 voisines. Rapide, mais forer
 *   une partie du fossile déjà à nu l'abîme fortement.</li>
 *   <li><b>Burin</b> : retire une couche sur une seule case. Lent, et ne fait qu'érafler le fossile.</li>
 * </ul>
 * La fouille réussit dès que toutes les cases du fossile sont dégagées. Si le temps s'écoule, le
 * fossile est arraché de force et perd de l'intégrité pour chaque partie encore enfouie.
 */
public class ExcavationGame {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 11;
    public static final int MAX_DEPTH = 3;
    public static final int TIME_LIMIT = 20 * 75; // 75 secondes
    public static final int DRILL_DAMAGE = 5;
    public static final int CHISEL_DAMAGE = 1;

    public enum Tool { DRILL, CHISEL }

    public enum State { PLAYING, SUCCESS, TIMEOUT, BROKEN }

    /** Résultat d'un coup d'outil, pour les effets visuels et sonores. */
    public record HitResult(int layersRemoved, int damage) {
        public static final HitResult NONE = new HitResult(0, 0);
    }

    private final int[][] depth = new int[WIDTH][HEIGHT];
    private final boolean[][] fossil = new boolean[WIDTH][HEIGHT];
    private final int fossilCells;
    private final Property property;
    private int integrity = 100;
    private int ticksLeft = TIME_LIMIT;
    private State state = State.PLAYING;

    public ExcavationGame(Property property, RandomSource random) {
        this.property = property;
        // Roche : bruit lissé pour former des veines plus ou moins dures.
        float[][] noise = new float[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) noise[x][y] = random.nextFloat();
        }
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                float sum = 0;
                int n = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx, ny = y + dy;
                        if (inside(nx, ny)) {
                            sum += noise[nx][ny];
                            n++;
                        }
                    }
                }
                float v = sum / n;
                depth[x][y] = v < 0.42f ? 1 : v < 0.58f ? 2 : 3;
            }
        }
        // Fossile : une silhouette propre à la propriété, posée au hasard (éventuellement retournée).
        String[] shape = FossilShapes.forProperty(property, random);
        boolean flip = random.nextBoolean();
        int sw = shape[0].length();
        int sh = shape.length;
        int ox = random.nextInt(Math.max(1, WIDTH - sw - 1)) + 1;
        int oy = random.nextInt(Math.max(1, HEIGHT - sh - 1)) + 1;
        int count = 0;
        for (int sy = 0; sy < sh; sy++) {
            for (int sx = 0; sx < sw; sx++) {
                char c = shape[sy].charAt(flip ? sw - 1 - sx : sx);
                int x = ox + sx, y = oy + sy;
                if (c == '#' && inside(x, y)) {
                    fossil[x][y] = true;
                    depth[x][y] = Math.max(depth[x][y], 2);
                    count++;
                }
            }
        }
        this.fossilCells = count;
    }

    public static boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT;
    }

    public Property property() {
        return property;
    }

    public int depth(int x, int y) {
        return depth[x][y];
    }

    public boolean isFossil(int x, int y) {
        return fossil[x][y];
    }

    public int integrity() {
        return integrity;
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    public State state() {
        return state;
    }

    public boolean isOver() {
        return state != State.PLAYING;
    }

    /** Fraction du fossile dégagée, entre 0 et 1. */
    public float exposed() {
        if (fossilCells == 0) return 1f;
        int exposed = 0;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (fossil[x][y] && depth[x][y] == 0) exposed++;
            }
        }
        return exposed / (float) fossilCells;
    }

    /** Intégrité finale envoyée au serveur (0 si le fossile est brisé). */
    public int finalIntegrity() {
        return state == State.BROKEN ? 0 : integrity;
    }

    public HitResult use(Tool tool, int x, int y) {
        if (isOver() || !inside(x, y)) return HitResult.NONE;
        int removed = 0;
        int damage = 0;
        if (tool == Tool.DRILL) {
            int[][] area = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : area) {
                int cx = x + d[0], cy = y + d[1];
                if (!inside(cx, cy)) continue;
                if (depth[cx][cy] > 0) {
                    depth[cx][cy]--;
                    removed++;
                } else if (fossil[cx][cy]) {
                    damage += DRILL_DAMAGE;
                }
            }
        } else {
            if (depth[x][y] > 0) {
                depth[x][y]--;
                removed++;
            } else if (fossil[x][y]) {
                damage += CHISEL_DAMAGE;
            }
        }
        integrity = Math.max(0, integrity - damage);
        checkEnd();
        return new HitResult(removed, damage);
    }

    public void tick() {
        if (isOver()) return;
        if (--ticksLeft <= 0) {
            ticksLeft = 0;
            int covered = Math.round((1f - exposed()) * 100f);
            integrity = Math.max(0, integrity - covered);
            state = integrity <= 0 ? State.BROKEN : State.TIMEOUT;
        }
    }

    private void checkEnd() {
        if (integrity <= 0) {
            state = State.BROKEN;
        } else if (exposed() >= 1f) {
            state = State.SUCCESS;
        }
    }
}
