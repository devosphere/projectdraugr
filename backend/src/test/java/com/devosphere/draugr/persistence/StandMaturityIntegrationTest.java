package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Young growth vs mature timber (EPIC #200 forestry / #201 tree condition). A stand cut to nothing and grown back is
 * young — thin poles, not sawlogs — until it matures over its species' regrowth span. Felling young growth yields a
 * single thin log, the standing cost of stripping a wood bare rather than harvesting it selectively; an old natural
 * stand gives its full timber.
 *
 * <p>Proven: a freshly established (young) oak stand fells to one thin log and says so; a mature natural stand fells
 * without that penalty. Skips gracefully without Docker.
 */
@SpringBootTest
class StandMaturityIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int logsOnGround(UUID chunk) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='oak_log' AND w.current_location_id=? AND w.current_owner_id IS NULL AND w.lifecycle_state='ACTIVE'",
                Integer.class, chunk);
        return n == null ? 0 : n;
    }

    @Test
    void youngGrowthYieldsThinPolesWhereAMatureStandGivesTimber() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        List<UUID> forest = jdbc.queryForList(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        Assumptions.assumeTrue(forest.size() >= 2, "need two forest chunks to compare young and mature stands");
        UUID youngChunk = forest.get(0), matureChunk = forest.get(1);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", now, "TEST_SEED");

        // A young oak stand — just established, so its cohort is far younger than oak's 365-day regrowth span.
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key IN (SELECT flora_key FROM flora_definition WHERE organism_type='TREE')", youngChunk);
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity, established_at) VALUES (?, 'oak', 8, 16, ?)", youngChunk, Timestamp.from(now));
        String[] youngFell = items.fellTree(chronicle, youngChunk, now);
        assertEquals("SUCCEEDED", youngFell[0], () -> "felling a young stand still succeeds: " + youngFell[1]);
        assertTrue(youngFell[1].toLowerCase(Locale.ROOT).contains("young"),
                () -> "felling young growth must read as young growth: " + youngFell[1]);
        assertEquals(1, logsOnGround(youngChunk), "young growth must yield a single thin log");

        // A mature natural stand — no recorded cohort, so it is old and gives its full timber, with no young penalty.
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key IN (SELECT flora_key FROM flora_definition WHERE organism_type='TREE')", matureChunk);
        String[] matureFell = items.fellTree(chronicle, matureChunk, now);
        assertEquals("SUCCEEDED", matureFell[0], () -> "felling a mature stand succeeds: " + matureFell[1]);
        assertFalse(matureFell[1].toLowerCase(Locale.ROOT).contains("young"),
                () -> "a mature natural stand must not read as young growth: " + matureFell[1]);
        assertTrue(logsOnGround(matureChunk) >= 1, "a mature stand must yield timber");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
