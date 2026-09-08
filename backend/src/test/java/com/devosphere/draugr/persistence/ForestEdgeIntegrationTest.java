package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.ecology.ResourceEcologyService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wood's edge carries more than the wood (#159).
 *
 * <p>An ecotone is the line where two kinds of country meet, and it carries more than either side because both
 * are within reach of it — the forager on a wood's edge has the trees at their back and the open in front. That
 * is a real effect, and this world already models the shape of it: any RESOURCE site lifts what the ground gives
 * up. So an edge placed truthfully is functional the moment it exists, with no new code at all.
 *
 * <p><b>Placed truthfully is the whole point.</b> A "forest edge" dropped anywhere among the trees would be the
 * right name on ground that does not answer to it — which is exactly what {@code marker()}'s silent fallback
 * produces. {@code WorldMarkerPlacementTest} holds the placement across five seeds without a database; this holds
 * the consequence.
 *
 * <p>The ticket's {@code woodland_meadow} is deliberately absent: it is the same line seen from the grass, and
 * adding it would be a second name for one effect.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class ForestEdgeIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** The world must place the edge, and place it on forest that touches the open. */
    @Test
    void theWorldPlacesAnEdgeWhereTheWoodMeetsTheOpen() {
        world();

        UUID edge = jdbc.query("SELECT chunk_id FROM ecology_site WHERE site_kind='Forest edge' LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        assertNotNull(edge, "the world must place a forest edge");

        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, edge);
        assertEquals("TEMPERATE_FOREST", biome, "an edge of the wood is in the wood, looking out");

        Integer open = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_chunk n JOIN world_chunk c ON c.id=? AND n.world_id=c.world_id " +
            "WHERE abs(n.grid_x-c.grid_x) + abs(n.grid_y-c.grid_y) = 1 AND n.biome IN ('GRASSLAND','COAST')",
            Integer.class, edge);
        assertTrue(open != null && open > 0,
            "an edge that touches no open ground is not an edge — it is a marker in the middle of a wood");
    }

    /**
     * And the consequence. {@code take} seeds the ground's stock from its profile, so what it gives back on a
     * fresh chunk is that chunk's capacity — a deterministic reading of the same number the forager meets.
     */
    @Test
    void theEdgeGivesUpMoreFibreThanPlainForest() {
        world();
        Instant now = Instant.now();

        UUID edge = jdbc.query("SELECT chunk_id FROM ecology_site WHERE site_kind='Forest edge' LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        assertNotNull(edge, "the world must place a forest edge");

        UUID plain = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome='TEMPERATE_FOREST' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        assertNotNull(plain, "the world must have plain forest with nothing on it to compare against");

        jdbc.update("DELETE FROM world_chunk_resource WHERE chunk_id IN (?,?)", edge, plain);
        int fromEdge = resources.take(edge, "plant_fiber", 1000, now);
        int fromPlain = resources.take(plain, "plant_fiber", 1000, now);

        assertTrue(fromEdge > fromPlain,
            () -> "the wood's edge must carry more than the wood behind it (edge=" + fromEdge
                + ", plain forest=" + fromPlain + ")");
        assertTrue(fromPlain > 0,
            "and plain forest still gives fibre — this is a richer margin, not a poorer middle");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
