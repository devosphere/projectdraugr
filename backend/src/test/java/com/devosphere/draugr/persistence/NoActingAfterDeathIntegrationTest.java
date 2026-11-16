package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A death during the elapsed time ends the act (#884). ChronicleActionService runs the clock over the whole span
 * before the intent branch, and the physiology tick at its end is where a death lands and the Chronicle's
 * possessions are laid on the ground. The branch then ran against a dead body: a kitted bow-drill retry was refused
 * for want of the kit the death had just put down. Here a Chronicle on the edge of fatal thirst gathers stones; the
 * gather must not happen, must not be charged as labour, and must not be told as anything but unfinished.
 * Skips without Docker.
 */
@SpringBootTest
class NoActingAfterDeathIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Awaken in temperate forest at June midday, with the body's thirst set and its clock in step with the world's. */
    private UUID awakenWithThirst(double hoursWithoutWater) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the approved world must contain a TEMPERATE_FOREST chunk");
        Instant noon = Instant.parse("2026-06-18T12:00:00Z");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        jdbc.update("UPDATE simulation_clock SET simulated_at=?", Timestamp.from(noon));
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=?, "
                  + "sleep_debt_hours=0, illness_severity=0, injury_severity=0, blood_loss_ml=0, core_temperature_c=37.0 WHERE chronicle_id=?",
            Timestamp.from(noon), hoursWithoutWater, summary.id());
        return summary.id();
    }

    private int gatherEffects(UUID actionId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_action_effect WHERE action_id=?", Integer.class, actionId);
        return n == null ? 0 : n;
    }

    @Test
    void aGatherTheChronicleDiesDuringIsNotDone() {
        // 72.95 hours: any act that takes more than three minutes crosses the 73-hour line in its own span.
        awakenWithThirst(72.95);

        ChronicleActionService.ActionResult gathered = actions.resolve("gather stones");

        assertTrue(gathered.died(), "the span must carry the Chronicle past the thirst line: " + gathered.perception());
        assertEquals("FAILED", gathered.outcome(), "an act died in is not a success, whatever it would have found");
        String said = gathered.perception().toLowerCase(Locale.ROOT);
        assertTrue(said.contains("unfinished"), "the act must be told as begun and not finished: " + gathered.perception());
        assertTrue(said.contains("thirst takes the last of you"), "the coda names the cause: " + gathered.perception());
        assertEquals(0, gatherEffects(gathered.actionId()), "nothing may be gathered on behalf of a body already gone");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void theSameGatherLivedThroughIsDone() {
        awakenWithThirst(0);

        ChronicleActionService.ActionResult gathered = actions.resolve("gather stones");

        assertFalse(gathered.died(), "a watered Chronicle survives a gather: " + gathered.perception());
        assertFalse(gathered.perception().toLowerCase(Locale.ROOT).contains("unfinished"),
            "a gather lived through is told as itself: " + gathered.perception());
        // The instrument the death test reads: a gather that ran writes its effect row, found stones or not, so
        // the 0 there means the branch did not run rather than that it found nothing.
        assertEquals(1, gatherEffects(gathered.actionId()), "a gather that ran records itself");
    }
}
