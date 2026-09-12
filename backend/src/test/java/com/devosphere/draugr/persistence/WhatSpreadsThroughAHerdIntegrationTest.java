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
 * #108/#52/#79 — what spreads through a herd.
 *
 * <p>Stock could be hungry, thirsty and worked to exhaustion, and their newborns could die of cold. They could
 * not get sick, which is why #108's quarantine pen and sick-animal shelter sat blocked for three cycles: there
 * was nothing to isolate an animal <em>from</em>.
 *
 * <p>It also left a hole in a loop that already ran. Kept stock foul the ground they stand on, and a manure pit
 * contains that muck — but the cost was only ever paid by the Chronicle and their larder, and <b>the animals
 * standing in it were unaffected</b>. Filth is the oldest reason stock sicken and the oldest reason to muck out.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WhatSpreadsThroughAHerdIntegrationTest {

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

    private UUID tame(UUID chronicle, UUID chunk, String species, Instant at) {
        Timestamp ts = Timestamp.from(at);
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

    private void refuse(UUID chunk, int level) {
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,?,?) " +
                    "ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=EXCLUDED.refuse_level, last_updated_at=EXCLUDED.last_updated_at",
            chunk, level, Timestamp.from(Instant.now()));
    }

    private UUID isolationShelter(UUID chunk, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE','Sick animal shelter',?)", id, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                    "VALUES (?,'SICK_ANIMAL_SHELTER','COMPLETED',100,?,100)", id, Timestamp.from(at));
        return id;
    }

    private int sickness(UUID bond) {
        return jdbc.queryForObject("SELECT sickness FROM wildlife_bond WHERE id=?", Integer.class, bond);
    }

    /** The loop this closes: stock foul the ground, the ground sickens the stock, the keeper mucks out or does not. */
    @Test
    void filthSickensStockAndCleanGroundMendsThem() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2026-05-01T00:00:00Z");

        try {
            UUID goat = tame(chronicle, chunk, "mountain_goat", now);

            // Clean ground takes nothing out of them.
            refuse(chunk, 0);
            items.advanceHerdSickness(now);
            assertEquals(0, sickness(goat), "clean ground does not make an animal ill");

            // A camp choked with muck does.
            refuse(chunk, 85);
            items.advanceHerdSickness(now);
            int afterOne = sickness(goat);
            assertTrue(afterOne > 0, "standing in filth must make an animal ill");
            items.advanceHerdSickness(now);
            assertTrue(sickness(goat) > afterOne, "and it gets worse the longer it stands there");

            // Muck out, and it mends. Nothing here is a one-way loss.
            refuse(chunk, 0);
            int beforeMending = sickness(goat);
            items.advanceHerdSickness(now);
            assertTrue(sickness(goat) < beforeMending,
                () -> "clean ground must let an animal mend (was " + beforeMending + ", now " + sickness(goat) + ")");
        } finally {
            jdbc.update("UPDATE wildlife_bond SET sickness=0");
            jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The whole job of the isolation shelter, and the only reason it is not a fourth pen: without one, a sick
     * beast takes the rest of its kind down with it; with one, it does not.
     */
    @Test
    void sicknessRunsThroughAHerdUnlessThereIsSomewhereToPutTheSickOne() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2026-05-02T00:00:00Z");

        try {
            jdbc.update("UPDATE wildlife_bond SET sickness=0");
            refuse(chunk, 0);                       // isolate the spread rule from the filth rule
            UUID ill = tame(chronicle, chunk, "reindeer", now);
            UUID well = tame(chronicle, chunk, "reindeer", now);
            // Nothing on this ground isolates anything yet.
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                        "  AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.isolates_sick)", chunk);
            jdbc.update("UPDATE wildlife_bond SET sickness=60 WHERE id=?", ill);

            items.advanceHerdSickness(now);
            assertTrue(sickness(well) > 0,
                () -> "a sick beast must take the rest of its kind down with it when there is nowhere to put it");

            // Raise the shelter, reset, and it no longer spreads.
            jdbc.update("UPDATE wildlife_bond SET sickness=0");
            jdbc.update("UPDATE wildlife_bond SET sickness=60 WHERE id=?", ill);
            isolationShelter(chunk, now);
            items.advanceHerdSickness(now);
            assertEquals(0, sickness(well),
                "somewhere to put the sick one is the whole job of the shelter");
            // Still ill — the shelter stops the next animal catching it, it does not cure what is already in it.
            // Not exactly 60: the ground here is clean, and the recovery half of the same turn has already taken
            // its bite out of the sick one before anything spreads.
            assertTrue(sickness(ill) >= PhysicalItemService.TOO_SICK_TO_GIVE,
                () -> "an isolated animal is still a sick animal, got " + sickness(ill));
            assertTrue(sickness(ill) < 60,
                () -> "and clean ground mends it a little even in isolation, got " + sickness(ill));
        } finally {
            jdbc.update("UPDATE wildlife_bond SET sickness=0");
            jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
            // The restore mirrors the teardown: a completed build left at zero integrity is world corruption.
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** A sick animal gives nothing. The herd stops paying until it is looked after. */
    @Test
    void aSickAnimalGivesNothing() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2026-05-03T00:00:00Z");
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        try {
            jdbc.update("UPDATE wildlife_bond SET sickness=0");
            jdbc.update("DELETE FROM tamed_production");
            items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", now, "TEST_FIXTURE");
            UUID goat = tame(chronicle, chunk, "mountain_goat", now);
            // Silence every other milk animal so the answer is about this one.
            jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=? AND id<>?", chronicle, goat);

            var healthy = wildlife.takeTamedYield(chronicle, now, "milk the goat");
            assertEquals("SUCCEEDED", healthy.outcome(), () -> "a well goat gives milk: " + healthy.narration());

            jdbc.update("DELETE FROM tamed_production");
            jdbc.update("UPDATE wildlife_bond SET last_yield_at=NULL, sickness=? WHERE id=?",
                PhysicalItemService.TOO_SICK_TO_GIVE, goat);
            var sick = wildlife.takeTamedYield(chronicle, now, "milk the goat");
            assertEquals("FAILED", sick.outcome(),
                () -> "a sick animal gives nothing until it is looked after: " + sick.narration());
        } finally {
            jdbc.update("UPDATE wildlife_bond SET sickness=0");
            // Put back what was silenced — the tests in this class share a database, and a sibling that expects
            // to find tamed stock should not inherit a herd this test quietly demoted.
            jdbc.update("UPDATE wildlife_bond SET bond_stage='TAMED' WHERE chronicle_id=? AND bond_stage='BONDED'", chronicle);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The catalogue must hold the distinction the structure rests on: exactly one thing isolates, it shelters
     * what it isolates, and it is reachable — a shelter nobody can build isolates nothing.
     */
    @Test
    void theCatalogueHoldsTheIsolationShelterToItsJob() {
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE isolates_sick", Integer.class),
            "quarantine_pen and sick_animal_shelter are one behaviour under two names; there must be one row");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE isolates_sick AND NOT (shelters_stock AND encloses)", Integer.class),
            "an isolation shelter must shelter what it isolates, or it saves an animal from its herd and loses it to the frost");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE isolates_sick AND shelters_birth", Integer.class),
            "a sick bay is the last place to put a labouring animal");
        assertTrue(jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key " +
            "JOIN construction_kind ck ON ck.project_kind=ad.construction_kind " +
            "WHERE ck.isolates_sick AND ad.review_state='VERIFIED'", Integer.class) > 0,
            "a shelter nobody can build isolates nothing");
    }
}
