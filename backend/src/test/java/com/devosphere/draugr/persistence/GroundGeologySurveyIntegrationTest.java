package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading likely ground before working it (EPIC #180 / #181 geological survey). Depletion (V165) meant a Chronicle
 * could learn a seam's extent only by quarrying it. Now a deliberate survey reads what the ground holds — the
 * minerals whose affinity matches this chunk — and, for any seam already opened, whether it still holds, runs thin,
 * or is worked out.
 *
 * <p>Proven through the real action pipeline (OBSERVE): an untouched wetland reads as likely to hold iron; once its
 * seam is recorded worked out, the survey says so; once thinning, it says that. Skips gracefully without Docker.
 */
@SpringBootTest
class GroundGeologySurveyIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired JdbcTemplate jdbc;

    private String surveyText() {
        ChronicleActionService.ActionResult r = actions.resolve("look around carefully");
        return r.perception() == null ? "" : r.perception().toLowerCase(Locale.ROOT);
    }

    @Test
    void aSurveyReadsWhatTheGroundHoldsAndHowWorkedTheSeamIs() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have wetland ground (iron-bearing) to read");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);

        // Untouched ground reads as likely to hold iron (bog iron in the wetlands).
        String untouched = surveyText();
        assertTrue(untouched.contains("iron ore"),
                () -> "an untouched wetland must read as likely to hold iron: " + untouched);

        // A seam recorded worked out reads as worked out.
        jdbc.update("INSERT INTO mineral_deposit (chunk_id, mineral_key, remaining_units) VALUES (?, 'iron_ore', 0) " +
                "ON CONFLICT (chunk_id, mineral_key) DO UPDATE SET remaining_units=0", chunk);
        String spent = surveyText();
        assertTrue(spent.contains("worked out"),
                () -> "a spent seam must read as worked out in the survey: " + spent);

        // A seam drawn down but not spent reads as running thin.
        jdbc.update("UPDATE mineral_deposit SET remaining_units=5 WHERE chunk_id=? AND mineral_key='iron_ore'", chunk);
        String thin = surveyText();
        assertTrue(thin.contains("running thin"),
                () -> "a dwindling seam must read as running thin in the survey: " + thin);
    }
}
