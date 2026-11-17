package com.dirtrobes.gametest;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.block.IncubatorBlockEntity;
import com.dirtrobes.combat.ChGauge;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.entity.StatCalc;
import com.dirtrobes.excavation.ExcavationGame;
import com.dirtrobes.krawl.KrawlEntity;
import com.dirtrobes.krawl.KrawlManager;
import com.dirtrobes.krawl.KrawlSpecies;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.species.Property;
import com.dirtrobes.species.Species;
import com.dirtrobes.species.SpeciesManager;
import com.dirtrobes.species.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Tests en jeu, lancés par GitHub Actions avec {@code ./gradlew runGameTestServer}.
 * Ils utilisent une plateforme de pierre vide (data/dirtrobes/structure/platform.nbt).
 */
@GameTestHolder(DirtRobes.MODID)
@PrefixGameTestTemplate(false)
public class DirtRobesGameTests {

    private static ServerPlayer placePlayer(GameTestHelper helper, double x, double z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
        return player;
    }

    private static void log(String msg, Object... args) {
        DirtRobes.LOGGER.info("[GameTest] " + msg, args);
    }

    /** Un fossile dégagé éclot dans l'incubateur, et le Spectrobe obtenu rejoint son dresseur. */
    @GameTest(template = "platform", timeoutTicks = 1000)
    public static void hatchedSpectrobeFollowsOwner(GameTestHelper helper) {
        BlockPos incubatorPos = new BlockPos(3, 1, 10);
        helper.setBlock(incubatorPos, ModRegistry.INCUBATOR.get());
        ServerPlayer player = placePlayer(helper, 16.5, 10.5);
        IncubatorBlockEntity incubator = helper.getBlockEntity(incubatorPos);
        incubator.inventory().setItem(0, new ItemStack(ModRegistry.FOSSILS.get(Property.FLASH).get()));
        incubator.finishExcavation(player, 95);
        helper.assertTrue(incubator.isBusy(), "L'incubateur devrait être en train d'éveiller le fossile");
        helper.assertTrue(incubator.inventory().getItem(0).isEmpty(), "Le fossile aurait dû être consommé");

        helper.runAfterDelay(IncubatorBlockEntity.DURATION + 5, () -> {
            List<SpectrobeEntity> found = helper.getEntities(ModRegistry.SPECTROBE.get());
            helper.assertTrue(found.size() == 1, "Un Spectrobe aurait dû éclore, trouvé : " + found.size());
            SpectrobeEntity s = found.get(0);
            log("Éclos : {} niv. {} (dresseur à {} blocs), apprivoisé={}, propriétaire={}, assis={}, noAi={}",
                    s.getSpeciesId(), s.getSpectrobeLevel(), String.format("%.1f", s.distanceTo(player)), s.isTame(),
                    s.getOwner() == player, s.isOrderedToSit(), s.isNoAi());
            helper.assertTrue(s.isOwnedBy(player), "Le Spectrobe éclos devrait appartenir au joueur");
            helper.assertTrue(s.getQuality() == 100, "Intégrité 95 % : stats à 100 % attendues, obtenu " + s.getQuality());
        });
        for (int t = 20; t <= 240; t += 20) {
            final int dt = t;
            helper.runAfterDelay(IncubatorBlockEntity.DURATION + 5 + t, () -> {
                List<SpectrobeEntity> found = helper.getEntities(ModRegistry.SPECTROBE.get());
                if (!found.isEmpty()) {
                    SpectrobeEntity s = found.get(0);
                    log("+{} ticks : distance au dresseur {}, navigation en cours={}, cible={}, goals=[{}]", dt,
                            String.format("%.2f", s.distanceTo(player)), !s.getNavigation().isDone(),
                            s.getTarget(), s.debugRunningGoals());
                }
            });
        }
        helper.runAfterDelay(IncubatorBlockEntity.DURATION + 250, () -> {
            SpectrobeEntity s = helper.getEntities(ModRegistry.SPECTROBE.get()).get(0);
            double d = s.distanceTo(player);
            helper.assertTrue(d < 6.5, "Le Spectrobe éclos aurait dû rejoindre son dresseur (distance " + String.format("%.1f", d) + ")");
            helper.assertTrue(d > 2.0, "Le Spectrobe ne devrait pas coller son dresseur (distance " + String.format("%.1f", d) + ")");
            helper.succeed();
        });
    }

    /** Un Spectrobe déjà proche (moins de 6 blocs) ne fonce pas dans les jambes du joueur. */
    @GameTest(template = "platform", timeoutTicks = 200, required = false)
    public static void followerKeepsItsDistance(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity s = SpectrobeEntity.create(helper.getLevel(), adult, 15, player, helper.absoluteVec(new Vec3(15.0, 1, 10.5)));
        double[] closest = {s.distanceTo(player)};
        for (int t = 1; t <= 120; t++) {
            helper.runAfterDelay(t, () -> closest[0] = Math.min(closest[0], s.distanceTo(player)));
        }
        helper.runAfterDelay(121, () -> {
            log("Distance minimale atteinte : {}", String.format("%.2f", closest[0]));
            helper.assertTrue(closest[0] > 2.5, "Le Spectrobe s'est trop approché : " + String.format("%.2f", closest[0]));
            helper.succeed();
        });
    }

    /** Échelle intégrité du fossile → qualité des stats, et effet sur les stats réelles. */
    @GameTest(template = "platform")
    public static void integrityScalesStats(GameTestHelper helper) {
        int[][] expected = {{100, 100}, {95, 100}, {94, 99}, {52, 75}, {11, 51}, {10, 50}, {1, 50}};
        for (int[] e : expected) {
            int q = StatCalc.qualityForIntegrity(e[0]);
            helper.assertTrue(q == e[1], "Intégrité " + e[0] + " % : qualité " + e[1] + " attendue, obtenue " + q);
        }
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity perfect = SpectrobeEntity.create(helper.getLevel(), adult, 50, 100, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        SpectrobeEntity damaged = SpectrobeEntity.create(helper.getLevel(), adult, 50, 50, player, helper.absoluteVec(new Vec3(15.5, 1, 15.5)));
        log("Stats à 100 % : PV {} ATQ {} DÉF {} · à 50 % : PV {} ATQ {} DÉF {}", perfect.hpStat(), perfect.attackStat(), perfect.defenseStat(),
                damaged.hpStat(), damaged.attackStat(), damaged.defenseStat());
        helper.assertTrue(Math.abs(damaged.attackStat() * 2 - perfect.attackStat()) <= 1, "L'ATQ à 50 % devrait être la moitié de l'ATQ à 100 %");
        helper.assertTrue(damaged.getMaxHealth() < perfect.getMaxHealth(), "Les PV max devraient baisser avec la qualité");
        helper.succeed();
    }

    /** Un Krawl créé a bien son espèce, son niveau et des stats qui montent avec le niveau. */
    @GameTest(template = "platform")
    public static void krawlHasSpeciesAndStats(GameTestHelper helper) {
        KrawlSpecies swar = KrawlManager.byName("swar");
        helper.assertTrue(swar != null, "Le Krawl « swar » devrait être chargé (" + KrawlManager.all().size() + " Krawl)");
        KrawlEntity weak = KrawlEntity.create(helper.getLevel(), swar, 2, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        KrawlEntity strong = KrawlEntity.create(helper.getLevel(), swar, 20, helper.absoluteVec(new Vec3(15.5, 1, 15.5)));
        log("Swar niv. 2 : {} PV / niv. 20 : {} PV, propriété {}", weak.getMaxHealth(), strong.getMaxHealth(), weak.getProperty());
        helper.assertTrue(swar.equals(weak.getSpecies()), "Espèce attendue : swar");
        helper.assertTrue(strong.getMaxHealth() > weak.getMaxHealth(), "Un Krawl de plus haut niveau devrait avoir plus de PV");
        helper.succeed();
    }

    /** Un Spectrobe adulte se défend contre un Krawl qui l'attaque, le bat et gagne de l'expérience. */
    @GameTest(template = "platform", timeoutTicks = 600)
    public static void spectrobeDefeatsKrawl(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 2.5, 2.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity spectrobe = SpectrobeEntity.create(helper.getLevel(), adult, 30, player, helper.absoluteVec(new Vec3(8.5, 1, 10.5)));
        int xpBefore = spectrobe.getXp();
        KrawlEntity krawl = KrawlEntity.create(helper.getLevel(), KrawlManager.byName("swar"), 2, helper.absoluteVec(new Vec3(12.5, 1, 10.5)));
        krawl.setTarget(spectrobe);
        helper.succeedWhen(() -> {
            helper.assertTrue(!krawl.isAlive(), "Le Krawl est encore en vie (" + krawl.getHealth() + " PV)");
            helper.assertTrue(spectrobe.getXp() > xpBefore, "Le Spectrobe aurait dû gagner de l'XP");
            log("Krawl vaincu, XP du Spectrobe : {} → {}", xpBefore, spectrobe.getXp());
        });
    }

    /** Le vortex fait apparaître un Krawl quand un joueur est à proximité. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void vortexSpawnsKrawl(GameTestHelper helper) {
        placePlayer(helper, 10.5, 16.5);
        helper.setBlock(new BlockPos(10, 1, 6), ModRegistry.VORTEX.get());
        helper.succeedWhen(() -> {
            List<KrawlEntity> krawl = helper.getEntities(ModRegistry.KRAWL.get());
            helper.assertTrue(!krawl.isEmpty(), "Aucun Krawl n'est sorti du vortex");
            helper.assertTrue(krawl.get(0).getSpecies() != null, "Le Krawl du vortex devrait avoir une espèce");
        });
    }

    /** Jauge CH : se remplit au combat, l'attaque spéciale la consomme, impossible si elle est trop basse. */
    @GameTest(template = "platform", timeoutTicks = 400)
    public static void chGaugeFillsAndPaysForSpecial(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 2.5, 2.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity spectrobe = SpectrobeEntity.create(helper.getLevel(), adult, 30, player, helper.absoluteVec(new Vec3(8.5, 1, 10.5)));
        KrawlEntity krawl = KrawlEntity.create(helper.getLevel(), KrawlManager.byName("otorso"), 40, helper.absoluteVec(new Vec3(11.5, 1, 10.5)));
        ChGauge.set(player, 0);
        helper.assertTrue(ModNetwork.triggerSpecialAttack(player, krawl) == 0, "L'attaque spéciale ne devrait pas partir avec une jauge vide");
        krawl.setTarget(spectrobe);
        helper.runAfterDelay(120, () -> {
            int ch = ChGauge.get(player);
            log("Jauge CH après 6 s de combat : {}", ch);
            helper.assertTrue(ch > 0, "La jauge CH aurait dû se remplir pendant le combat");
            ChGauge.set(player, 80);
            int used = ModNetwork.triggerSpecialAttack(player, krawl);
            helper.assertTrue(used == 1, "Le Spectrobe aurait dû lancer son attaque spéciale (" + used + ")");
            helper.assertTrue(ChGauge.get(player) == 80 - ChGauge.SPECIAL_COST, "La jauge aurait dû baisser de " + ChGauge.SPECIAL_COST
                    + ", valeur " + ChGauge.get(player));
            helper.succeed();
        });
    }

    /** Logique du mini-jeu de fouille. */
    @GameTest(template = "platform")
    public static void excavationLogic(GameTestHelper helper) {
        // Partie parfaite au burin : 100 % d'intégrité.
        ExcavationGame perfect = new ExcavationGame(Property.CORONA, RandomSource.create(42));
        for (int x = 0; x < ExcavationGame.WIDTH; x++) {
            for (int y = 0; y < ExcavationGame.HEIGHT; y++) {
                while (perfect.isFossil(x, y) && perfect.depth(x, y) > 0) perfect.use(ExcavationGame.Tool.CHISEL, x, y);
            }
        }
        helper.assertTrue(perfect.state() == ExcavationGame.State.SUCCESS, "La fouille parfaite devrait réussir : " + perfect.state());
        helper.assertTrue(perfect.integrity() == 100, "Intégrité attendue 100, obtenue " + perfect.integrity());

        // Forer un fossile à nu l'abîme.
        ExcavationGame rough = new ExcavationGame(Property.HALO, RandomSource.create(7));
        int fx = -1, fy = -1;
        for (int x = 0; x < ExcavationGame.WIDTH && fx < 0; x++) {
            for (int y = 0; y < ExcavationGame.HEIGHT; y++) {
                if (rough.isFossil(x, y)) {
                    fx = x;
                    fy = y;
                    break;
                }
            }
        }
        while (rough.depth(fx, fy) > 0) rough.use(ExcavationGame.Tool.CHISEL, fx, fy);
        int before = rough.integrity();
        rough.use(ExcavationGame.Tool.DRILL, fx, fy);
        helper.assertTrue(rough.integrity() < before, "La foreuse aurait dû abîmer le fossile");

        // Le temps écoulé termine la partie.
        ExcavationGame slow = new ExcavationGame(Property.AURORA, RandomSource.create(3));
        for (int i = 0; i < ExcavationGame.TIME_LIMIT; i++) slow.tick();
        helper.assertTrue(slow.isOver(), "La partie aurait dû se terminer à la fin du temps");
        helper.succeed();
    }
}
