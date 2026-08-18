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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scout monster-lair regression (#128/#123). EPIC #123 names goblin caves and monster lairs among the dangers a
 * Chronicle must be able to sense before forced contact — but scoutBoundary read only carnivorous wildlife, so a
 * lair of a non-carnivore monster (a hydra, a siren, a swarm) at the next chunk was invisible. threatAt now
 * flags any MONSTER-category site, whatever its ecological role, and reports it distinctly as a lair. Proven: a
 * MONSTER site with a NON-carnivore population two ground east is named as a lair to the east.
 *
 * <p>Reads scoutBoundary directly (no tick), so the fabricated lair is never disturbed. Skips without Docker.
 */
@SpringBootTest
class ScoutMonsterLairIntegrationTest {

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

    @Test
    void aScoutNamesAMonsterLairInTheNextChunk() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        // A chunk with ground to its east; fabricate a MONSTER-category lair there with a NON-carnivore
        // population, so only the site_category='MONSTER' path (not the carnivore path) can detect it.
        Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id AS here, c.world_id AS world, c.grid_x AS gx, c.grid_y AS gy FROM world_chunk c " +
                "WHERE c.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk e WHERE e.world_id=c.world_id AND e.grid_x=c.grid_x+1 AND e.grid_y=c.grid_y) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID chronicleChunk = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        int gx = (int) row.get("gx"), gy = (int) row.get("gy");
        UUID eastChunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, gx + 1, gy);
        Timestamp ts = Timestamp.from(ticks.current().simulatedAt());
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Bog warden lair',?)", site, eastChunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'MONSTER','Bog warden lair',400)", site, world, eastChunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','HERBIVORE','NOCTURNAL',2,5,'ALERT',?)", UUID.randomUUID(), site, ts);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chronicleChunk, chronicle);

        String read = wildlife.scoutBoundary(chronicle, chronicleChunk, "HIGH", 0.0).narration();
        String p = read.toLowerCase(Locale.ROOT);
        assertTrue(p.contains("the lair of"),
                () -> "a scout must name a monster lair as a lair, not miss it for want of a carnivore role (#128): " + read);
        assertTrue(p.contains("to the east"),
                () -> "the lair must be placed to the east where it was set (#128): " + read);

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
