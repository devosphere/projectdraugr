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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coppicing — a renewable wood harvest (EPIC #200 forestry / #204 coppice). Cutting the rods from living stools
 * yields a crop without felling the tree: the stand is never reduced, and the stools may be cut again once they have
 * rested and thrown up new growth. This is the counterpart to the clear-cutting penalty (#201) — the reward for
 * leaving a wood standing and working it sustainably.
 *
 * <p>Proven: coppicing yields rods and does NOT reduce the stand; cutting again at once fails (the stools have not
 * rested); and after the rest period it yields again. Skips gracefully without Docker.
 */
@SpringBootTest
class CoppicingIntegrationTest {

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

    private int rods(UUID chronicle) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='hazel_rod' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private int standQty(UUID chunk) {
        Integer n = jdbc.queryForObject("SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key='oak'", Integer.class, chunk);
        return n == null ? 0 : n;
    }

    @Test
    void coppicingYieldsRodsWithoutFellingAndOnlyAfterTheStoolsHaveRested() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have forest ground to coppice");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", now, "TEST_SEED"); // a blade to cut the rods

        // A standing oak wood to work.
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key IN (SELECT flora_key FROM flora_definition WHERE organism_type='TREE')", chunk);
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity) VALUES (?, 'oak', 8, 16)", chunk);

        // First cutting: yields rods, and the wood still stands — the tree count is unchanged.
        String[] first = items.coppice(chronicle, chunk, now);
        assertEquals("SUCCEEDED", first[0], () -> "coppicing a standing wood must succeed: " + first[1]);
        assertTrue(rods(chronicle) >= 2, "coppicing must yield a crop of rods");
        assertEquals(8, standQty(chunk), "coppicing must NOT fell the wood — the stand is unchanged");

        // Cutting again at once fails — the stools have not thrown up new growth yet.
        int rodsAfterFirst = rods(chronicle);
        String[] tooSoon = items.coppice(chronicle, chunk, now);
        assertFalse("SUCCEEDED".equals(tooSoon[0]), "the stools must not give a second crop before they have rested: " + tooSoon[1]);
        assertTrue(tooSoon[1].toLowerCase().contains("rest"), () -> "a too-soon cutting must read as needing rest: " + tooSoon[1]);
        assertEquals(rodsAfterFirst, rods(chronicle), "a failed cutting yields no rods");

        // After the stools have rested and thrown up new growth, they give again — and still without felling.
        Instant later = now.plus(120, ChronoUnit.DAYS);
        String[] again = items.coppice(chronicle, chunk, later);
        assertEquals("SUCCEEDED", again[0], () -> "rested stools must yield a fresh crop: " + again[1]);
        assertTrue(rods(chronicle) > rodsAfterFirst, "the second cutting must add more rods");
        assertEquals(8, standQty(chunk), "even over repeated coppicing the wood is never felled");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
