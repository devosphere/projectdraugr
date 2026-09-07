package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Water has to be crossed, not walked over (#156/#157).
 *
 * <p>{@code move()} took the adjacent chunk in the named direction and handed it over without ever asking what it
 * was. A Chronicle could step off a beach into the open sea, carrying anything, and stand there. Nothing in the
 * world said no — not the biome, not the load, not the body.
 *
 * <p>What replaces it is not a wall. Open water can be swum, but not while loaded, because the load is what drowns
 * you — which is why anybody crossing water puts it down first. A marsh is the same question with a gentler
 * answer: soft ground takes a walker and will not take a walker with a heavy pack. A shallow ford is where neither
 * rule applies, which is what a ford is for.
 *
 * <p>Every case is asserted in both directions — the load that is refused and the load that is not — because a
 * rule that only ever refuses is indistinguishable from a wall, and the point is that it is not one. Skips without
 * Docker.
 */
@SpringBootTest
class WaterMustBeCrossedIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID chronicle() {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /** Stand the Chronicle west of a chunk of the given biome, so that one step east enters it. */
    private UUID standWestOf(UUID chronicle, String biome) {
        java.util.Map<String,Object> pair = jdbc.query(
            "SELECT w.id AS from_id, t.id AS to_id FROM world_chunk t JOIN world_chunk w " +
            "  ON w.world_id=t.world_id AND w.grid_x=t.grid_x-1 AND w.grid_y=t.grid_y " +
            "WHERE t.biome=? AND w.biome<>? LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("from", rs.getObject(1), "to", rs.getObject(2)) : null, biome, biome);
        if (pair == null) return null;
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", pair.get("from"), chronicle);
        return (UUID) pair.get("to");
    }

    /** Load the Chronicle to roughly the given percentage of what they can shoulder. */
    private void loadTo(UUID chronicle, int percentOfCapacity, Instant now) {
        // Put down what is already carried rather than destroying it: an object set to DESTROYED while still
        // owned by a living body, with no record of how it died, is exactly what the Auditor exists to catch —
        // and did catch, in CI, when this fixture's first draft did that. Setting it on the ground is both
        // consistent and what a Chronicle would actually do before crossing water.
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("DELETE FROM item_containment WHERE item_id IN (SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? " +
            "WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", here, chronicle);
        var capacity = items.currentLoad(chronicle).sustainedMassCapacityGrams();
        int want = capacity * percentOfCapacity / 100;
        // Whatever solid thing the catalogue actually holds, read from the definition rather than assumed — a key
        // guessed here would silently load nothing, and every assertion below would then pass for the wrong reason.
        java.util.Map<String,Object> ballast = jdbc.queryForMap(
            "SELECT item_key, unit_mass_grams FROM item_definition WHERE unit_mass_grams BETWEEN 300 AND 6000 " +
            "ORDER BY unit_mass_grams DESC, item_key LIMIT 1");
        String key = (String) ballast.get("item_key");
        int each = ((Number) ballast.get("unit_mass_grams")).intValue();
        for (int carried = 0; carried < want; carried += each)
            items.createCarriedItem(chronicle, key, key, now, "FORAGED_FROM_GROUND");
        if (percentOfCapacity > 50)
            assertTrue(items.currentLoad(chronicle).massGrams() > capacity / 2,
                "the ballast must actually weigh something, or a refusal below would prove nothing");
    }

    /** One step east, through the real action path — the classifier, the intent, and move() itself. */
    private void stepEast() {
        actions.resolve("walk east", UUID.randomUUID());
    }

    /** The sea takes a swimmer and refuses a pack mule. */
    @Test
    void openWaterCanBeSwumButNotCarriedAcross() {
        world();
        Instant now = ticks.current().simulatedAt();
        UUID chronicle = chronicle();
        UUID sea = standWestOf(chronicle, "OCEAN");
        Assumptions.assumeTrue(sea != null, "this world laid down no shore to step off");

        loadTo(chronicle, 90, now);
        UUID before = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        stepEast();
        assertEquals(before, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle),
            "loaded to ninety per cent, a Chronicle must not be standing in the open sea — this is the defect: "
                + "move() never asked what the ground was");

        standWestOf(chronicle, "OCEAN");
        loadTo(chronicle, 5, now);
        before = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        stepEast();
        assertTrue(!before.equals(jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle)),
            "and unburdened it must go — people swim; this is a load rule, not a wall");
    }

    /** Soft ground allows far more than the sea does, and still has a limit. */
    @Test
    void aMarshTakesAWalkerAndNotAHeavyPack() {
        world();
        Instant now = ticks.current().simulatedAt();
        UUID chronicle = chronicle();
        UUID marsh = standWestOf(chronicle, "WETLAND");
        Assumptions.assumeTrue(marsh != null, "this world laid down no marsh with dry ground beside it");
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND site_kind ILIKE '%ford%'", marsh);

        loadTo(chronicle, 95, now);
        UUID before = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        stepEast();
        assertEquals(before, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle),
            "a full pack has no bottom to push off in a bog");

        standWestOf(chronicle, "WETLAND");
        loadTo(chronicle, 40, now);
        before = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        stepEast();
        assertTrue(!before.equals(jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle)),
            "and an ordinary load walks a marsh, which is far more forgiving than the sea");
    }

    /**
     * The ford, and the reason it is worth placing at all: the same load, the same marsh, and the crossing is
     * possible because the world put a ford there.
     */
    @Test
    void aFordIsWhereTheLoadGetsAcross() {
        world();
        Instant now = ticks.current().simulatedAt();
        UUID chronicle = chronicle();
        UUID marsh = standWestOf(chronicle, "WETLAND");
        Assumptions.assumeTrue(marsh != null, "this world laid down no marsh with dry ground beside it");
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND site_kind ILIKE '%ford%'", marsh);

        loadTo(chronicle, 95, now);
        UUID before = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        stepEast();
        assertEquals(before, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle),
            "without a ford this load does not cross — that is the baseline the ford is measured against");

        UUID site = UUID.randomUUID();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, marsh);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Shallow ford',?)", site, marsh);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) " +
            "VALUES (?,?,?,'RESOURCE','Shallow ford',20)", site, worldId, marsh);

        standWestOf(chronicle, "WETLAND");
        before = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        stepEast();
        assertTrue(!before.equals(jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle)),
            "with a ford the same load crosses the same marsh — if this fails the ford is decoration");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Dry land is untouched: no load makes a meadow refuse a step. */
    @Test
    void dryGroundIsUnchanged() {
        world();
        Instant now = ticks.current().simulatedAt();
        UUID chronicle = chronicle();
        UUID land = standWestOf(chronicle, "GRASSLAND");
        Assumptions.assumeTrue(land != null, "this world laid down no grassland with other ground beside it");

        loadTo(chronicle, 95, now);
        UUID before = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        stepEast();
        assertTrue(!before.equals(jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle)),
            "a full pack walks over dry ground exactly as it always did");
    }
}
