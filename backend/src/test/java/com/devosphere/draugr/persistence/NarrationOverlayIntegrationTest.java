package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tier. Proves the narration-overlay contract against the REAL migrated schema and
 * the REAL read paths — the fix for the {@code prevent_chronicle_action_mutation} violation, where
 * AI narration was written back with an illegal {@code UPDATE chronicle_action}.
 *
 * <p>Two things are pinned here, deterministically (no model, no key, no router dependence):
 * <ol>
 *   <li>the append-only trigger really guards {@code chronicle_action} on the live table; and</li>
 *   <li>an overlay row is {@code COALESCE}d over the base narration by every read path
 *       (journey/archive/PDF and the scroll-back history), while the immutable base row is
 *       left untouched.</li>
 * </ol>
 *
 * <p>The full AI-drive (router → refine → overlay) is unit-tested against a stub in
 * {@code SimulationNarratorTest} and reproduced as a SQL contract replay in
 * {@code tests/regression/immutability-and-overlay.sql}; here we assert the persistence and
 * read-path wiring the deterministic layer owns.
 *
 * <p>The container starts after a Docker-availability assumption, so the class skips gracefully
 * where Testcontainers cannot reach a Docker engine and never fails a local {@code mvn test}.
 */
@SpringBootTest
class NarrationOverlayIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the narration-overlay integration test");
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

    @Autowired ChronicleService chronicles;
    @Autowired ChronicleActionService actions;
    @Autowired JdbcTemplate jdbc;

    private ChronicleActionService.ActionResult awakenAndAct() {
        chronicles.awaken();
        // A quiet, always-deterministic act that resolves to a durable chronicle_action row.
        return actions.resolve("rest quietly for a while");
    }

    @Test
    void immutabilityTriggerGuardsTheRealChronicleActionTable() {
        ChronicleActionService.ActionResult result = awakenAndAct();
        // The exact operation the old code did — writing narration back onto the ledger row —
        // must be rejected by the append-only trigger on the live table.
        DataAccessException rejected = assertThrows(DataAccessException.class,
            () -> jdbc.update("UPDATE chronicle_action SET narration = ? WHERE id = ?", "a forbidden rewrite", result.actionId()),
            "UPDATE chronicle_action must be blocked by prevent_chronicle_action_mutation");
        assertTrue(String.valueOf(rejected.getMostSpecificCause().getMessage()).contains("immutable history"),
            "the rejection must come from the immutability trigger");
    }

    @Test
    void overlayNarrationIsCoalescedIntoHistoryAndJourneyWhileBaseRowIsUntouched() {
        ChronicleActionService.ActionResult result = awakenAndAct();
        UUID actionId = result.actionId();
        UUID chronicleId = chronicles.active().id();
        String deterministic = jdbc.queryForObject("SELECT narration FROM chronicle_action WHERE id = ?", String.class, actionId);
        String enriched = deterministic + " The quiet settles over the clearing like dusk.";

        // Persist the displayed prose as an overlay — a fresh row, never a write-back (so this
        // INSERT itself proves the immutable row need not be touched to keep the AI narration).
        jdbc.update("INSERT INTO chronicle_action_narration (action_id, narration, model) VALUES (?, ?, ?)",
            actionId, enriched, "claude-haiku-4-5");

        // Scroll-back history returns the enriched prose the player saw live.
        List<ChronicleActionService.NarrationEntry> history = actions.narrationHistory(null, null, 20).entries();
        String shownInHistory = history.stream().filter(e -> e.id().equals(actionId)).map(ChronicleActionService.NarrationEntry::narration).findFirst().orElse(null);
        assertEquals(enriched, shownInHistory, "history must COALESCE the overlay over the base narration");

        // The journey archive (which also backs the PDF export) returns the same enriched prose.
        List<ChronicleService.JourneyEntry> journey = chronicles.journey(chronicleId).entries();
        assertTrue(journey.stream().anyMatch(e -> enriched.equals(e.narration())),
            "the journey archive and PDF must show the enriched narration");

        // And the immutable base row is provably unchanged.
        String baseAfter = jdbc.queryForObject("SELECT narration FROM chronicle_action WHERE id = ?", String.class, actionId);
        assertEquals(deterministic, baseAfter, "the append-only base narration must never be altered");
    }
}
