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
 * Monster-territory retaliation regression (EPIC #207, story #210 first tier). Where ordinary wildlife quits a
 * disturbed range — flees, shifts, or declines — a monster does the opposite: intruded upon in its lair it is
 * roused and turns on the intruder (HUNTING), and it holds its ground rather than migrating or declining. The
 * way to be rid of it is to leave it be until the disturbance decays, or to face it — not to drive it off.
 * Proven end to end: a MONSTER-site population on heavily disturbed ground is HUNTING (not FLEEING), and stays on
 * its own chunk with its numbers intact.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class DisturbanceMonsterRetaliationIntegrationTest {

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

    @Test
    void anIntrudedMonsterLairRetaliatesAndHoldsGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        // A forest chunk with a same-biome neighbour — so that, were the monster ordinary wildlife, it WOULD have
        // a viable route and migrate; proving it stays is proving the monster exclusion, not a lack of options.
        java.util.Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id AS here, c.world_id AS world FROM world_chunk c " +
                "WHERE c.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id " +
                "AND n.biome='TEMPERATE_FOREST' AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID chunk = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        Instant base = Instant.parse("2026-06-01T12:00:00Z");

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Bog warden lair',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'MONSTER','Bog warden lair',400)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','NOCTURNAL',3,5,'RESTING',?)", pop, site, Timestamp.from(base));

        // The lair's ground is worked hard — heavy enough that an ordinary population would migrate or decline.
        wildlife.recordDisturbance(chunk, "PREDATION", 80, base);
        sim.advanceTo(base.plus(Duration.ofHours(1)));

        assertEquals("HUNTING", jdbc.queryForObject("SELECT behavior_state FROM wildlife_population WHERE id=?", String.class, pop),
                "an intruded monster lair must rouse and hunt the intruder, not flee (#210)");
        assertEquals(chunk, jdbc.queryForObject("SELECT chunk_id FROM ecology_site WHERE id=?", UUID.class, site),
                "a monster holds its lair — it must NOT migrate off disturbed ground the way ordinary wildlife does");
        assertEquals(3, (int) jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop),
                "a monster does not decline from the disturbance — it retaliates instead");
        Integer moved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type IN ('MIGRATED','DECLINED')", Integer.class, site);
        assertEquals(0, moved, "a monster lair must neither migrate nor decline from disturbance (#210)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
