package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pen must be what keeps stock in, and a pen coming apart must not (#108, V433). Skips without Docker.
 */
@SpringBootTest
class StockStrayIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int flockSize(UUID pop) {
        return jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop);
    }

    private UUID herdOf(UUID chronicle, UUID chunk, UUID worldId, String species, String role, int count, Timestamp ts) {
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Stray fold',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Stray fold',30)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,?,'DIURNAL',?,?,'FORAGING',?)", pop, site, species, role, count, count + 2, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',95,10,?)", UUID.randomUUID(), chronicle, pop, ts);
        return pop;
    }

    /**
     * #108 asks for "escape through a broken gate", and there was no escape at all: a tamed herd stayed put forever
     * whatever stood around it, so a pen held stock exactly as well as no pen. Proves loose stock wander in the
     * night, a sound fence holds them, the same fence coming apart does not, fowl go home to roost, and the last
     * of a herd stays with its keeper.
     */
    @Test
    void stockStrayFromGroundThatDoesNotHoldThem() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        UUID goats = herdOf(chronicle, chunk, worldId, "mountain_goat", "HERBIVORE", 3, ts);

        assertNull(wildlife.strayFromUnheldGround(chronicle, now, false), "stock do not wander off under the keeper's eye by day");
        assertEquals(3, flockSize(goats));

        assertEquals("mountain_goat", wildlife.strayFromUnheldGround(chronicle, now, true),
            "a herd left out overnight with nothing around it must be able to wander off");
        assertEquals(2, flockSize(goats), "one animal strays, not the herd");
        assertNull(wildlife.strayFromUnheldGround(chronicle, now, true), "a herd is thinned, not scattered in a night");
        assertEquals(2, flockSize(goats));

        // AND THE TRACKS LEAD SOMEWHERE. The morning's prose says "only tracks going out across the ground",
        // and a decrement on its own would make that a promise with nothing at the end of it: the animal would
        // simply cease to exist. A declared capability nothing delivers is this project's standing defect, and
        // prose describing a trackable beast is exactly that in a new place.
        //
        // The strayed goat is now on adjoining ground as a population of its own kind — joining its species if
        // one is next door, otherwise becoming one — so the existing TRACK can follow the tracks and a keeper
        // can go and fetch their goat back.
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Integer nextDoor = jdbc.queryForObject(
            "SELECT COALESCE(SUM(wp.population_count),0) FROM wildlife_population wp"
          + " JOIN ecology_site es ON es.id = wp.site_id"
          + " JOIN world_chunk n ON n.id = es.chunk_id"
          + " JOIN world_chunk h ON h.id = ?"
          + " WHERE wp.species_key='mountain_goat' AND n.world_id = h.world_id AND n.id <> h.id"
          + "   AND abs(n.grid_x - h.grid_x) <= 1 AND abs(n.grid_y - h.grid_y) <= 1", Integer.class, here);
        assertNotNull(nextDoor);
        assertTrue(nextDoor >= 1,
            () -> "the strayed goat must be somewhere a keeper could track it to, not gone from the world: "
                + nextDoor + " of its kind on adjoining ground");

        // A sound fence holds them.
        UUID fence = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Wattle fence','ACTIVE',?)", fence, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'WATTLE_FENCE','COMPLETED',100,?,100)", fence, ts);
        jdbc.update("UPDATE wildlife_bond SET last_strayed_at=NULL WHERE chronicle_id=?", chronicle);
        assertNull(wildlife.strayFromUnheldGround(chronicle, now, true), "a sound fence is what holds stock in");
        assertEquals(2, flockSize(goats));

        // The same fence one below "coming apart" is a gap with rails either side.
        jdbc.update("UPDATE construction_project SET integrity_percent=? WHERE object_id=?",
            com.devosphere.draugr.construction.ConstructionService.COMING_APART_BELOW - 1, fence);
        assertEquals("mountain_goat", wildlife.strayFromUnheldGround(chronicle, now, true),
            "a fence coming apart must not hold stock — the broken gate #108 names");
        assertEquals(1, flockSize(goats));

        // The last one stays: a lone tame beast follows its keeper.
        jdbc.update("UPDATE wildlife_bond SET last_strayed_at=NULL WHERE chronicle_id=?", chronicle);
        assertNull(wildlife.strayFromUnheldGround(chronicle, now, true), "the last of a herd does not wander off");
        assertEquals(1, flockSize(goats));

        // Fowl go back to the roost at dusk.
        UUID fowl = herdOf(chronicle, chunk, worldId, "guinea_fowl", "OMNIVORE", 5, ts);
        assertNull(wildlife.strayFromUnheldGround(chronicle, now, true), "fowl come home to roost and do not stray");
        assertEquals(5, flockSize(fowl));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
