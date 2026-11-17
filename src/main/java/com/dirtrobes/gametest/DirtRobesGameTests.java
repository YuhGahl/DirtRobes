package com.dirtrobes.gametest;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.block.IncubatorBlockEntity;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.excavation.ExcavationGame;
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
            helper.assertTrue(s.getSpectrobeLevel() == 3, "Intégrité 95 % : niveau 3 attendu, obtenu " + s.getSpectrobeLevel());
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
