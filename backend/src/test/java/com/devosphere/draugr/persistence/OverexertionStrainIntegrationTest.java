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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Overexertion-strain regression (EPIC #215, story #217 — labour physiology). Heavy labour drained energy but
 * had no bodily consequence for being pushed past what the body had left: a Chronicle could swing an axe on an
 * empty tank forever, taking only tiredness. Now driving heavy labour while already spent strains the body — a
 * pulled muscle, a wrenched back (pain plus a minor tissue injury, but no bleeding), kept in history as a STRAIN
 * condition event, easing only with rest and care. A real recovery need, never a silent reset.
 *
 * <p>Proven deterministically through a real action: felling a tree while exhausted strains; felling the same
 * tree well-rested does not. Skips gracefully without Docker.
 */
@SpringBootTest
class OverexertionStrainIntegrationTest {

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

    private void setEntering(UUID chronicle, int energy) {
        jdbc.update("UPDATE chronicle_physiology SET energy_level=?, injury_severity=0, pain_level=0, blood_loss_ml=0, " +
                "illness_severity=0, stress_level=0, hours_without_food=30, hours_without_water=4, sleep_debt_hours=0, " +
                "core_temperature_c=37, wetness_level=0, last_metabolic_update=? WHERE chronicle_id=?",
                energy, java.sql.Timestamp.from(ticks.current().simulatedAt()), chronicle);
    }

    private int strainEvents(UUID chronicle) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_condition_event WHERE chronicle_id=? AND condition_kind='STRAIN'", Integer.class, chronicle);
    }

    @Test
    void heavyLabourWhileSpentStrainsTheBodyButNotWhenRested() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        // An axe to fell with — heavy labour that reliably succeeds in a temperate forest.
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", ticks.current().simulatedAt(), "TEST_SEED");

        // Entered the work exhausted: felling strains the body, kept as a STRAIN event with a real injury.
        setEntering(chronicle, 10);
        com.devosphere.draugr.action.ChronicleActionService.ActionResult spent = actions.resolve("I fell a tree.");
        assertEquals("SUCCEEDED", spent.outcome(), () -> "felling must succeed to test overexertion: " + spent.perception());
        assertEquals(1, strainEvents(chronicle), "heavy labour entered exhausted must leave a STRAIN in history (#217)");
        assertTrue(jdbc.queryForObject("SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle) > 0,
                "the strain must be a real injury with a recovery need, not just tiredness (#217)");

        // Entered the same work well-rested: no strain — overexertion is about being spent, not the labour itself.
        setEntering(chronicle, 85);
        com.devosphere.draugr.action.ChronicleActionService.ActionResult rested = actions.resolve("I fell a tree.");
        assertEquals("SUCCEEDED", rested.outcome(), () -> "felling must succeed to test the rested case: " + rested.perception());
        assertEquals(1, strainEvents(chronicle), "heavy labour entered well-rested must NOT strain the body (#217)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
