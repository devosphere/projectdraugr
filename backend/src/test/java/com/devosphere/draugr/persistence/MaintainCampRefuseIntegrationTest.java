package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maintain-camp refuse clean-up regression (EPIC #215, story #218 — sanitation counter-play). Refuse breaks down
 * slowly on its own and faster where a latrine stands (V140), but a Chronicle had no ACTIVE way to clean a camp.
 * Now tidying the camp carries off the filth of living there — the hands-on counter-play beside the latrine's
 * passive disposal — and a good tidy is worth doing even at a bare site that has grown foul.
 *
 * <p>Proven: a fouled camp is measurably cleaner after a tidy, and the intent routes (a "clean up the camp"
 * clears refuse), even where nothing is built to tend. Skips gracefully without Docker.
 */
@SpringBootTest
class MaintainCampRefuseIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int refuse(UUID chunk) {
        Integer v = jdbc.query("SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?", rs -> rs.next() ? rs.getInt(1) : null, chunk);
        return v == null ? 0 : v;
    }

    @Test
    void tidyingACampCarriesOffRefuse() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();

        // A fouled, bare camp — refuse on the ground, nothing built here to tend.
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,80,?) ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=80, last_updated_at=?",
                chunk, Timestamp.from(now), Timestamp.from(now));

        // Tidying it carries off filth even with nothing built to tend, and succeeds on the strength of that.
        String[] tidied = construction.maintainCamp(chronicle, chunk, now);
        assertEquals("SUCCEEDED", tidied[0], () -> "tidying a fouled bare camp must succeed on the clearing alone (#218): " + tidied[1]);
        int afterOne = refuse(chunk);
        assertTrue(afterOne < 80, () -> "tidying the camp must carry off refuse (was 80, now " + afterOne + ") (#218)");

        // The MAINTAIN_CAMP intent routes and clears further — a second tidy leaves the ground cleaner still.
        com.devosphere.draugr.action.ChronicleActionService.ActionResult routed = actions.resolve("I clean up the camp.");
        assertEquals("SUCCEEDED", routed.outcome(), () -> "the clean-up intent must route and clear: " + routed.perception());
        assertTrue(refuse(chunk) < afterOne, "a second tidy must leave the camp cleaner still");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
