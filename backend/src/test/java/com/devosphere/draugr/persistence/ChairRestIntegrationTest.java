package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chair-rest regression (#207 built-furniture functionality). The wooden chair a Chronicle can craft (CRAFT_CHAIR)
 * was a dead-craft — raised, "the chair holds your weight", then read by no mechanic. Now a built seat on the
 * ground eases a sit-down rest more than the bare earth does: the humblest of camp comforts, ranked below a bed or
 * a shelter (you would not sit up in a chair when you could lie down), but above nothing.
 *
 * <p>Proven end to end from the built chair (not a fabricated row): from the same low-energy baseline over the same
 * span, a Chronicle with a chair standing recovers strictly more than one without. Skips gracefully without Docker.
 */
@SpringBootTest
class ChairRestIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** From a fixed low-energy baseline, rest {@code minutes} and return the energy recovered (isolated from the
     *  tick's passive metabolic drain by calling rest directly). */
    private double energyRecovered(UUID chronicle, int minutes) {
        jdbc.update("UPDATE chronicle_physiology SET energy_level=20, sleep_debt_hours=0, pain_level=0, stress_level=0, " +
                "wetness_level=0, hours_without_food=40, hours_without_water=5, illness_severity=0, injury_severity=0 WHERE chronicle_id=?", chronicle);
        physiology.rest(chronicle, minutes);
        return jdbc.queryForObject("SELECT energy_level FROM chronicle_physiology WHERE chronicle_id=?", Double.class, chronicle) - 20;
    }

    @Test
    void aBuiltChairEasesARest() {
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

        // No seat on this ground — no bed or shelter either — so a rest recovers at the bare-earth rate.
        double withoutChair = energyRecovered(chronicle, 480);
        assertTrue(withoutChair > 0, "a rest must recover some energy (else the test proves nothing)");

        // Build a real wooden chair from carried stock — the full acquisition path, closing the chair's dead-craft.
        Instant now = ticks.current().simulatedAt();
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        com.devosphere.draugr.action.ChronicleActionService.ActionResult built = actions.resolve("I make a chair.");
        assertEquals("SUCCEEDED", built.outcome(), () -> "building a wooden chair must succeed: " + built.perception());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "WHERE w.current_location_id=? AND i.item_key='wooden_chair' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk),
                "the chair must stand as a persistent seat at the chunk");

        // With the seat standing, the same rest from the same baseline recovers strictly more.
        double withChair = energyRecovered(chronicle, 480);
        assertTrue(withChair > withoutChair,
                () -> "a built chair must ease a rest (withChair=" + withChair + ", withoutChair=" + withoutChair + ") (#207)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
