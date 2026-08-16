package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edged-tool usefulness regression (dead-craft audit #257, EPIC #123). The adze, chisel, burin, and stone/bone
 * scrapers are worked edges, but hasCuttingTool knew only the knife, hatchet, and flake — so crafting one of
 * them satisfied no CUTTING gate and did nothing. Now each counts as the blade a knife-gated act needs. Proven
 * end to end (carving a blaze needs a blade: it fails bare-handed and succeeds with an adze) and per tool.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class CuttingToolIntegrationTest {

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
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Set down every loosely-carried item onto the ground here, so the next check starts with no cutting
     *  tool to hand while the items stay ACTIVE and valid at a real location. */
    private void shedLooseItems(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? " +
                "WHERE current_owner_id=? AND object_type='ITEM' AND id NOT IN (SELECT item_id FROM equipment_attachment)", chunk, chronicle);
    }

    @Test
    void everyWorkedEdgeCountsAsACuttingTool() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();

        // Each newly-recognised edge, in isolation, must satisfy the cutting-tool gate.
        for (String edge : new String[]{"stone_adze", "stone_chisel", "flint_burin", "flint_scraper", "bone_scraper"}) {
            shedLooseItems(chronicle, chunk);
            assertFalse(items.hasCuttingTool(chronicle), edge + ": a Chronicle with no blade must not read as having a cutting tool");
            items.createCarriedItem(chronicle, edge, edge, now, "TEST_SEED");
            assertTrue(items.hasCuttingTool(chronicle), edge + ": a worked edge must count as a cutting tool (#257)");
        }

        // End to end: carving a blaze needs a blade. Bare-handed it fails; with only an adze it succeeds.
        shedLooseItems(chronicle, chunk);
        ChronicleActionService.ActionResult bare = actions.resolve("I carve a blaze into the bark of a tree.");
        assertEquals("FAILED", bare.outcome(), () -> "carving a blaze with no blade must fail: " + bare.perception());
        items.createCarriedItem(chronicle, "stone_adze", "Stone adze", now, "TEST_SEED");
        ChronicleActionService.ActionResult withAdze = actions.resolve("I carve a blaze into the bark of a tree.");
        assertEquals("SUCCEEDED", withAdze.outcome(), () -> "carving a blaze with an adze to cut it must succeed: " + withAdze.perception());

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
