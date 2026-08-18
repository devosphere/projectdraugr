package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Monster-escalation regression (EPIC #207, story #210 second tier). A monster's response to intrusion is graded:
 * a lightly disturbed lair (>=40) is roused and hunts the intruder (HUNTING), but where the disturbance is heavy
 * and sustained (>=70) it escalates and presses the hunt to its utmost (PACK_HUNT — the state the ambush model
 * reads as the hardest press), coming for whoever keeps intruding. Proven deterministically: the same lair reads
 * HUNTING under light disturbance and PACK_HUNT once the disturbance is driven heavy.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class DisturbanceMonsterEscalationIntegrationTest {

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
    @Autowired WildlifeSimulationService sim;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String behaviorOf(UUID pop) { return jdbc.queryForObject("SELECT behavior_state FROM wildlife_population WHERE id=?", String.class, pop); }

    @Test
    void aMonsterEscalatesFromHuntingToPressingWhenDisturbanceGoesHeavy() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant t0 = Instant.parse("2026-06-01T12:00:00Z");

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Bog warden lair',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'MONSTER','Bog warden lair',400)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','NOCTURNAL',3,5,'RESTING',?)", pop, site, Timestamp.from(t0));

        // Light disturbance — enough to rouse, not to enrage.
        wildlife.recordDisturbance(chunk, "PREDATION", 55, t0);
        sim.advanceTo(t0.plus(Duration.ofHours(1)));
        assertEquals("HUNTING", behaviorOf(pop), "a lightly disturbed lair rouses and hunts (#210)");

        // Drive it heavy — the monster escalates and presses the hunt to its utmost.
        wildlife.recordDisturbance(chunk, "PREDATION", 50, t0.plus(Duration.ofHours(1)));
        sim.advanceTo(t0.plus(Duration.ofHours(2)));
        assertEquals("PACK_HUNT", behaviorOf(pop), "a heavily, sustainedly disturbed lair escalates and presses the hunt (#210)");

        // It still holds its lair — escalation is fiercer defence, not flight.
        assertEquals(chunk, jdbc.queryForObject("SELECT chunk_id FROM ecology_site WHERE id=?", UUID.class, site),
                "an escalating monster holds its lair — it does not migrate off");
        assertEquals(3, (int) jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop),
                "an escalating monster does not decline from the disturbance");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
