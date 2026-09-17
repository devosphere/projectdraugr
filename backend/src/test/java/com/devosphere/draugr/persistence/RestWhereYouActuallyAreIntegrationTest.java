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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rest where you actually are (#30).
 *
 * <p>Resting answered "You remain still while the forest continues around you" wherever the Chronicle happened to
 * be — on open grassland, on a beach, on a mountainside, inside a cave. Narration must witness the world, and a
 * wood that is not there is the plainest way of failing that; it is also the kind of error a player notices at
 * once, because they know where they are standing.
 *
 * <p>And the span was known all along — the player may have asked for two hours — and went unsaid, so a five-minute
 * pause and an afternoon on your back read identically.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class RestWhereYouActuallyAreIntegrationTest {

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

    @Test
    void restingNamesNoWoodThatIsNotThereAndSaysHowLongItLasted() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        String was = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);

        try {
            // Every kind of open country the world places except the one the old line assumed. If any of these
            // reports a forest, the narration is describing somewhere else.
            for (String biome : List.of("GRASSLAND", "COAST", "MOUNTAIN", "WETLAND")) {
                jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", biome, chunk);
                var rested = actions.resolve("rest for 2 hours");
                assertEquals("SUCCEEDED", rested.outcome(), () -> "resting must be possible: " + rested.perception());
                String line = rested.perception();

                assertTrue(!line.contains("the forest continues around you"),
                    () -> "resting on " + biome + " must not report a forest around the Chronicle: " + line);
                assertTrue(line.contains("2 hours"),
                    () -> "the span was asked for and is known, and the line must say it: " + line);
            }

            // A short rest and a long one must not read the same, which is the other half of the defect.
            jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk);
            String brief = actions.resolve("rest for 10 minutes").perception();
            String long_ = actions.resolve("rest for 3 hours").perception();
            assertTrue(!brief.equals(long_),
                () -> "ten minutes and three hours must not be the same sentence: " + brief);
            assertTrue(brief.contains("10 minutes"), () -> "a short rest says so: " + brief);
            assertTrue(long_.contains("3 hours"), () -> "a long one says so: " + long_);
        } finally {
            jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", was, chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
