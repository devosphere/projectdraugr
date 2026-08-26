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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generative pipeline's Stage 1 — dynamic interpretation — actually fires (#244). The pipeline
 * (Interpreter→Architect→Narrator) is built but disabled by default, so the "map a miss onto existing
 * processes and run them" capability has never been exercised end-to-end. This turns the master switch on
 * with a dummy key and a STUB {@link LanguageModel} (no network, no real key), and proves that
 * {@link RuntimeAuthoringService#attempt} takes a novel phrasing the deterministic classifier would miss,
 * asks the (stubbed) Interpreter which existing processes compose it, and runs them through the same gated
 * {@code executeProcess} — producing a real result. This is the exact capability #244 says never fires.
 *
 * <p>The stub returns an existing verified process key for the Interpreter's prompt and empty otherwise, so
 * only the compose path runs; the Architect authoring path is not reached. Skips gracefully without Docker.
 */
@SpringBootTest
@TestPropertySource(properties = {"draugr.ai.enabled=true", "draugr.ai.api-key=test-stub-key-not-real"})
class RuntimeComposePipelineIntegrationTest {

    /** A deterministic stand-in for the Anthropic client: the Interpreter's prompt yields an existing process
     *  key; every other call yields empty. No network, and the dummy key is never used to reach a real model. */
    @TestConfiguration
    static class StubAiConfig {
        @Bean
        @Primary
        LanguageModel stubLanguageModel() {
            return (model, system, user) ->
                (system != null && system.contains("EXISTING crafting/processing steps"))
                    ? Optional.of("char_charcoal_clamp")
                    : Optional.empty();
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

    private int count(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void theInterpreterComposesANovelActionIntoExistingProcessesAndRunsThem() {
        // The master switch and the interpreter agent are actually on in this context (a dummy key is present).
        assertTrue(aiProps.isUsable(), "the AI master must be on with a key present for the pipeline to run");
        assertTrue(aiProps.isInterpreterActive(), "the Interpreter agent must be active");

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
        Timestamp ts = Timestamp.from(now);

        // A lit fire and three branches — the inputs the composed process (char_charcoal_clamp) needs.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,960,?)", pit, ts);
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        assertEquals(0, count(chronicle, "charcoal"), "start with no charcoal");

        // A novel phrasing the deterministic classifier would miss — no "char"/"charcoal clamp" keyword. The
        // stubbed Interpreter maps it to the existing char_charcoal_clamp process, and the pipeline runs it.
        Optional<String[]> result = authoring.attempt(chronicle, chunk, "prepare a batch of fuel for the smelt from these branches", now);

        assertTrue(result.isPresent(), "the compose pipeline must resolve the action into a real result (#244 Stage 1)");
        assertEquals("SUCCEEDED", result.get()[0], () -> "the composed process must run and succeed: " + result.get()[1]);
        assertTrue(count(chronicle, "charcoal") >= 6, () -> "composing the action into char_charcoal_clamp must have produced charcoal, proving the interpreter path fired end-to-end");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
