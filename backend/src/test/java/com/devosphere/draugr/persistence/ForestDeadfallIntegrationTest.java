package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.ecology.ResourceEcologyService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forest-deadfall regression (EPIC #200 forestry — #201 deadfall / habitat value). Firewood (\code{dry_branch}) is a
 * finite, self-replenishing chunk resource, but its abundance ignored the standing woodland — bare ground shed as
 * much deadfall as an old wood. Now a well-treed chunk is a far richer source of deadfall (a woodland bonus on top
 * of the bare-ground base), and clearing the wood removes that bonus, so a felled-out chunk yields less firewood
 * than a standing one — but never less than bare ground, so gathering firewood anywhere still works as before.
 *
 * <p>Proven deterministically: on the same biome, a treed chunk yields strictly more deadfall than a treeless one.
 * Skips gracefully without Docker.
 */
@SpringBootTest
class ForestDeadfallIntegrationTest {

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
    @Autowired ResourceEcologyService resources;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aWellTreedChunkShedsMoreDeadfallThanBareGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        // Two chunks of the same biome (same bare-ground base), so the only difference measured is the woodland.
        java.util.List<UUID> forestChunks = jdbc.queryForList(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        UUID treed = forestChunks.get(0);
        UUID cleared = forestChunks.get(1);
        Instant now = ticks.current().simulatedAt();

        // A standing oak wood on the first; the second cut out to nothing. A cut-out stand is a recorded stand at
        // zero (not an absent one) — an absent record is instead read as pristine natural woodland (#200), so "bare"
        // here must be the worked-out state, a row at quantity zero.
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key IN (SELECT flora_key FROM flora_definition WHERE organism_type='TREE')", treed);
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity) VALUES (?,?,?,?)", treed, "oak", 5, 5);
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key IN (SELECT flora_key FROM flora_definition WHERE organism_type='TREE')", cleared);
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity) VALUES (?,?,?,?)", cleared, "oak", 0, 16);

        // Take all the standing deadfall from each (the first take fixes the chunk's capacity from its woodland).
        int fromWood = resources.take(treed, "dry_branch", 100, now);
        int fromBare = resources.take(cleared, "dry_branch", 100, now);

        assertTrue(fromWood > fromBare,
                () -> "a standing wood must shed more deadfall than bare ground (wood=" + fromWood + ", bare=" + fromBare + ") (#200/#201)");
        assertTrue(fromBare > 0, "bare ground must still yield some firewood — the woodland is a bonus, never a floor below the base");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
