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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-tick playthrough regression. Boots the real Spring context against a
 * PostgreSQL container, seeds the canonical world, awakens a Chronicle, and
 * drives a battery of authoritative actions and simulation ticks.
 *
 * Every action resolves through {@link SimulationTickService#advanceBy}, which
 * advances weather, fire, food spoilage, construction integrity, physiology,
 * and wildlife in one transaction. This is the guard that catches the class of
 * PostgreSQL timestamp-binding defects that previously surfaced one subsystem
 * at a time during live play: any raw java.time.Instant bind, invalid SQL, or
 * broken migration fails here in CI rather than in a player's session.
 *
 * The container is started manually after a Docker-availability assumption so
 * the whole class skips gracefully in environments without a reachable Docker
 * engine (matching the existing persistence integration test), and never fails
 * a local `mvn test`.
 */
@SpringBootTest
class FullTickPlaythroughIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the full-tick integration test");
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

    @Test
    void fullPlaythroughLoopResolvesEveryTickSubsystemWithoutSqlErrors() {
        // Canonical world must exist before a Chronicle can awaken.
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }

        ChronicleService.ChronicleSummary chronicle = chronicles.awaken();
        assertNotNull(chronicle, "awakening must produce a living Chronicle");
        assertNotNull(chronicle.locationId(), "the Chronicle must spawn at a physical chunk");

        // Each of these resolves through the full tick (weather, fire, spoilage,
        // construction integrity, physiology, wildlife) plus its own action path.
        // A raw-Instant bind or broken SQL in any of them throws here.
        String[] intents = {
                "I look carefully around me.",
                // Gather enough physical material to drive the success paths (fire pit
                // needs four stones; lighting needs a dry branch), so the fire_state and
                // construction INSERTs with timestamp binds are actually executed.
                "I gather loose stones from the ground.",
                "I gather loose stones from the ground.",
                "I gather loose stones from the ground.",
                "I gather dry branches from beneath the trees.",
                "I gather dry branches from beneath the trees.",
                "I gather plant fiber from the growth around me.",
                "I search for wild berries.",
                "I rest for 30 minutes.",
                "I build a stone fire pit.",
                "I light a fire.",
                "I add a branch to the fire.",
                "I weave a basket from the plant fiber.",
                "I urinate.",
                "I wash myself in the stream.",
                "I move north.",
                "I move east."
        };
        for (String intent : intents) {
            assertDoesNotThrow(() -> actions.resolve(intent), "action must resolve without a persistence error: " + intent);
        }

        // Exercise the standalone tick path as well (real-time advance variant).
        assertDoesNotThrow(() -> ticks.advance(), "standalone simulation tick must not raise a persistence error");

        // The authoritative body snapshot must still be readable and the clock advanced.
        assertNotNull(actions.resolve("I wait a moment.").body(), "body HUD snapshot must remain readable");
        assertTrue(ticks.current().tick() > 0, "simulation clock must have advanced through the playthrough");

        // The living Chronicle must retain exactly one body and physiology record.
        Integer body = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chronicle c JOIN chronicle_body b ON b.chronicle_id=c.id JOIN chronicle_physiology p ON p.chronicle_id=c.id WHERE c.life_state='LIVING'",
                Integer.class);
        assertTrue(body != null && body == 1, "the living Chronicle must retain exactly one body and physiology record");
    }
}
