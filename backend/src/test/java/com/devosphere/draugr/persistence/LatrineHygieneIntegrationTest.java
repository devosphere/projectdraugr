package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.construction.ConstructionService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camp-latrine hygiene regression (#127/#218, EPIC #123). Low hygiene already drives an illness pressure and
 * hygiene decays every hour, but a Chronicle had no way to keep a camp clean. A built LATRINE now halves the
 * passive hygiene loss while a Chronicle stands at its chunk — a clean camp is a healthier one. Proven
 * deterministically: over the same span of hours from the same starting hygiene, a Chronicle at a latrine loses
 * strictly less hygiene than one without.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class LatrineHygieneIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** From a benign baseline (hygiene 80) at {@code base}, advance {@code hours} and return the hygiene left. */
    private double hygieneAfter(UUID chronicle, Instant base, int hours) {
        jdbc.update("UPDATE chronicle_physiology SET hygiene_level=80, hours_without_food=50, hours_without_water=5, " +
                "energy_level=90, core_temperature_c=37, wetness_level=0, bladder_level=0, bowel_level=0, sleep_debt_hours=0, " +
                "pain_level=0, stress_level=0, injury_severity=0, illness_severity=0, blood_loss_ml=0, last_metabolic_update=? WHERE chronicle_id=?",
                Timestamp.from(base), chronicle);
        physiology.advanceTo(base.plus(Duration.ofHours(hours)));
        return jdbc.queryForObject("SELECT hygiene_level FROM chronicle_physiology WHERE chronicle_id=?", Double.class, chronicle);
    }

    @Test
    void aCampLatrineSlowsHygieneLoss() {
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
        Instant base = ticks.current().simulatedAt();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        int hours = 40;
        // No latrine at camp: the baseline hygiene loss over the span.
        double withoutLatrine = hygieneAfter(chronicle, base, hours);
        assertTrue(withoutLatrine < 80, "hygiene must actually fall over the span (else the test proves nothing)");

        // Dig a latrine from carried tools and screen material — the full acquisition path.
        items.createCarriedItem(chronicle, "digging_stick", "Digging stick", base, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "reed_bundle", "Reed bundle", base, "TEST_SEED");
        String[] built = construction.buildLatrine(chronicle, chunk, base);
        assertEquals("SUCCEEDED", built[0], () -> "digging a latrine must succeed: " + built[1]);
        Integer latrines = jdbc.queryForObject(
                "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='LATRINE' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, chunk);
        assertEquals(1, latrines, "the latrine must stand as a persistent construction at the chunk");

        // With the latrine, the same span from the same start loses less hygiene.
        double withLatrine = hygieneAfter(chronicle, base, hours);

        assertTrue(withLatrine > withoutLatrine,
                () -> "a camp latrine must slow hygiene loss (with=" + withLatrine + ", without=" + withoutLatrine + ") (#127/#218)");

        // The BUILD_LATRINE intent also routes and mends an existing latrine (a tick here is harmless — the
        // measurements are done), proving the acquisition path end to end and guarding against a classifier collision.
        items.createCarriedItem(chronicle, "reed_bundle", "Reed bundle", base, "TEST_SEED");
        com.devosphere.draugr.action.ChronicleActionService.ActionResult reBuilt = actions.resolve("I dig a latrine.");
        assertEquals("SUCCEEDED", reBuilt.outcome(), () -> "the BUILD_LATRINE intent must route and mend: " + reBuilt.perception());

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
