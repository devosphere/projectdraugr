package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #106/#108 — an animal that will hurt you.
 *
 * <p>Every tamed animal behaved identically. A milk goat and a full-grown ox were the same creature once the
 * bond reached TAMED: the same to approach, the same to work, the same to stand beside. `tamability` decided how
 * hard an animal was to <em>win over</em> and then stopped mattering.
 *
 * <p>That is why #106's restraint group sat blocked — there was nothing for a restraint to restrain.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AnAnimalThatWillHurtYouIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID tame(UUID chronicle, UUID chunk, String species) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',1,5,'FORAGING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
                "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,0,0,0,0)", bond, chronicle, pop, ts);
        return bond;
    }

    private int injury(UUID chronicle) {
        return jdbc.queryForObject("SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    private void stanchion(UUID chunk) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE','Milking stanchion',?)", id, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                    "VALUES (?,'MILKING_STANCHION','COMPLETED',100,?,100)", id, Timestamp.from(Instant.now()));
    }

    /** A biddable animal is handled without thinking about it; a dangerous one is not. */
    @Test
    void milkingAnOxUnrestrainedHurtsAndMilkingAGoatDoesNot() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2026-07-01T00:00:00Z");
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        try {
            // Nothing holds an animal still on this ground yet.
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                        "  AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.holds_an_animal_still)", chunk);
            jdbc.update("UPDATE chronicle_physiology SET injury_severity=0 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=?", chronicle);
            jdbc.update("DELETE FROM tamed_production");
            items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", now, "TEST_FIXTURE");

            // A goat is biddable: milking it costs nothing but the time.
            tame(chronicle, chunk, "mountain_goat");
            int beforeGoat = injury(chronicle);
            var goat = wildlife.takeTamedYield(chronicle, now, "milk the goat");
            assertEquals("SUCCEEDED", goat.outcome(), () -> goat.narration());
            assertEquals(beforeGoat, injury(chronicle), () -> "a biddable animal must not hurt anybody: " + goat.narration());

            // An aurochs is not. Silence the goat so the taking is about the cow.
            jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=?", chronicle);
            jdbc.update("DELETE FROM tamed_production");
            tame(chronicle, chunk, "aurochs");
            int beforeCow = injury(chronicle);
            var cow = wildlife.takeTamedYield(chronicle, now, "milk the cow");
            assertEquals("SUCCEEDED", cow.outcome(), () -> cow.narration());
            assertTrue(injury(chronicle) > beforeCow,
                () -> "a dangerous animal worked on with nothing holding it must hurt the keeper: " + cow.narration());
        } finally {
            jdbc.update("UPDATE chronicle_physiology SET injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** The stanchion's whole job: the same animal, worked safely, because something is holding it. */
    @Test
    void aStanchionMakesTheSameAnimalSafeToWorkOn() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2026-07-02T00:00:00Z");
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        try {
            jdbc.update("UPDATE chronicle_physiology SET injury_severity=0 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=?", chronicle);
            jdbc.update("DELETE FROM tamed_production");
            items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", now, "TEST_FIXTURE");
            tame(chronicle, chunk, "aurochs");
            stanchion(chunk);

            int before = injury(chronicle);
            var cow = wildlife.takeTamedYield(chronicle, now, "milk the cow");
            assertEquals("SUCCEEDED", cow.outcome(), () -> cow.narration());
            assertEquals(before, injury(chronicle),
                () -> "a stanchion holds the animal, and the work is safe: " + cow.narration());
        } finally {
            jdbc.update("UPDATE chronicle_physiology SET injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** The catalogue must keep the distinction the whole slice rests on, and keep a safe way to start. */
    @Test
    void theCatalogueKnowsWhichAnimalsAreDangerous() {
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_species WHERE temperament='DANGEROUS'", Integer.class) > 0,
            "nothing is dangerous to handle");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_species WHERE temperament='BIDDABLE'", Integer.class) > 0,
            "nothing is biddable, so every animal is a hazard");
        // A keeper must be able to milk something safely before they can build anything.
        assertTrue(jdbc.queryForObject(
            "SELECT COUNT(*) FROM tamed_yield ty JOIN wildlife_species ws ON ws.species_key=ty.species_key " +
            "WHERE ty.yield_kind='MILK' AND ws.temperament <> 'DANGEROUS'", Integer.class) > 0,
            "every milk animal is dangerous; there would be no safe way to start");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE holds_an_animal_still", Integer.class),
            "there is one restraint");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE holds_an_animal_still AND (is_shelter OR encloses OR is_barrier OR shelters_stock)", Integer.class),
            "a stanchion holds an animal still, nothing else");
    }
}
