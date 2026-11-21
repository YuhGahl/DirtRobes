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
 * Comme dans Spectrobes Origins, de grosses <b>pierres</b> (jusqu'à 10) reposent par-dessus la roche :
 * chacune a des PV (10 à 20) et un ou deux <b>points sensibles</b>. Frapper une pierre lui retire des PV
 * (beaucoup plus sur un point sensible) ; à 0 elle vole en éclats, libère toutes ses cases et ébranle
 * les pierres voisines. Les fossiles rares sont recouverts de plus de pierres, plus solides.
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
    public static final int MAX_STONES = 10;
    public static final int MAX_STONE_HP = 20;
    /** PV retirés à une pierre : burin, foreuse, multiplicateur sur un point sensible, onde de choc sur les voisines. */
    public static final int CHISEL_STONE = 2, DRILL_STONE = 3, SWEET_SPOT_MULT = 4, SHOCK = 3;

    public enum Tool { DRILL, CHISEL }

    public enum State { PLAYING, SUCCESS, TIMEOUT, BROKEN }

    /** Résultat d'un coup d'outil, pour les effets visuels et sonores. */
    public record HitResult(int layersRemoved, int damage, int stoneHits, boolean sweetSpot, int stonesBroken) {
        public static final HitResult NONE = new HitResult(0, 0, 0, false, 0);

        public HitResult(int layersRemoved, int damage) {
            this(layersRemoved, damage, 0, false, 0);
        }
    }

    /** Une pierre d'Origins : PV, points sensibles et pierres voisines. */
    public static final class Stone {
        public final int id;
        public final int maxHp;
        int hp;
        final java.util.List<int[]> sweetSpots = new java.util.ArrayList<>();
        final java.util.Set<Integer> adjacent = new java.util.TreeSet<>();

        Stone(int id, int hp) {
            this.id = id;
            this.maxHp = hp;
            this.hp = hp;
        }

        public int hp() {
            return hp;
        }

        public boolean broken() {
            return hp <= 0;
        }

        public java.util.Set<Integer> adjacent() {
            return java.util.Collections.unmodifiableSet(adjacent);
        }
    }

    private final int[][] stoneAt = new int[WIDTH][HEIGHT];
    private final boolean[][] sweet = new boolean[WIDTH][HEIGHT];
    private final java.util.List<Stone> stones = new java.util.ArrayList<>();

    private final int[][] depth = new int[WIDTH][HEIGHT];
    private final boolean[][] fossil = new boolean[WIDTH][HEIGHT];
    private final int fossilCells;
    private final Property property;
    private int integrity = 100;
    private final int timeLimit;
    private int ticksLeft;
    private State state = State.PLAYING;

    public ExcavationGame(Property property, RandomSource random) {
        this(property, com.dirtrobes.species.Rarity.COMMON, random);
    }

    /** Plus le fossile est rare, plus la roche est dure (couches profondes) et plus le temps accordé est long. */
    public ExcavationGame(Property property, com.dirtrobes.species.Rarity rarity, RandomSource random) {
        this.property = property;
        this.timeLimit = 20 * rarity.excavationSeconds;
        this.ticksLeft = timeLimit;
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
                depth[x][y] = v < rarity.depth2 ? 1 : v < rarity.depth3 ? 2 : 3;
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
        placeStones(rarity, random, ox + sw / 2, oy + sh / 2);
    }

    /**
     * Pose les pierres façon Origins : des germes répartis autour du fossile, chaque pierre occupant les
     * cases les plus proches de son germe (dans un rayon limité). Les PV suivent la progression du
     * disque (10, 11, 12…) ; la dernière pierre n'a qu'un point sensible.
     */
    private void placeStones(com.dirtrobes.species.Rarity rarity, RandomSource random, int cx, int cy) {
        for (int[] row : stoneAt) java.util.Arrays.fill(row, -1);
        int count = switch (rarity) {
            case COMMON -> 5;
            case RARE -> 7;
            case EPIC -> 9;
            case SECRET -> MAX_STONES;
        };
        int hpBonus = rarity.ordinal();
        int[][] seeds = new int[count][2];
        for (int i = 0; i < count; i++) {
            int sx = 0, sy = 0;
            for (int attempt = 0; attempt < 20; attempt++) {
                sx = Math.clamp(cx + random.nextInt(11) - 5, 0, WIDTH - 1);
                sy = Math.clamp(cy + random.nextInt(9) - 4, 0, HEIGHT - 1);
                boolean free = true;
                for (int j = 0; j < i; j++) {
                    if (Math.abs(seeds[j][0] - sx) + Math.abs(seeds[j][1] - sy) < 3) free = false;
                }
                if (free) break;
            }
            seeds[i][0] = sx;
            seeds[i][1] = sy;
            stones.add(new Stone(i, Math.min(MAX_STONE_HP, 10 + i + hpBonus)));
        }
        float radius = 2.6f;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                int best = -1;
                float bestDist = Float.MAX_VALUE;
                for (int i = 0; i < count; i++) {
                    float dx = x - seeds[i][0], dy = (y - seeds[i][1]) * 1.2f;
                    float d = dx * dx + dy * dy;
                    if (d < bestDist) {
                        bestDist = d;
                        best = i;
                    }
                }
                if (best >= 0 && bestDist <= radius * radius) stoneAt[x][y] = best;
            }
        }
        // Voisines : pierres qui se touchent.
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                int a = stoneAt[x][y];
                if (a < 0) continue;
                int[][] dirs = {{1, 0}, {0, 1}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (!inside(nx, ny)) continue;
                    int b = stoneAt[nx][ny];
                    if (b >= 0 && b != a) {
                        stones.get(a).adjacent.add(b);
                        stones.get(b).adjacent.add(a);
                    }
                }
            }
        }
        // Points sensibles : 2 par pierre (1 pour la dernière), tirés parmi ses cases.
        for (Stone stone : stones) {
            java.util.List<int[]> cells = new java.util.ArrayList<>();
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (stoneAt[x][y] == stone.id) cells.add(new int[]{x, y});
                }
            }
            if (cells.isEmpty()) {
                stone.hp = 0;
                continue;
            }
            int spots = Math.min(cells.size(), stone.id == count - 1 ? 1 : 2);
            for (int k = 0; k < spots; k++) {
                int[] c = cells.remove(random.nextInt(cells.size()));
                sweet[c[0]][c[1]] = true;
                stone.sweetSpots.add(c);
            }
        }
    }

    /** Identifiant de la pierre intacte qui recouvre la case, ou -1. */
    public int stoneAt(int x, int y) {
        int id = stoneAt[x][y];
        return id >= 0 && !stones.get(id).broken() ? id : -1;
    }

    public boolean isSweetSpot(int x, int y) {
        return sweet[x][y] && stoneAt(x, y) >= 0;
    }

    public java.util.List<Stone> stones() {
        return java.util.Collections.unmodifiableList(stones);
    }

    public Stone stone(int id) {
        return stones.get(id);
    }

    /** Frappe une pierre ; renvoie le nombre de pierres brisées (onde de choc comprise). */
    private int hitStone(int id, int damage) {
        Stone stone = stones.get(id);
        if (stone.broken()) return 0;
        stone.hp = Math.max(0, stone.hp - damage);
        if (!stone.broken()) return 0;
        int broken = 1;
        for (int n : stone.adjacent) broken += hitStone(n, SHOCK);
        return broken;
    }

    public int timeLimit() {
        return timeLimit;
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
                if (fossil[x][y] && depth[x][y] == 0 && stoneAt(x, y) < 0) exposed++;
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
        int stoneHits = 0;
        int broken = 0;
        boolean sweetHit = false;
        int[][] area = tool == Tool.DRILL ? new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}} : new int[][]{{0, 0}};
        // Une pierre ne prend qu'un coup par utilisation, même si la foreuse touche plusieurs de ses cases.
        java.util.Map<Integer, Boolean> struck = new java.util.LinkedHashMap<>();
        for (int[] d : area) {
            int cx = x + d[0], cy = y + d[1];
            if (!inside(cx, cy)) continue;
            int stone = stoneAt(cx, cy);
            if (stone >= 0) {
                struck.merge(stone, sweet[cx][cy], Boolean::logicalOr);
            } else if (depth[cx][cy] > 0) {
                depth[cx][cy]--;
                removed++;
            } else if (fossil[cx][cy]) {
                damage += tool == Tool.DRILL ? DRILL_DAMAGE : CHISEL_DAMAGE;
            }
        }
        for (var e : struck.entrySet()) {
            int base = tool == Tool.DRILL ? DRILL_STONE : CHISEL_STONE;
            if (e.getValue()) sweetHit = true;
            broken += hitStone(e.getKey(), e.getValue() ? base * SWEET_SPOT_MULT : base);
            stoneHits++;
        }
        integrity = Math.max(0, integrity - damage);
        checkEnd();
        return new HitResult(removed, damage, stoneHits, sweetHit, broken);
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
