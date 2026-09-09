package com.devosphere.draugr.persistence;

import com.devosphere.draugr.assembly.AssemblyService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #108 — the byre you built holds nothing.
 *
 * <p>V284 added the animal houses a keeper would actually raise, and every one of them is buildable today: a
 * cattle byre has a VERIFIED assembly with two stages, real timber and thatch requirements, and a cutting tool
 * to set its posts. A keeper could work through all of that, put their oxen in it, and the oxen would not rest.
 * {@code restPennedDraftBeasts} named three literals — ANIMAL_PEN, HITCHING_POST, TETHER_LINE — so the only
 * thing that rested a beast was the generic pen, and the building whose whole purpose is a place an ox stands
 * overnight was scenery.
 *
 * <p>This walks the byre through the real assembly boundary and proves the ox recovers in it. It also holds the
 * catalogue to the agreement V293 introduced: the day encounter check and the night raid on a herd must never
 * disagree about what stands in a wolf's way. Skips without Docker.
 */
@SpringBootTest
class ByreHoldsTheOxIntegrationTest {

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
    @Autowired AssemblyService assembly;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aCattleByreRestsTheOxThatStandsInIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // A tamed ox, worked tired, bonded to this Chronicle.
        UUID site = UUID.randomUUID();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Pasture',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Pasture',50)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'ox','HERBIVORE','DIURNAL',1,3,'FORAGING',?)", pop, site, ts);
        UUID bond = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at,draft_fatigue) " +
                "VALUES (?,?,?,'TAMED',90,10,?,80)", bond, chronicle, pop, ts);

        // Nothing built here yet: the beast stays tired. This is what proves the fixture, not the fix.
        items.restPennedDraftBeasts(now);
        assertEquals(80, (int) jdbc.queryForObject("SELECT draft_fatigue FROM wildlife_bond WHERE id=?", Integer.class, bond),
                "with nowhere to be held, a tired beast does not recover");

        // Raise the byre through the real assembly boundary: posts set with a cutting tool, then walls and thatch.
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", now, "TEST_SEED"); // stage one is CUTTING work
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "timber_log", "Timber log", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fibre cordage", now, "TEST_SEED");
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", now, "TEST_SEED");
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "thatch_bundle", "Thatch bundle", now, "TEST_SEED");

        boolean standing = false;
        for (int i = 0; i < 5 && !standing; i++) {
            String[] step = assembly.advance(chronicle, chunk, "build a cattle byre", now);
            assertNotNull(step, "the text must name the cattle-byre assembly");
            assertTrue(!"FAILED".equals(step[0]), () -> "advancing the byre must not fail: " + step[1]);
            standing = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                    "WHERE cp.project_kind='CATTLE_BYRE' AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
                    "  AND w.lifecycle_state='ACTIVE' AND w.current_location_id=?)", Boolean.class, chunk));
        }
        assertTrue(standing, "a cattle byre must stand complete on this ground after its stages are worked");

        // The whole complaint: the ox rests in the building raised to hold it.
        items.restPennedDraftBeasts(now);
        int rested = jdbc.queryForObject("SELECT draft_fatigue FROM wildlife_bond WHERE id=?", Integer.class, bond);
        assertTrue(rested < 80, () -> "an ox standing in its byre must recover (was 80, now " + rested + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The byre is not the only animal house that was inert, so this asserts the catalogue rather than one row:
     * every structure a keeper raises to hold stock must actually hold it, and each must be something a keeper
     * can reach — a verified assembly with stages — or wiring it changes nothing.
     */
    @Test
    void everyBuildingRaisedToHoldStockHoldsStock() {
        List<String> inert = jdbc.queryForList(
                "SELECT k FROM unnest(ARRAY['CATTLE_BYRE','GOAT_FOLD','PIG_STY','POULTRY_COOP','TIMBER_BARN'," +
                "                           'ANIMAL_PEN','HITCHING_POST','TETHER_LINE']) k " +
                "WHERE k NOT IN (SELECT project_kind FROM construction_kind WHERE shelters_stock)", String.class);
        assertTrue(inert.isEmpty(), () -> "these are built to keep animals and rest none: " + inert);

        List<String> unreachable = jdbc.queryForList(
                "SELECT ck.project_kind FROM construction_kind ck WHERE ck.shelters_stock AND ck.project_kind <> 'ANIMAL_PEN' " +
                "AND NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key " +
                "                WHERE ad.construction_kind=ck.project_kind AND ad.review_state='VERIFIED')", String.class);
        assertTrue(unreachable.isEmpty(), () -> "nobody can build these, so resting a beast in them changes nothing: " + unreachable);
    }

    /**
     * The day and the night must agree about what stands in a wolf's way. V281 made {@code is_barrier} the thing
     * the night raid on a herd reads; the day encounter check scored two literals of its own, so a dry stone wall
     * turned a predator aside at midnight and was invisible at noon. One number now answers both.
     */
    @Test
    void theDayAndTheNightAgreeAboutWhatIsABarrier() {
        List<String> disagreeing = jdbc.queryForList(
                "SELECT project_kind FROM construction_kind WHERE is_barrier <> (barrier_strength > 0) ORDER BY 1", String.class);
        assertTrue(disagreeing.isEmpty(),
                () -> "is_barrier and barrier_strength must name the same things: " + disagreeing);

        // And stacked stone must be worth more than piled brush, or the number carries no meaning.
        Integer stone = jdbc.queryForObject("SELECT barrier_strength FROM construction_kind WHERE project_kind='DRY_STONE_WALL'", Integer.class);
        Integer brush = jdbc.queryForObject("SELECT barrier_strength FROM construction_kind WHERE project_kind='BRUSH_FENCE'", Integer.class);
        assertNotNull(stone);
        assertNotNull(brush);
        assertTrue(stone > brush, () -> "a stone wall must stand stronger than piled brush (" + stone + " vs " + brush + ")");

        // Nothing that is not a barrier may quietly acquire a strength.
        assertNull(jdbc.queryForObject(
                "SELECT MAX(barrier_strength) FROM construction_kind WHERE NOT is_barrier AND barrier_strength > 0", Integer.class),
                "a strength on something that is not a barrier is a wall that only exists at noon");
    }
}
