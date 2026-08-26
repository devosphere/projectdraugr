package com.devosphere.draugr.persistence;

import com.devosphere.draugr.ai.AiProperties;
import com.devosphere.draugr.ai.LanguageModel;
import com.devosphere.draugr.ai.RuntimeAuthoringService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generative pipeline's Stage 2 — runtime authoring of a NEW scoped mechanic — actually fires (#244).
 * This is the pipeline's headline capability ("runtime object/process creation never fires"): when the
 * Interpreter cannot compose a novel procedure from existing processes, the Architect drafts a new recipe,
 * the deterministic gate enforces physics, the QA critic judges plausibility, and on pass the scoped
 * item + process rows are written and the mechanic runs — all without ever touching canon.
 *
 * <p>Proven with a STUB {@link LanguageModel} (dummy key, no network): the Interpreter declines (empty),
 * the Architect returns a mass-balanced, category-agreeing draft, the QA critic passes, and the pipeline
 * writes a chronicle-scoped {@code item_definition} + {@code material_process}, runs it to produce the new
 * item, logs the discovery, and leaves the world Auditor-consistent. Skips gracefully without Docker.
 */
@SpringBootTest
@TestPropertySource(properties = {"draugr.ai.enabled=true", "draugr.ai.api-key=test-stub-key-not-real"})
class RuntimeAuthorPipelineIntegrationTest {

    /** Deterministic stand-in for the Anthropic client: the Interpreter declines so the Architect path runs;
     *  the Architect returns one valid draft (3× plant_fiber → a 200g cord, keyword "twist" ⇒ PROCESS); QA passes. */
    @TestConfiguration
    static class StubAiConfig {
        @Bean
        @Primary
        LanguageModel stubLanguageModel() {
            String draft = "{\"processKey\":\"twist_fibre_cord\",\"category\":\"PROCESS\","
                + "\"keywords\":\"twist fibre into cord,twist,ply the fibre\",\"subjects\":\"fibre,cord\","
                + "\"toolClass\":null,\"inputs\":[{\"itemKey\":\"plant_fiber\",\"quantity\":3}],"
                + "\"outputItemKey\":\"twisted_fibre_cord\",\"outputQty\":1,"
                + "\"narration\":\"You twist the loose fibres against your thigh until they bind into a length of cord.\","
                + "\"newItems\":[{\"itemKey\":\"twisted_fibre_cord\",\"displayName\":\"Twisted fibre cord\","
                + "\"category\":\"MATERIAL\",\"unitMassGrams\":200,\"unitVolumeMl\":140}]}";
            return (model, system, user) -> {
                if (system == null) return Optional.empty();
                if (system.contains("a sequence of EXISTING")) return Optional.empty();            // Interpreter declines
                if (system.contains("author ONE primitive-survival")) return Optional.of(draft);   // Architect drafts
                if (system.contains("review a proposed primitive-survival")) return Optional.of("PASS"); // QA passes
                return Optional.empty();
            };
        }
    }

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
    @Autowired RuntimeAuthoringService authoring;
    @Autowired PhysicalItemService items;
    @Autowired AiProperties aiProps;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theArchitectAuthorsAScopedMechanicThatGatesQasWritesRunsAndStaysConsistent() {
        assertTrue(aiProps.isAuthoringActive(), "the Architect (authoring) agent must be active with a key present");

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
        Instant now = ticks.current().simulatedAt();

        // The inputs the drafted recipe consumes: three lengths of plant fibre.
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_SEED");

        // A novel procedure with no existing process (the Interpreter declines) — the Architect must draft it.
        Optional<String[]> result = authoring.attempt(chronicle, chunk, "roll the loose fibres into a length of cord by hand", now);

        assertTrue(result.isPresent(), "the authoring pipeline must resolve the novel action into a real result (#244 Stage 2)");
        assertEquals("SUCCEEDED", result.get()[0], () -> "the authored mechanic must run and succeed: " + result.get()[1]);

        // A chronicle-scoped process and item were written (never canonical — they carry the discoverer's id).
        Integer scopedProcess = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process WHERE discovered_by_chronicle_id=? AND process_key LIKE 'twist_fibre_cord%'", Integer.class, chronicle);
        assertTrue(scopedProcess != null && scopedProcess >= 1, "a chronicle-scoped material_process must have been authored");
        Integer scopedItem = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition WHERE discovered_by_chronicle_id=? AND item_key LIKE 'twisted_fibre_cord%'", Integer.class, chronicle);
        assertTrue(scopedItem != null && scopedItem >= 1, "the recipe's brand-new item must have been defined (scoped)");

        // The Chronicle now holds the authored item — the mechanic actually ran and produced it.
        Integer held = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key LIKE 'twisted_fibre_cord%' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        assertTrue(held != null && held >= 1, "running the authored mechanic must have produced the new item for the Chronicle");

        // The discovery is logged with a passing gate and QA verdict.
        Integer passed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_tech_discovery WHERE chronicle_id=? AND gate_result='PASS' AND qa_verdict='PASS'", Integer.class, chronicle);
        assertTrue(passed != null && passed >= 1, "a passing tech discovery must be recorded (#208 evidence)");

        assertTrue(auditor.inspect().consistent(), () -> "an AI-authored scoped mechanic must leave the world Auditor-consistent: " + auditor.inspect().violations());
    }
}
