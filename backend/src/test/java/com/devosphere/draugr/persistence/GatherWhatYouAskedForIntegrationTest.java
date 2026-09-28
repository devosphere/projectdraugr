package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gather what you asked for (#37).
 *
 * <p>Act nine: {@code gather dry grass} came back with <b>six beech mast</b>. Three faults compounded, and each of
 * them was a hand-written list standing in for the catalogue:
 *
 * <ul>
 *   <li>the classifier's forage rule carried thirty nouns and <b>grass was not among them</b>;</li>
 *   <li>the targeting matched the plant's key ({@code meadow_grass}) and its first drop
 *       ({@code dry_grass_bundle}) against the raw words, so neither "grass" nor "dry grass" matched;</li>
 *   <li>and the yield took {@code drops.get(0)} — the alphabetically first — so meadow grass's four distinct
 *       drops (dry grass, green grass, straw, thatch) were one drop with three spare names.</li>
 * </ul>
 *
 * <p>With nothing matched, the request fell through to "best available food", which is how asking for grass
 * produced nuts. The world knows 102 plants and 126 distinct drops; the question is now asked of the world.
 *
 * <p>Both directions are asserted, because widening a match is how you steal somebody else's words. Skips
 * without Docker.
 */
@SpringBootTest
class GatherWhatYouAskedForIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorldGenesisService worldGenesis;
    @Autowired WorldEcologyGenesisService ecology;
    @Autowired ChronicleService chronicles;
    @Autowired ChronicleActionService actions;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    private UUID where(UUID chronicle) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
    }

    private void plant(UUID chronicle, String floraKey, int quantity) {
        UUID chunk = where(chronicle);
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key=?", chunk, floraKey);
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity) VALUES (?,?,?,?)", chunk, floraKey, quantity, quantity);
    }

    private void clearGrowth(UUID chronicle) {
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=?", where(chronicle));
        jdbc.update("UPDATE world_chunk SET biome='MOUNTAIN' WHERE id=?", where(chronicle)); // little grows here
    }

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void askingForGrassGivesGrassAndEachOfItsDropsIsItsOwn() {
        UUID chronicle = awaken();
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", where(chronicle));
        plant(chronicle, "meadow_grass", 40);

        // The four drops the catalogue distinguishes, asserted from the catalogue rather than assumed.
        assertEquals(4, (int) jdbc.queryForObject("SELECT COUNT(*) FROM flora_drop WHERE flora_key='meadow_grass'", Integer.class),
            "meadow grass yields four different things, which is the whole point of naming one");

        int grassBefore = owned(chronicle, "dry_grass_bundle");
        ChronicleActionService.ActionResult grass = actions.resolve("gather dry grass");
        assertEquals("GATHER_PLANT", grass.intent(), () -> "the plainest word for it must reach foraging: " + grass.perception());
        assertEquals("SUCCEEDED", grass.outcome(), () -> "and find it where it grows: " + grass.perception());
        assertTrue(owned(chronicle, "dry_grass_bundle") > grassBefore,
            () -> "asking for dry grass gives dry grass, not whatever food sorts first: " + grass.perception());

        // The NAMED drop, not the alphabetically first. dry_grass_bundle sorts before straw_bundle, so a straw
        // request that came back with dry grass would pass every other line in this test.
        int strawBefore = owned(chronicle, "straw_bundle");
        ChronicleActionService.ActionResult straw = actions.resolve("gather straw");
        assertEquals("SUCCEEDED", straw.outcome(), () -> "straw is one of the four: " + straw.perception());
        assertTrue(owned(chronicle, "straw_bundle") > strawBefore,
            () -> "and asking for straw gives straw: " + straw.perception());

        // The plain family word, with no qualifier at all.
        assertEquals("GATHER_PLANT", actions.resolve("gather some grass").intent(),
            "the family word reaches the family — this was UNKNOWN, because 'grass' was on no list");
    }

    @Test
    void namingSomethingThatGrowsElsewhereIsToldSoRatherThanAnsweredWithSomethingElse() {
        UUID chronicle = awaken();
        clearGrowth(chronicle);

        ChronicleActionService.ActionResult absent = actions.resolve("gather dry grass");
        assertEquals("FAILED", absent.outcome(), () -> "no grass here: " + absent.perception());
        assertTrue(absent.perception().contains("none grows within reach"),
            () -> "and it says that, rather than handing over a different plant: " + absent.perception());
    }

    @Test
    void mastIsGatheredUnderTheTreeThatDropsItAndNeverByAGenericForage() {
        UUID chronicle = awaken();
        jdbc.update("UPDATE world_chunk SET biome='TEMPERATE_FOREST' WHERE id=?", where(chronicle));
        plant(chronicle, "oak", 5);

        int before = owned(chronicle, "acorn");
        ChronicleActionService.ActionResult acorns = actions.resolve("gather acorns");
        assertEquals("SUCCEEDED", acorns.outcome(), () -> "an oak stands here: " + acorns.perception());
        assertTrue(owned(chronicle, "acorn") > before,
            () -> "and acorns come from it — this answered with an arrowhead tuber: " + acorns.perception());

        // A tree is never what a generic forage turns up: felling and coppicing are their own acts, and
        // "forage for plants" must not come back with mast.
        ChronicleActionService.ActionResult generic = actions.resolve("forage for plants");
        assertNotEquals("FAILED", generic.outcome(), () -> "a forest yields something: " + generic.perception());
        assertTrue(!generic.perception().contains("acorn"),
            () -> "but not the oak's mast, which has to be asked for: " + generic.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void wideningTheForageRuleTakesNobodyElsesWords() {
        UUID chronicle = awaken();
        jdbc.update("UPDATE world_chunk SET biome='TEMPERATE_FOREST' WHERE id=?", where(chronicle));

        // Each of these is already somebody's. The forage rule now asks the catalogue what grows, which is
        // exactly the kind of widening that quietly swallows a neighbour.
        assertEquals("GATHER_BRANCHES", actions.resolve("gather firewood").intent(), "firewood is branches");
        assertEquals("GATHER_STONE", actions.resolve("gather stones").intent(), "stones are stones");
        assertEquals("GATHER_CLAY", actions.resolve("dig up some clay").intent(), "clay is clay");
        assertEquals("HARVEST_CROP", actions.resolve("harvest the grain").intent(), "grain is the crop");
        assertEquals("STRIP_BARK", actions.resolve("gather bark").intent(), "bark comes off a standing tree");
        assertEquals("STRIP_BARK", actions.resolve("gather willow bark").intent(), "even when the willow is named");
        assertEquals("FELL_TREE", actions.resolve("fell a tree").intent(), "felling is felling");
        assertEquals("EAT", actions.resolve("eat the blackberries").intent(), "eating is not gathering");
    }
}
