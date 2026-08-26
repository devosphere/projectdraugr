package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke in the air (EPIC #215, story #219 — a hazard must have grounded sensory evidence). The smoke a working lays
 * on the ground and drifts onto the neighbours (#369/#370) was, until now, an invisible wildlife-only number. A
 * Chronicle who looks around should smell and see it: woodsmoke hanging over a burn here, or a haze drifting in from
 * a fire on the next ground over — read before the fire itself is ever in sight.
 *
 * <p>Proven: a survey over ground with a fresh SMOKE disturbance reads woodsmoke here; a survey on a neighbour that
 * took the drift reads a haze drifting in; ground with no recent smoke reads none. Skips gracefully without Docker.
 */
@SpringBootTest
class SmokeInTheAirIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String surveyAt(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        ChronicleActionService.ActionResult r = actions.resolve("look around carefully");
        return r.perception() == null ? "" : r.perception().toLowerCase(Locale.ROOT);
    }

    @Test
    void aSurveyReadsWoodsmokeOnItsGroundAndDriftingInFromNearbyButNotWhereThereIsNone() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        UUID source = jdbc.queryForObject(
            "SELECT here.id FROM world_chunk here WHERE EXISTS (SELECT 1 FROM world_chunk n " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1) LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, source);
        UUID neighbour = jdbc.queryForObject(
            "SELECT n.id FROM world_chunk n JOIN world_chunk here ON here.id=? " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1 LIMIT 1", UUID.class, source);
        UUID far = jdbc.query(
            "SELECT n.id FROM world_chunk n JOIN world_chunk here ON here.id=? " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)>=2 LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, source);

        // Clean slate for the smoke reading across this world, then lay a fresh plume at the source.
        jdbc.update("DELETE FROM chunk_disturbance WHERE chunk_id IN (SELECT id FROM world_chunk WHERE world_id=?)", world);
        jdbc.update("DELETE FROM chunk_disturbance_event WHERE chunk_id IN (SELECT id FROM world_chunk WHERE world_id=?) AND source_kind IN ('SMOKE','SMOKE_DRIFT')", world);
        Instant now = ticks.current().simulatedAt();
        wildlife.recordEmissionDrift(source, "SMOKE", 20, now);

        // Over the burn: woodsmoke hangs on the ground.
        String here = surveyAt(chronicle, source);
        assertTrue(here.contains("woodsmoke") && here.contains("burning here"),
            () -> "a survey over a fresh burn must read woodsmoke on its ground (#219): " + here);

        // On the neighbour that took the drift: a haze carried in from close by.
        String next = surveyAt(chronicle, neighbour);
        assertTrue(next.contains("woodsmoke") && next.contains("drifts"),
            () -> "a survey on the drifted-onto ground must read a haze drifting in (#219): " + next);

        // Where no smoke has reached: nothing on the air.
        if (far != null) {
            String clean = surveyAt(chronicle, far);
            assertFalse(clean.contains("woodsmoke"),
                () -> "a survey on ground the plume never reached must read no smoke: " + clean);
        }

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
