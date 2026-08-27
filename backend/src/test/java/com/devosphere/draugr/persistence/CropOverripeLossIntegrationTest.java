package com.devosphere.draugr.persistence;

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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The harvest has a deadline (EPIC #162 agriculture). A ripe crop left un-reaped past its season goes over and is
 * lost — the heads shatter, the birds and weather take it — so bringing the harvest in on time is a real decision,
 * not a stand that waits forever. A stand still within its ripe window is untouched, and a loss is marked LOST,
 * distinct from a REAPED harvest.
 *
 * <p>Proven through the world tick: an over-ripe stand is lost while a stand still in season endures. Skips
 * gracefully without Docker.
 */
@SpringBootTest
class CropOverripeLossIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private boolean harvested(UUID stand) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT harvested FROM crop_stand WHERE id=?", Boolean.class, stand));
    }

    private String outcome(UUID stand) {
        return jdbc.queryForObject("SELECT outcome FROM crop_stand WHERE id=?", String.class, stand);
    }

    private UUID sow(UUID chunk, Instant sownAt, int maturityDays) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested) VALUES (?,?,?,?,?,false)",
            id, chunk, "wild_grain", Timestamp.from(sownAt), maturityDays);
        return id;
    }

    @Test
    void anOverripeStandIsLostWhileOneStillInSeasonEndures() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        List<UUID> chunks = jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        Instant base = ticks.current().simulatedAt();

        // One stand sown sixty days ago (ripe at 30, now well past its 30+21-day window) — over-ripe. Another sown
        // ten days ago — still green, well within season.
        UUID overripe = sow(chunks.get(0), base.minus(Duration.ofDays(60)), 30);
        UUID inSeason = sow(chunks.get(1), base.minus(Duration.ofDays(10)), 30);

        // The world turns one hour — the harvest deadline runs.
        ticks.advanceBy(Duration.ofHours(1));

        assertEquals(true, harvested(overripe), "an over-ripe stand left un-reaped must be gone");
        assertEquals("LOST", outcome(overripe), "the loss must be marked LOST, distinct from a reaped harvest");
        assertEquals(false, harvested(inSeason), "a stand still in season must stand untouched");
        assertNull(outcome(inSeason), "a growing stand has no outcome yet");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
