package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a sown crop (EPIC #162 agriculture). A crop is only playable if a Chronicle can tell a green stand from
 * one ripe to reap; the survey now reads the crop's stage from its elapsed growing-time, so the harvest can be
 * judged before the sickle is set to it.
 *
 * <p>Proven through the survey: a freshly sown crop reads green, a stand well into its season reads ripening, and a
 * matured one reads ripe and ready to reap. Skips gracefully without Docker.
 */
@SpringBootTest
class CropSurveyIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String survey() {
        ChronicleActionService.ActionResult r = actions.resolve("look around carefully");
        return r.perception() == null ? "" : r.perception().toLowerCase(Locale.ROOT);
    }

    @Test
    void aSurveyReadsWhetherTheSownCropIsGreenRipeningOrRipe() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID field = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", field);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", field, chronicle);
        Instant base = ticks.current().simulatedAt();

        UUID stand = UUID.randomUUID();
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested) VALUES (?,?,?,?,?,false)",
            stand, field, "wild_grain", Timestamp.from(base), 30);

        // Freshly sown: green.
        String green = survey();
        assertTrue(green.contains("sown grain") && green.contains("still green"),
            () -> "a freshly sown crop must read as green: " + green);

        // Well into its season (20 of 30 days): ripening.
        jdbc.update("UPDATE crop_stand SET sown_at=? WHERE id=?", Timestamp.from(base.minus(Duration.ofDays(20))), stand);
        String ripening = survey();
        assertTrue(ripening.contains("ripening"), () -> "a crop well into its season must read as ripening: " + ripening);

        // Past maturity: ripe and ready to reap.
        jdbc.update("UPDATE crop_stand SET sown_at=? WHERE id=?", Timestamp.from(base.minus(Duration.ofDays(40))), stand);
        String ripe = survey();
        assertTrue(ripe.contains("ripe") && ripe.contains("reap"), () -> "a matured crop must read as ripe and ready to reap: " + ripe);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
