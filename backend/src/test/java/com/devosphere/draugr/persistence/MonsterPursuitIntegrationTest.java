package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Monster-pursuit regression (EPIC #207, story #210 third tier). A monster roused to its utmost (PACK_HUNT) at
 * its lair follows the intruder onto neighbouring ground rather than letting them walk away: a Chronicle who
 * heavily provokes a lair and then steps to the next chunk is still hunted — from a chunk away, with a beat to
 * react. Proven deterministically over a fixed set of encounters: an escalated monster one chunk away lands
 * ambushes that the same monster, de-escalated, does not.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class MonsterPursuitIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Count landed ambushes over a fixed id set, resetting the pursuer to PACK_HUNT before each (an ambush
     *  drops it to ALERT), so every roll faces the same escalated threat. */
    private int pursuitAmbushes(UUID chronicle, UUID chunk, UUID pursuer, Instant now, java.util.List<UUID> ids) {
        int hits = 0;
        for (UUID a : ids) {
            jdbc.update("UPDATE wildlife_population SET behavior_state='PACK_HUNT' WHERE id=?", pursuer);
            if (wildlife.passiveEncounter(chronicle, chunk, a, now, "LOW") != null) hits++;
        }
        return hits;
    }

    @Test
    void anEscalatedMonsterPursuesTheChronicleOntoTheNextChunk() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        java.util.Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id AS here, c.world_id AS world, c.grid_x AS gx, c.grid_y AS gy FROM world_chunk c " +
                "WHERE c.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id " +
                "AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1) ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID chunk = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        int gx = (int) row.get("gx"), gy = (int) row.get("gy");
        UUID adjacent = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE world_id=? AND abs(grid_x-?)+abs(grid_y-?)=1 ORDER BY grid_y, grid_x LIMIT 1",
                UUID.class, world, gx, gy);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // The Chronicle's own ground carries no immediate threat — any local predators are settled — so the only
        // danger is what pursues from the next chunk.
        jdbc.update("UPDATE wildlife_population SET behavior_state='RESTING' WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);

        // A monster lair on the neighbouring ground, roused to its utmost.
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dusk prowler lair',?)", site, adjacent);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'MONSTER','Dusk prowler lair',400)", site, world, adjacent);
        UUID pursuer = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','NOCTURNAL',3,5,'PACK_HUNT',?)", pursuer, site, ts);

        java.util.Random rnd = new java.util.Random(42);
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) ids.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // Escalated and one chunk away: the pursuit lands.
        int pursuing = pursuitAmbushes(chronicle, chunk, pursuer, now, ids);
        assertTrue(pursuing > 0, "a fully-escalated monster must pursue the Chronicle onto the next chunk (#210)");

        // The same monster, no longer escalated, does not follow onto the next ground.
        jdbc.update("UPDATE wildlife_population SET behavior_state='RESTING' WHERE id=?", pursuer);
        int settled = 0;
        for (UUID a : ids) if (wildlife.passiveEncounter(chronicle, chunk, a, now, "LOW") != null) settled++;
        assertEquals(0, settled, "a monster that is not escalated must not pursue onto neighbouring ground");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
