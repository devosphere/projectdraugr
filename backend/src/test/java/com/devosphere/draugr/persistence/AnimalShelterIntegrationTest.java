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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Somewhere to put the animals (#77/#108), and it has to actually keep them.
 *
 * <p>A keeper had exactly one way to shelter stock — ANIMAL_PEN, raised by a Java intent, one size for everything
 * from a hare to an aurochs. These four are species-appropriate, and the reason they are worth having is not
 * variety: V281 made {@code is_barrier} the thing the night predator raid reads, so a structure that stands in a
 * wolf's way protects a herd and one that does not, does not.
 *
 * <p>Proves the distinction is real in both directions — a goat fold keeps the wolves off, and the roofed two
 * shelter while the two hurdle rings deliberately do not. Skips without Docker.
 */
@SpringBootTest
class AnimalShelterIntegrationTest {

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

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** The registry must say what each one is: all four stop a wolf, only the roofed two hold a body. */
    @Test
    void theRoofedOnesEncloseAndAllOfThemStandInSomethingsWay() {
        world();

        List<String> notBarriers = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE proven_in='V284' AND NOT is_barrier ORDER BY 1", String.class);
        assertTrue(notBarriers.isEmpty(),
            "an animal shelter that stops nothing protects no stock: " + notBarriers);

        List<String> encloses = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE proven_in='V284' AND encloses ORDER BY 1", String.class);
        assertTrue(encloses.equals(List.of("CATTLE_BYRE", "POULTRY_COOP")),
            "a hurdle ring is a wall, not a roof — only the coop and the byre enclose: " + encloses);

        // Each must be buildable: an assembly, at least one stage, and every material it asks for must exist.
        List<String> unbuildable = jdbc.queryForList(
            "SELECT ad.assembly_key FROM assembly_definition ad " +
            "WHERE ad.construction_kind IN (SELECT project_kind FROM construction_kind WHERE proven_in='V284') " +
            "AND NOT EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key=ad.assembly_key) ORDER BY 1", String.class);
        assertTrue(unbuildable.isEmpty(), "a structure with no stages cannot be raised: " + unbuildable);
    }

    /** And the point of it: a fold keeps the wolves off stock that would otherwise be taken. */
    @Test
    void aGoatFoldKeepsTheWolvesOffTheGoats() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // Clear this ground, then a tamed flock and hunting wolves on it, with nothing standing.
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        UUID fold = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Goat ground',?)", fold, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Goat ground',30)", fold, worldId, chunk);
        UUID flock = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,'mountain_goat','HERBIVORE','DIURNAL',4,6,'FORAGING',?)", flock, fold, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
            "VALUES (?,?,?,'TAMED',95,10,?)", UUID.randomUUID(), chronicle, flock, ts);
        // wildlife_population.site_id is UNIQUE, so the wolves need ground of their own.
        UUID wolfGround = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Wolf ground',?)", wolfGround, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Wolf ground',30)", wolfGround, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,'gray_wolf','CARNIVORE','NOCTURNAL',3,5,'HUNTING',?)", UUID.randomUUID(), wolfGround, ts);

        // Unprotected in the dark, one is taken — the baseline, or this proves nothing.
        assertNotNull(wildlife.raidUnprotectedStock(chronicle, now, true),
            "unfolded stock beside hunting wolves must be at risk in the dark");

        // Raise the fold and clear the rest window: the same night, they are safe.
        UUID pen = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Goat fold','ACTIVE',?)", pen, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'GOAT_FOLD','COMPLETED',100,?,100)", pen, ts);
        jdbc.update("UPDATE wildlife_bond SET last_raid_at=NULL WHERE chronicle_id=?", chronicle);

        assertNull(wildlife.raidUnprotectedStock(chronicle, now, true),
            "a hurdle ring is exactly what a keeper folds goats into at night, and it must work");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
