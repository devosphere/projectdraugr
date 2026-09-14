package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guinea fowl and the llama (#79, V328): each a whole kept animal.
 *
 * <p>Eggs keep their season (V323), so the flock is visited in June and the clock put back afterwards.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class GuineaFowlAndLlamaIntegrationTest {

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

    private Timestamp clockBefore;

    @BeforeEach
    void inSeason() {
        clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(Instant.parse("2031-06-10T08:00:00Z")));
    }

    @AfterEach
    void restoreClock() {
        if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore);
    }

    @Test
    void bothAreWholeKeptAnimals() {
        for (String species : new String[]{"guinea_fowl", "llama"}) {
            assertTrue(jdbc.queryForObject("SELECT tamability FROM wildlife_species WHERE species_key=?", Integer.class, species) > 0, species + " can be kept");
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM tamed_yield WHERE species_key=?", Integer.class, species) > 0, species + " gives something");
            assertEquals(1, (int) jdbc.queryForObject("SELECT count(*) FROM breeding_profile WHERE species_key=?", Integer.class, species), species + " breeds");
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM wildlife_drop WHERE species_key=?", Integer.class, species) > 0, species + " leaves remains");
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM wildlife_sign WHERE species_key=?", Integer.class, species) >= 2, species + " can be tracked");
        }
        assertTrue(jdbc.queryForObject("SELECT haul_bonus_grams FROM draft_species WHERE species_key='llama'", Integer.class) > 0, "a llama carries a pack");
        assertFalse(jdbc.queryForObject("SELECT rideable FROM draft_species WHERE species_key='llama'", Boolean.class), "and is not ridden");
        assertTrue(jdbc.queryForObject("SELECT litter_max FROM breeding_profile WHERE species_key='guinea_fowl'", Integer.class) > 1,
            "a flock of guinea fowl grows by the clutch");
    }

    @Test
    void aKeptGuineaFowlLaysInSummer() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Only guinea fowl among this keeper's egg-layers, so the eggs are theirs.
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        Timestamp ts = Timestamp.from(Instant.now());
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept flock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept flock',30)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'guinea_fowl','OMNIVORE','DIURNAL',3,9,'FORAGING',?)", pop, site, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',95,12,?)", UUID.randomUUID(), chronicle, pop, ts);

        var eggs = wildlife.takeTamedYield(chronicle, Instant.now(), "collect the eggs");
        assertEquals("SUCCEEDED", eggs.outcome(), () -> "a kept guinea fowl flock lays in June: " + eggs.narration());
        assertTrue(jdbc.queryForObject(
            "SELECT count(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=? AND i.item_key='fowl_egg'",
            Integer.class, chronicle) > 0, "the eggs must be in hand");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
