package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Save / resume durability. Plays part of a Chronicle's life, then forces a full
 * Spring application-context rebuild against the same PostgreSQL database — the
 * software equivalent of stopping and relaunching the game. The second phase
 * asserts the world resumes with identical authoritative state.
 *
 * This is the exact failure mode hit during early manual testing (migrations not
 * reapplied, JPA validation mismatch on restart, or lost runtime state). Because
 * the rebuilt context re-runs Flyway (idempotent) and boots with
 * hibernate ddl-auto=validate, a passing second phase proves migrations are
 * restart-safe, the schema matches the entities, and no authoritative state
 * lives only in memory.
 *
 * The container is shared across the context rebuild (it is a static field, not
 * managed by the Spring context), so the same database survives the "restart".
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SaveResumeDurabilityIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // Authoritative state captured before the simulated restart.
    private static UUID chronicleId;
    private static UUID locationId;
    private static long clockTick;
    private static int carriedCount;

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the save/resume durability test");
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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void playAChronicleThenCaptureAuthoritativeState() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary chronicle = chronicles.awaken();
        actions.resolve("I look around and take stock of the land.");
        actions.resolve("I gather plant fiber from the growth around me.");
        actions.resolve("I gather loose stones from the ground.");
        actions.resolve("I rest for 45 minutes.");

        chronicleId = chronicle.id();
        locationId = chronicles.active().locationId();
        clockTick = ticks.current().tick();
        carriedCount = items.carried().size();

        assertTrue(clockTick > 0, "the clock must have advanced before the restart");
        assertTrue(carriedCount > 0, "the Chronicle should be carrying gathered items and arrival clothing before the restart");
    }

    @Test
    @Order(2)
    void afterContextRebuildTheWorldResumesIdentically() {
        // A fresh Spring context (new beans, re-run Flyway, ddl-auto=validate)
        // is now pointed at the same database. The living Chronicle and world
        // state must be exactly what was persisted before the "restart".
        ChronicleService.ChronicleSummary resumed = chronicles.active();
        assertNotNull(resumed, "a living Chronicle must still exist after a restart");
        assertEquals(chronicleId, resumed.id(), "the same Chronicle identity must resume after a restart");
        assertEquals(locationId, resumed.locationId(), "the Chronicle must resume at the same physical location");
        assertEquals(clockTick, ticks.current().tick(), "the simulation clock must resume at the persisted tick");
        assertEquals(carriedCount, items.carried().size(), "carried items must persist across a restart with no loss or duplication");
    }
}
