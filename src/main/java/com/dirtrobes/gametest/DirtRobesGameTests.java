package com.dirtrobes.gametest;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.block.IncubatorBlockEntity;
import com.dirtrobes.combat.ChGauge;
import com.dirtrobes.combat.ComboAttack;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.team.PrizmodTeam;
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
import com.dirtrobes.species.Nature;
import com.dirtrobes.species.Stage;
import com.dirtrobes.item.MineralItem;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
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
        incubator.skipAhead(400); // l'éveil dure 2 minutes : on garde 20 s pour le test

        helper.runAfterDelay(400 + 5, () -> {
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
            helper.runAfterDelay(400 + 5 + t, () -> {
                List<SpectrobeEntity> found = helper.getEntities(ModRegistry.SPECTROBE.get());
                if (!found.isEmpty()) {
                    SpectrobeEntity s = found.get(0);
                    log("+{} ticks : distance au dresseur {}, navigation en cours={}, cible={}, goals=[{}]", dt,
                            String.format("%.2f", s.distanceTo(player)), !s.getNavigation().isDone(),
                            s.getTarget(), s.debugRunningGoals());
                }
            });
        }
        helper.runAfterDelay(400 + 250, () -> {
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
        perfect.setNature(Nature.BALANCED);
        damaged.setNature(Nature.BALANCED);
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

    /** Équipe du Prizmod : 2 dehors au maximum, ranger et sortir conservent le Spectrobe (niveau, EV…). */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void prizmodTeamStoresAndSendsOut(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity a = SpectrobeEntity.create(helper.getLevel(), adult, 12, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        SpectrobeEntity b = SpectrobeEntity.create(helper.getLevel(), adult, 13, player, helper.absoluteVec(new Vec3(6.5, 1, 4.5)));
        SpectrobeEntity c = SpectrobeEntity.create(helper.getLevel(), adult, 14, player, helper.absoluteVec(new Vec3(8.5, 1, 4.5)));
        PrizmodTeam.Team team = PrizmodTeam.team(player);
        helper.assertTrue(team.entries.size() == 3, "3 membres attendus, " + team.entries.size());
        helper.assertTrue(team.outCount() == 2, "2 dehors attendus, " + team.outCount());
        helper.assertTrue(c.isRemoved(), "Le 3e Spectrobe aurait dû être rangé d'office");

        helper.assertTrue(!PrizmodTeam.toggle(player, 2), "Impossible de sortir un 3e Spectrobe");
        helper.assertTrue(PrizmodTeam.toggle(player, 0), "Ranger le 1er devrait marcher");
        helper.assertTrue(a.isRemoved(), "Le 1er devrait avoir quitté le monde");
        helper.assertTrue(PrizmodTeam.toggle(player, 2), "Sortir le 3e devrait marcher maintenant");
        List<SpectrobeEntity> inWorld = helper.getEntities(ModRegistry.SPECTROBE.get());
        log("Dans le monde après échange : {}", inWorld.stream().map(s -> s.getSpectrobeLevel()).toList());
        helper.assertTrue(inWorld.size() == 2, "2 Spectrobes attendus dans le monde, " + inWorld.size());
        helper.assertTrue(inWorld.stream().anyMatch(s -> s.getUUID().equals(c.getUUID()) && s.getSpectrobeLevel() == 14 && s.isOwnedBy(player)),
                "Le 3e devrait être revenu avec son niveau 14 et son dresseur");
        helper.assertTrue(PrizmodTeam.summaries(player).size() == 3, "Le résumé de l'équipe devrait lister 3 membres");
        helper.succeed();
    }

    /** Recherche de l'enfant : trouve le fossile et le gisement de minéraux les plus proches, ignore ce qui est hors de portée. */
    @GameTest(template = "platform")
    public static void childFindsFossilsAndMinerals(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 12.5);
        Species child = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.CHILD).findFirst().orElseThrow();
        SpectrobeEntity s = SpectrobeEntity.create(helper.getLevel(), child, 5, player, helper.absoluteVec(new Vec3(10.5, 1, 10.5)));
        helper.setBlock(new BlockPos(13, 1, 10), ModRegistry.FOSSIL_STONE.get());
        helper.setBlock(new BlockPos(6, 1, 9), ModRegistry.MINERAL_DEPOSIT.get());
        helper.setBlock(new BlockPos(8, 1, 10), ModRegistry.DEEPSLATE_MINERAL_DEPOSIT.get());
        SpectrobeEntity.SearchResult result = s.scanForTreasure();
        log("Portée {} : fossile {} · minéral {}", s.searchRadius(), result.fossil(), result.mineral());
        helper.assertTrue(helper.absolutePos(new BlockPos(13, 1, 10)).equals(result.fossil()), "Fossile attendu en (13,1,10)");
        helper.assertTrue(helper.absolutePos(new BlockPos(8, 1, 10)).equals(result.mineral()), "Le gisement le plus proche est en (8,1,10)");
        helper.succeed();
    }

    /** Repos au Prizmod : un Spectrobe rangé blessé ou K.O. récupère avec le temps. */
    @GameTest(template = "platform")
    public static void storedSpectrobesRecover(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity s = SpectrobeEntity.create(helper.getLevel(), adult, 20, player, helper.absoluteVec(new Vec3(5.5, 1, 5.5)));
        s.setHealth(s.getMaxHealth() * 0.2f);
        helper.assertTrue(PrizmodTeam.toggle(player, 0), "Ranger devrait marcher");
        float before = PrizmodTeam.summaries(player).get(0).health();
        PrizmodTeam.restStored(player, 600);
        float after = PrizmodTeam.summaries(player).get(0).health();
        log("PV au repos : {} → {}", before, after);
        helper.assertTrue(after > before, "Les PV auraient dû remonter au repos");
        helper.succeed();
    }

    /** Krawl chef : renforce les Krawl proches et empêche de ranger ses Spectrobes. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void krawlLeaderRalliesAndBlocksStoring(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 2.5, 2.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity.create(helper.getLevel(), adult, 20, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        KrawlEntity leader = KrawlEntity.create(helper.getLevel(), KrawlManager.byName("otorso"), 30, helper.absoluteVec(new Vec3(15.5, 1, 15.5)));
        KrawlEntity minion = KrawlEntity.create(helper.getLevel(), KrawlManager.byName("swar"), 5, helper.absoluteVec(new Vec3(13.5, 1, 15.5)));
        helper.assertTrue(leader.isLeader(), "Otorso devrait être un chef");
        helper.assertTrue(PrizmodTeam.toggle(player, 0) && PrizmodTeam.toggle(player, 0), "Un chef qui ne combat pas ne bloque rien");
        leader.setTarget(player);
        helper.assertTrue(!PrizmodTeam.toggle(player, 0), "Impossible de ranger un Spectrobe près d'un chef");
        helper.succeedWhen(() -> helper.assertTrue(minion.hasEffect(MobEffects.DAMAGE_BOOST), "Le Krawl proche du chef devrait être renforcé"));
    }

    /** Vitesse : la stat VIT règle la vitesse de déplacement ; une nature +VIT la monte de 10 %. */
    @GameTest(template = "platform")
    public static void speedAndNatures(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity neutral = SpectrobeEntity.create(helper.getLevel(), adult, 50, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        SpectrobeEntity nimble = SpectrobeEntity.create(helper.getLevel(), adult, 50, player, helper.absoluteVec(new Vec3(15.5, 1, 4.5)));
        neutral.setNature(Nature.BALANCED);
        nimble.setNature(Nature.NIMBLE);
        log("{} niv. 50 : VIT {} (neutre) / {} (Agile), ATQ {} / {}, vitesse MC {} / {}", adult.name(), neutral.speedStat(), nimble.speedStat(),
                neutral.attackStat(), nimble.attackStat(),
                String.format("%.3f", neutral.getAttributeValue(Attributes.MOVEMENT_SPEED)), String.format("%.3f", nimble.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        helper.assertTrue(nimble.speedStat() == (int) Math.floor(neutral.speedStat() * 1.1), "Agile : VIT +10 % attendue");
        helper.assertTrue(nimble.attackStat() == (int) Math.floor(neutral.attackStat() * 0.9), "Agile : ATQ −10 % attendue");
        helper.assertTrue(nimble.getAttributeValue(Attributes.MOVEMENT_SPEED) > neutral.getAttributeValue(Attributes.MOVEMENT_SPEED),
                "Plus de VIT devrait donner plus de vitesse de déplacement");
        helper.succeed();
    }

    /** Gemmes : Topaze monte les EV de VIT, Graphite les baisse, sans passer sous 0. */
    @GameTest(template = "platform")
    public static void gemsRaiseAndLowerEvs(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity s = SpectrobeEntity.create(helper.getLevel(), adult, 30, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        int spd0 = s.speedStat();
        s.feedGem(ModRegistry.TOPAZ.get(), player);
        s.feedGem(ModRegistry.TOPAZ.get(), player);
        helper.assertTrue(s.getEv(MineralItem.Stat.SPEED) == 40, "2 Topazes : 40 EV VIT attendus, " + s.getEv(MineralItem.Stat.SPEED));
        helper.assertTrue(s.speedStat() > spd0, "La VIT aurait dû monter");
        s.feedGem(ModRegistry.GRAPHITE.get(), player);
        helper.assertTrue(s.getEv(MineralItem.Stat.SPEED) == 20, "Graphite : retour à 20 EV VIT, " + s.getEv(MineralItem.Stat.SPEED));
        s.feedGem(ModRegistry.GRAPHITE.get(), player);
        s.feedGem(ModRegistry.GRAPHITE.get(), player);
        helper.assertTrue(s.getEv(MineralItem.Stat.SPEED) == 0, "Les EV ne passent pas sous 0");
        helper.succeed();
    }

    /** Gemmes de nature : 16 gemmes, une par nature ; donner la gemme change la nature et les stats. */
    @GameTest(template = "platform")
    public static void natureGemsChangeNature(GameTestHelper helper) {
        helper.assertTrue(ModRegistry.NATURE_GEMS.size() == Nature.values().length, "Une gemme par nature attendue");
        helper.assertTrue(ModRegistry.NATURE_GEMS.stream().map(g -> g.get().nature()).distinct().count() == Nature.values().length,
                "Chaque nature doit avoir sa propre gemme");
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity s = SpectrobeEntity.create(helper.getLevel(), adult, 40, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        s.setNature(Nature.BALANCED);
        int spd = s.speedStat();
        helper.assertTrue(s.feedNatureGem(ModRegistry.SYLVITE.get(), player), "La Sylvite devrait changer la nature");
        helper.assertTrue(s.getNature() == Nature.NIMBLE, "Nature Agile attendue après la Sylvite, " + s.getNature());
        helper.assertTrue(s.speedStat() > spd, "La VIT devrait monter avec la nature Agile");
        helper.assertTrue(!s.feedNatureGem(ModRegistry.SYLVITE.get(), player), "Une 2e Sylvite ne devrait pas être consommée");
        helper.succeed();
    }

    /** Chroma : change la couleur, la garde après rangement au Prizmod, Chroma 1 rend la couleur normale. */
    @GameTest(template = "platform")
    public static void chromaChangesColour(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity s = SpectrobeEntity.create(helper.getLevel(), adult, 20, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        helper.assertTrue(s.colorVariant() == 0, "Couleur normale au départ");
        helper.assertTrue(s.feedChroma(2, player), "Chroma 3 devrait changer la couleur");
        helper.assertTrue(PrizmodTeam.toggle(player, 0) && PrizmodTeam.toggle(player, 0), "Ranger puis ressortir");
        SpectrobeEntity back = helper.getEntities(ModRegistry.SPECTROBE.get()).get(0);
        helper.assertTrue(back.colorVariant() == 2, "La couleur devrait être conservée au Prizmod, " + back.colorVariant());
        helper.assertTrue(back.feedChroma(0, player) && back.colorVariant() == 0, "Chroma 1 rend la couleur normale");
        helper.assertTrue(!back.feedChroma(0, player), "Pas de changement si c'est déjà la bonne couleur");
        helper.succeed();
    }

    /** Soutien : un Corona rangé donne +5 % d'ATQ aux Spectrobes dehors. */
    @GameTest(template = "platform")
    public static void storedSpectrobesSupportTheTeam(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        Species corona = SpeciesManager.all().stream().filter(s -> s.property() == com.dirtrobes.species.Property.CORONA).findFirst().orElseThrow();
        SpectrobeEntity fighter = SpectrobeEntity.create(helper.getLevel(), adult, 30, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        SpectrobeEntity.create(helper.getLevel(), corona, 30, player, helper.absoluteVec(new Vec3(6.5, 1, 4.5)));
        double before = fighter.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(PrizmodTeam.toggle(player, 1), "Ranger le Corona");
        com.dirtrobes.combat.Support sup = PrizmodTeam.applySupport(player);
        double after = fighter.getAttributeValue(Attributes.ATTACK_DAMAGE);
        log("Soutien {} : ATQ {} → {}", sup, String.format("%.2f", before), String.format("%.2f", after));
        helper.assertTrue(sup.atk() > 0.04f, "Le Corona rangé devrait donner un bonus d'ATQ");
        helper.assertTrue(after > before, "L'ATQ du Spectrobe dehors devrait monter");
        helper.succeed();
    }

    /** Combo : avec 2 Spectrobes dehors et 40 CH, la cible prend de gros dégâts et la jauge baisse de 40. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void comboAttackHitsHard(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 2.5, 2.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity.create(helper.getLevel(), adult, 40, player, helper.absoluteVec(new Vec3(9.5, 1, 9.5)));
        SpectrobeEntity.create(helper.getLevel(), adult, 40, player, helper.absoluteVec(new Vec3(11.5, 1, 9.5)));
        KrawlEntity krawl = KrawlEntity.create(helper.getLevel(), KrawlManager.byName("otorso"), 60, helper.absoluteVec(new Vec3(10.5, 1, 12.5)));
        float hp = krawl.getHealth();
        ChGauge.set(player, 20);
        helper.assertTrue(!ComboAttack.trigger(player, krawl), "Pas de combo avec 20 CH");
        ChGauge.set(player, 50);
        helper.assertTrue(ComboAttack.trigger(player, krawl), "Le combo devrait partir avec 50 CH");
        helper.assertTrue(ChGauge.get(player) == 10, "La jauge devrait baisser de 40, valeur " + ChGauge.get(player));
        helper.succeedWhen(() -> {
            helper.assertTrue(krawl.getHealth() < hp - 1, "Le Krawl aurait dû prendre des dégâts");
            log("Combo : PV du Krawl {} → {}", hp, krawl.getHealth());
        });
    }

    /** Objets de combat : Sérum soigne, Réveil sort du K.O., inutile = non consommé, recharge partagée. */
    @GameTest(template = "platform")
    public static void combatItemsWork(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity s = SpectrobeEntity.create(helper.getLevel(), adult, 30, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        helper.assertTrue(!s.useCombatItem(com.dirtrobes.item.CombatItem.Kind.SERUM, player), "Sérum inutile si PV pleins");
        s.setHealth(s.getMaxHealth() * 0.2f);
        helper.assertTrue(s.useCombatItem(com.dirtrobes.item.CombatItem.Kind.SERUM, player), "Le sérum devrait soigner");
        helper.assertTrue(s.getHealth() >= s.getMaxHealth() * 0.69f, "PV attendus vers 70 %, " + s.getHealth() / s.getMaxHealth());
        helper.assertTrue(!s.useCombatItem(com.dirtrobes.item.CombatItem.Kind.REVIVE, player), "Réveil inutile s'il n'est pas K.O.");
        s.hurt(s.damageSources().generic(), 1000f);
        helper.assertTrue(s.isKnockedOut(), "Le Spectrobe devrait être K.O.");
        helper.assertTrue(s.useCombatItem(com.dirtrobes.item.CombatItem.Kind.REVIVE, player) && !s.isKnockedOut(), "Le Réveil devrait le relever");
        com.dirtrobes.item.CombatItem.startSharedCooldown(player);
        helper.assertTrue(player.getCooldowns().isOnCooldown(ModRegistry.ANTIDOTE.get()), "La recharge doit être partagée par tous les objets");
        helper.succeed();
    }

    /** PC + enclos : ranger un Spectrobe au PC le fait apparaître dans l'enclos, le reprendre le retire. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void pcStorageShowsSpectrobesInPen(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 2.5, 2.5);
        BlockPos terminalPos = new BlockPos(1, 1, 1);
        helper.setBlock(terminalPos, ModRegistry.LAB_TERMINAL.get());
        com.dirtrobes.lab.LabTerminalBlockEntity terminal = helper.getBlockEntity(terminalPos);
        // Enclos réglé comme dans l'interface : coin à (+7, 0, +7) du terminal, 8 × 3 × 8, spawn au milieu du sol
        com.dirtrobes.lab.Pen pen = com.dirtrobes.lab.Pen.fromRelative(helper.absolutePos(terminalPos), 7, 0, 7, 8, 3, 8, 4, 0, 4);
        helper.assertTrue(pen.min().equals(helper.absolutePos(new BlockPos(8, 1, 8))) && pen.sizeX() == 8 && pen.sizeY() == 3,
                "Enclos mal calculé depuis les réglages : " + pen);
        com.dirtrobes.lab.Pen tooBig = com.dirtrobes.lab.Pen.fromRelative(helper.absolutePos(terminalPos), 0, 0, 0, 500, 1, 500, 900, 0, 900);
        helper.assertTrue(tooBig.sizeX() == com.dirtrobes.lab.Pen.MAX_XZ && tooBig.sizeY() == com.dirtrobes.lab.Pen.MIN_Y
                && tooBig.containsBlock(tooBig.spawn()), "Les réglages hors limites doivent être ramenés dans les limites");
        terminal.setPen(pen);
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity s = SpectrobeEntity.create(helper.getLevel(), adult, 25, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        java.util.UUID id = s.getUUID();
        helper.assertTrue(terminal.move(player, com.dirtrobes.lab.LabTerminalBlockEntity.AREA_TEAM, 0, com.dirtrobes.lab.LabTerminalBlockEntity.AREA_BOX, 0),
                "Ranger au PC devrait marcher");
        helper.assertTrue(PrizmodTeam.team(player).entries.isEmpty(), "L'équipe devrait être vide");
        helper.assertTrue(terminal.holds(id), "Le PC devrait contenir le Spectrobe");
        helper.runAfterDelay(60, () -> {
            List<SpectrobeEntity> shown = helper.getLevel().getEntitiesOfClass(SpectrobeEntity.class, pen.box().inflate(1), SpectrobeEntity::isPenResident);
            log("Pensionnaires visibles : {}", shown.size());
            helper.assertTrue(shown.size() == 1 && shown.get(0).getUUID().equals(id), "Le Spectrobe rangé devrait se promener dans l'enclos");
            log("Pensionnaire apparu à {} (spawn {})", shown.get(0).blockPosition(), pen.spawn());
            helper.assertTrue(!shown.get(0).shouldBeSaved(), "La copie de l'enclos ne doit pas être sauvegardée avec le monde");
            helper.assertTrue(!shown.get(0).hurt(shown.get(0).damageSources().generic(), 5f), "Les pensionnaires sont invulnérables");
        });
        helper.runAfterDelay(160, () -> {
            for (SpectrobeEntity e : helper.getLevel().getEntitiesOfClass(SpectrobeEntity.class, pen.box().inflate(8), SpectrobeEntity::isPenResident)) {
                helper.assertTrue(pen.contains(e.position()), "Le pensionnaire est sorti de l'enclos : " + e.position());
            }
            helper.assertTrue(terminal.move(player, com.dirtrobes.lab.LabTerminalBlockEntity.AREA_BOX, 0, com.dirtrobes.lab.LabTerminalBlockEntity.AREA_TEAM, 0),
                    "Reprendre depuis le PC devrait marcher");
            helper.assertTrue(PrizmodTeam.team(player).entries.size() == 1, "Le Spectrobe devrait être revenu dans l'équipe");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(SpectrobeEntity.class, pen.box().inflate(8), SpectrobeEntity::isPenResident).isEmpty(),
                    "La copie de l'enclos aurait dû disparaître");
            helper.succeed();
        });
    }

    /** Vol : une espèce « flying » utilise la navigation aérienne, ne prend pas de dégâts de chute et rejoint son dresseur. */
    @GameTest(template = "platform", timeoutTicks = 300)
    public static void flyingSpectrobesFly(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 16.5, 16.5);
        Species base = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        SpectrobeEntity flyer = SpectrobeEntity.create(helper.getLevel(), base, 20, player, helper.absoluteVec(new Vec3(3.5, 4, 3.5)));
        flyer.setSpecies(base.withFlying(true), true);
        helper.assertTrue(flyer.isFlyer(), "Le Spectrobe devrait être volant");
        helper.assertTrue(flyer.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation,
                "Navigation aérienne attendue, " + flyer.getNavigation().getClass().getSimpleName());
        float hp = flyer.getHealth();
        helper.runAfterDelay(250, () -> {
            log("Volant : PV {} → {}, distance au dresseur {}", hp, flyer.getHealth(), String.format("%.1f", flyer.distanceTo(player)));
            helper.assertTrue(flyer.getHealth() >= hp, "Un volant ne doit pas prendre de dégâts de chute");
            helper.assertTrue(flyer.distanceTo(player) < 8, "Le volant aurait dû rejoindre son dresseur");
            flyer.setSpecies(base.withFlying(false), true);
            helper.assertTrue(!flyer.isFlyer() && !(flyer.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation),
                    "Revenu au sol, il doit marcher à nouveau");
            helper.succeed();
        });
    }

    /** PC des Spectrobes : envoi direct depuis l'équipe, équipe pleine → PC, terminal cassé → PC. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void spectrobePcReceivesSpectrobes(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        com.dirtrobes.storage.GlobalPc pc = com.dirtrobes.storage.GlobalPc.get(helper.getLevel().getServer());
        java.util.UUID me = player.getUUID();
        Species adult = SpeciesManager.all().stream().filter(s -> s.stage() == Stage.ADULT).findFirst().orElseThrow();
        // envoi direct depuis l'équipe
        SpectrobeEntity first = SpectrobeEntity.create(helper.getLevel(), adult, 10, player, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        helper.assertTrue(com.dirtrobes.lab.LabNetwork.teamToPc(player, 0), "Envoi direct au PC");
        helper.assertTrue(first.isRemoved() && PrizmodTeam.team(player).entries.isEmpty() && pc.count(me) == 1, "Le Spectrobe devrait être au PC");
        // équipe pleine → PC
        for (int i = 0; i < PrizmodTeam.MAX; i++) {
            SpectrobeEntity.create(helper.getLevel(), adult, 5 + i, player, helper.absoluteVec(new Vec3(2.5 + i * 2, 1, 8.5)));
        }
        SpectrobeEntity extra = SpectrobeEntity.create(helper.getLevel(), adult, 30, player, helper.absoluteVec(new Vec3(10.5, 1, 14.5)));
        helper.assertTrue(extra.isRemoved() && PrizmodTeam.team(player).entries.size() == PrizmodTeam.MAX && pc.count(me) == 2,
                "Le 7e Spectrobe devrait partir au PC (PC : " + pc.count(me) + ")");
        // terminal cassé → PC
        BlockPos terminalPos = new BlockPos(1, 1, 1);
        helper.setBlock(terminalPos, ModRegistry.LAB_TERMINAL.get());
        com.dirtrobes.lab.LabTerminalBlockEntity terminal = helper.getBlockEntity(terminalPos);
        helper.assertTrue(terminal.capacity(me) == 30, "Un terminal = 1 boîte de 30");
        helper.assertTrue(terminal.move(player, com.dirtrobes.lab.LabTerminalBlockEntity.AREA_TEAM, 0, com.dirtrobes.lab.LabTerminalBlockEntity.AREA_BOX, 3),
                "Ranger au terminal");
        helper.setBlock(terminalPos, net.minecraft.world.level.block.Blocks.AIR);
        log("PC après destruction du terminal : {} Spectrobes", pc.count(me));
        helper.assertTrue(pc.count(me) == 3, "Le contenu du terminal détruit devrait être au PC, " + pc.count(me));
        helper.succeed();
    }

    /** Gantelet : module et épée à la table de forge (ancienne arme rendue), Prizmod requis, lame déployée (stats de l'épée) / rangée, extraction. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void gauntletModulesWeaponsAndExtraction(GameTestHelper helper) {
        ServerPlayer player = placePlayer(helper, 10.5, 10.5);
        var rm = helper.getLevel().getRecipeManager();
        var reg = helper.getLevel().registryAccess();
        ItemStack mineral = new ItemStack(ModRegistry.MINERALS.get(0).get());
        // 1) module d'extraction
        var in1 = new net.minecraft.world.item.crafting.SmithingRecipeInput(new ItemStack(ModRegistry.MODULE_EXTRACTOR.get()), new ItemStack(ModRegistry.GAUNTLET.get()), mineral);
        var r1 = rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, in1, helper.getLevel());
        helper.assertTrue(r1.isPresent(), "Recette du module d'extraction introuvable");
        ItemStack gauntlet = r1.get().value().assemble(in1, reg);
        // 2) une épée en diamant enchantée devient l'arme
        ItemStack sword = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        sword.setDamageValue(42);
        var in2 = new net.minecraft.world.item.crafting.SmithingRecipeInput(new ItemStack(ModRegistry.MODULE_BLADE.get()), gauntlet, sword);
        var r2 = rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, in2, helper.getLevel());
        helper.assertTrue(r2.isPresent(), "Recette de l'épée introuvable");
        gauntlet = r2.get().value().assemble(in2, reg);
        helper.assertTrue(com.dirtrobes.gauntlet.GauntletWeapon.get(gauntlet).is(net.minecraft.world.item.Items.DIAMOND_SWORD), "L'épée devrait être dans le gantelet");
        // 3) une épée en fer la remplace : l'épée en diamant doit être rendue
        var in3 = new net.minecraft.world.item.crafting.SmithingRecipeInput(new ItemStack(ModRegistry.MODULE_BLADE.get()), gauntlet,
                new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        var r3 = rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, in3, helper.getLevel());
        helper.assertTrue(r3.isPresent(), "Module de lame + épée devrait aussi marcher");
        gauntlet = r3.get().value().assemble(in3, reg);
        gauntlet.getItem().onCraftedBy(gauntlet, helper.getLevel(), player);
        boolean gotBack = player.getInventory().items.stream().anyMatch(s -> s.is(net.minecraft.world.item.Items.DIAMOND_SWORD) && s.getDamageValue() == 42);
        helper.assertTrue(gotBack, "L'ancienne épée (avec son usure) aurait dû être rendue");
        helper.assertTrue(com.dirtrobes.gauntlet.GauntletWeapon.get(gauntlet).is(net.minecraft.world.item.Items.IRON_SWORD), "Nouvelle arme : épée en fer");
        // 4) sortir / ranger
        player.getInventory().clearContent();
        player.getInventory().setItem(9, gauntlet);
        player.getInventory().selected = 0;
        helper.assertTrue(!com.dirtrobes.gauntlet.GauntletActions.toggleWeapon(player), "Sans Prizmod inséré, pas d'arme");
        gauntlet.set(ModRegistry.GAUNTLET_MODULES.get(), com.dirtrobes.gauntlet.GauntletItem.modules(gauntlet).withPrizmod(true));
        helper.assertTrue(com.dirtrobes.gauntlet.GauntletActions.toggleWeapon(player), "La lame devrait se déployer");
        helper.assertTrue(player.getMainHandItem().isEmpty() && com.dirtrobes.gauntlet.GauntletWeapon.get(gauntlet).is(net.minecraft.world.item.Items.IRON_SWORD),
                "L'épée reste dans le gantelet (effet visuel seulement)");
        helper.assertTrue(com.dirtrobes.gauntlet.GauntletActions.activeWeapon(player).is(net.minecraft.world.item.Items.IRON_SWORD), "Arme active : épée en fer");
        double dmg = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(dmg - 6.0) < 0.01, "Dégâts main vide avec l'épée en fer : 6 attendus, " + dmg);
        player.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.STICK));
        com.dirtrobes.gauntlet.GauntletCombat.syncAttributes(player);
        dmg = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(dmg - 1.0) < 0.01, "Main occupée : plus de bonus, " + dmg);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(com.dirtrobes.gauntlet.GauntletActions.toggleWeapon(player) && !com.dirtrobes.gauntlet.GauntletItem.modules(gauntlet).drawn(), "Lame rangée");
        dmg = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(dmg - 1.0) < 0.01, "Lame rangée : plus de bonus, " + dmg);
        // 5) arme alimentée par un Spectrobe : Module de lame + gantelet + noyau de Zazanero → Lame Zaza
        ItemStack core = com.dirtrobes.gauntlet.SpectrobeCoreItem.charged(com.dirtrobes.gauntlet.WeaponSpec.forSpecies("zazanero"));
        var in4 = new net.minecraft.world.item.crafting.SmithingRecipeInput(new ItemStack(ModRegistry.MODULE_BLADE.get()), gauntlet.copy(), core);
        var r4 = rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, in4, helper.getLevel());
        helper.assertTrue(r4.isPresent(), "Recette module + gantelet + noyau introuvable");
        ItemStack powered = r4.get().value().assemble(in4, reg);
        helper.assertTrue(com.dirtrobes.gauntlet.GauntletWeapon.get(powered).is(ModRegistry.SPECTROBE_WEAPONS.get("blade_zazanero").get()), "Lame Zaza installée");
        var wrongKind = new net.minecraft.world.item.crafting.SmithingRecipeInput(new ItemStack(ModRegistry.MODULE_CANNON.get()), gauntlet.copy(), core);
        helper.assertTrue(rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, wrongKind, helper.getLevel()).isEmpty(),
                "Un noyau de lame ne fait pas de canon");
        // 6) extraction
        BlockPos stone = new BlockPos(10, 1, 12);
        helper.setBlock(stone, ModRegistry.FOSSIL_STONE.get());
        helper.assertTrue(com.dirtrobes.gauntlet.GauntletActions.extractAt(player, helper.absolutePos(stone)), "L'extraction devrait marcher");
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.COBBLESTONE, stone);
        helper.assertTrue(player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof com.dirtrobes.item.FossilItem), "Un fossile attendu");
        helper.succeed();
    }

    /** Rareté : répartition des stats (total gardé, 200 max), paliers des lignées, tirage des fossiles, incubateur à l'électricité. */
    @GameTest(template = "platform")
    public static void rarityAndPoweredIncubator(GameTestHelper helper) {
        int[] r = com.dirtrobes.species.Rarity.RARE.redistribute(new int[]{100, 80, 60, 40});
        helper.assertTrue(r[0] == 107 && r[1] == 83 && r[2] == 54 && r[3] == 36, "Rare : 100/80/60/40 → 107/83/54/36, obtenu " + java.util.Arrays.toString(r));
        int[] capped = com.dirtrobes.species.Rarity.SECRET.redistribute(new int[]{190, 150, 100, 100});
        helper.assertTrue(capped[0] == 200 && capped[0] + capped[1] + capped[2] + capped[3] == 540, "Plafond 200, total gardé : " + java.util.Arrays.toString(capped));
        helper.assertTrue(com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("nagu")).rarity() == com.dirtrobes.species.Rarity.EPIC, "Nagu est épique");
        // rareté lue sur le disque d'Origins : 30 = Commun, 60 = Rare, 90 = Épique
        helper.assertTrue(com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("zappi")).rarity() == com.dirtrobes.species.Rarity.EPIC, "Zappi : 90 → Épique");
        helper.assertTrue(com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("komainu")).rarity() == com.dirtrobes.species.Rarity.RARE, "Komainu : 60 → Rare");
        helper.assertTrue(com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("zaza")).rarity() == com.dirtrobes.species.Rarity.COMMON, "Zaza : 30 → Commun");
        // lignée secrète d'Origins : Komainu spécial → Komanoto spécial → Flamerax
        var sk = com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("komainu_flamerax"));
        helper.assertTrue(sk != null && sk.rarity() == com.dirtrobes.species.Rarity.SECRET && sk.fromFossil()
                && DirtRobes.id("komanoto_flamerax").equals(sk.evolvesInto()), "Komainu de la lignée Flamerax");
        helper.assertTrue(!com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("flamerax")).fromFossil(), "Flamerax ne sort plus d'un fossile");
        helper.assertTrue(com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("windora")).rarity() == com.dirtrobes.species.Rarity.SECRET, "Windora est secret");
        helper.assertTrue(com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("windora")).fromFossil(), "Les secrets sortent (rarement) des fossiles");
        // fossile : espèce tirée à l'extraction, de la bonne propriété
        ItemStack fossil = new ItemStack(ModRegistry.FOSSILS.get(Property.AURORA).get());
        com.dirtrobes.item.FossilItem.assignSpecies(fossil, RandomSource.create(5));
        var sp = com.dirtrobes.species.SpeciesManager.get(fossil.get(ModRegistry.FOSSIL_SPECIES.get()));
        helper.assertTrue(sp != null && sp.property() == Property.AURORA && com.dirtrobes.item.FossilItem.rarity(fossil) == sp.rarity(),
                "Le fossile Aurora contient une espèce Aurora de la même rareté");
        // fouille plus longue pour un fossile rare
        helper.assertTrue(new ExcavationGame(Property.AURORA, com.dirtrobes.species.Rarity.EPIC, RandomSource.create(1)).timeLimit()
                > new ExcavationGame(Property.AURORA, RandomSource.create(1)).timeLimit(), "Fouille épique plus longue");
        // incubateur : 1 vitesse de base, +2 avec l'électricité
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, ModRegistry.INCUBATOR.get());
        com.dirtrobes.block.IncubatorBlockEntity be = helper.getBlockEntity(pos);
        ServerPlayer player = placePlayer(helper, 6.5, 6.5);
        be.start(Property.AURORA, player, 100);
        var cap = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(cap != null, "L'incubateur accepte l'énergie FE");
        // minéral d'évolution : évolue tout de suite
        com.dirtrobes.entity.SpectrobeEntity pet = com.dirtrobes.entity.SpectrobeEntity.create(helper.getLevel(),
                com.dirtrobes.species.SpeciesManager.get(DirtRobes.id("komainu_flamerax")), 1, player, helper.absoluteVec(new net.minecraft.world.phys.Vec3(8.5, 1, 8.5)));
        helper.assertTrue(pet.feedOriginsMineral(com.dirtrobes.item.OriginsMineralItem.Kind.EVOLVE)
                && DirtRobes.id("komanoto_flamerax").equals(pet.getSpeciesId()), "Minéral d'évolution : Komanoto, obtenu " + pet.getSpeciesId());
        int lvl = pet.getSpectrobeLevel();
        pet.feedOriginsMineral(com.dirtrobes.item.OriginsMineralItem.Kind.PURPLE);
        helper.assertTrue(pet.getSpectrobeLevel() > lvl, "1 024 EXP font monter de niveau");
        com.dirtrobes.block.IncubatorBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), be.getBlockState(), be);
        helper.assertTrue(be.progress() == 1, "Sans énergie : 1 par tick, obtenu " + be.progress());
        cap.receiveEnergy(1000, false);
        com.dirtrobes.block.IncubatorBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), be.getBlockState(), be);
        helper.assertTrue(be.progress() == 4 && cap.getEnergyStored() == 1000 - com.dirtrobes.block.IncubatorBlockEntity.FE_PER_TICK,
                "Avec énergie : +3 au tick (progression " + be.progress() + ", énergie " + cap.getEnergyStored() + ")");
        helper.succeed();
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
        for (int i = 0; i < slow.timeLimit(); i++) slow.tick();
        helper.assertTrue(slow.isOver(), "La partie aurait dû se terminer à la fin du temps");
        helper.succeed();
    }
}
