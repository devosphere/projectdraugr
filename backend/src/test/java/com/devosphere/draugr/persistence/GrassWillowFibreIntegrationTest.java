package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #137 - first-era plant-fibre source chains. Proves long grass fibre and willow bast are each obtained from a
 * reachable plant and twisted into fiber_cordage through the real dispatch (not stolen by STRIP_BARK/GATHER_FIBER).
 * Skips without Docker.
 */
@SpringBootTest
class GrassWillowFibreIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void grassAndWillowFibreTwistIntoCordage() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        // Grass chain: strip long grass -> long grass fibre -> twist into cordage.
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "green_grass_bundle", "Green grass bundle", now, "TEST_FIXTURE");
        var strip = actions.resolve("strip the long grass");
        assertEquals("SUCCEEDED", strip.outcome(), () -> "stripping long grass fibre must succeed: " + strip.perception());
        assertTrue(items.hasAtLeast(chronicle, "long_grass_fibre", 1), "long grass fibre must be in hand");
        var twistGrass = actions.resolve("twist grass into cordage");
        assertEquals("SUCCEEDED", twistGrass.outcome(), () -> "twisting grass cordage must succeed: " + twistGrass.perception());
        assertTrue(items.hasAtLeast(chronicle, "fiber_cordage", 1), "grass fibre must yield cordage");

        // Willow chain: ret willow -> willow bast -> twist into cordage (must not be stolen by STRIP_BARK).
        items.createCarriedItem(chronicle, "willow_branch", "Willow branch", now, "TEST_FIXTURE");
        var ret = actions.resolve("ret the willow bark");
        assertEquals("SUCCEEDED", ret.outcome(), () -> "retting willow bast must succeed (not routed to STRIP_BARK): " + ret.perception());
        assertTrue(items.hasAtLeast(chronicle, "willow_bark_strip", 1), "willow bast strip must be in hand");
        var twistWillow = actions.resolve("twist willow bast into cordage");
        assertEquals("SUCCEEDED", twistWillow.outcome(), () -> "twisting willow cordage must succeed: " + twistWillow.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
