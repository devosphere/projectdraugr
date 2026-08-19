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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing-desk regression (#207 heritage KNOWLEDGE_STATION). The world's heritage names a documentation
 * workstation, but nothing read one — and the wooden desk a Chronicle can already build (CRAFT_DESK) was a
 * dead-craft: raised, then read by no mechanic. Now a steady desk standing on the ground is the documentation
 * workstation the heritage names: putting marks to a page — writing a record, revising one, sketching a map —
 * goes quicker with a proper surface to work at than balancing the page on your knee.
 *
 * <p>Proven end to end from the built desk (not a fabricated row): the same map-sketch takes strictly less time
 * with a desk standing than without. Skips gracefully without Docker.
 */
@SpringBootTest
class WritingDeskIntegrationTest {

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
    @Autowired com.devosphere.draugr.action.ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Minutes of simulated time one action consumes — the clock advances by exactly the action's duration. */
    private long elapsedMinutes(String action) {
        Instant before = ticks.current().simulatedAt();
        actions.resolve(action);
        return Duration.between(before, ticks.current().simulatedAt()).toMinutes();
    }

    @Test
    void aBuiltDeskEasesTheWorkOfWriting() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // No desk on this ground: the baseline setting-up time for a map sketch (the action may want for a surface
        // or charcoal, but the clock advances by its duration regardless — it is the duration we are measuring).
        long withoutDesk = elapsedMinutes("I sketch a map of the valley.");
        assertTrue(withoutDesk > 0, "the sketch must take measurable time (else the test proves nothing)");

        // Build a real wooden desk from carried stock — the full acquisition path, closing the desk's dead-craft.
        Instant now = ticks.current().simulatedAt();
        for (int i = 0; i < 5; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        com.devosphere.draugr.action.ChronicleActionService.ActionResult built = actions.resolve("I make a desk.");
        assertEquals("SUCCEEDED", built.outcome(), () -> "building a wooden desk must succeed: " + built.perception());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "WHERE w.current_location_id=? AND i.item_key='wooden_desk' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk),
                "the desk must stand as a persistent surface at the chunk");

        // With the desk standing, the same sketch takes strictly less setting-up.
        long withDesk = elapsedMinutes("I sketch a map of the valley.");
        assertTrue(withDesk < withoutDesk,
                () -> "a built desk must ease the work of writing (withDesk=" + withDesk + ", withoutDesk=" + withoutDesk + ") (#207)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
