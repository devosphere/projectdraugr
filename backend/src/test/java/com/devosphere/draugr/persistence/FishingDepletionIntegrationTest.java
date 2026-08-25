package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finite fish stocks (EPIC #181 finite depletion / #36 fishing). Fishing read the aquatic species a biome holds and
 * yielded fish forever — the last bottomless natural resource, after ore (V165) and woodland (#200). Now a stretch of
 * water holds a finite, generous stock, drawn down by each catch and restocked over time, so a spot fished
 * relentlessly thins and, given rest, recovers.
 *
 * <p>Proven: a stretch recorded as fished out yields nothing (a distinct outcome, not a failed cast); and fresh water
 * still yields fish and is recorded drawn down from full. Skips gracefully without Docker.
 */
@SpringBootTest
class FishingDepletionIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aStretchOfWaterIsFiniteAndFishesOutButFreshWaterStillYields() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have wetland water to fish");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "fish_trap", "Fish trap", now, "TEST_SEED"); // a productive method

        // A stretch recorded as fished out gives nothing — and says so, distinctly from a failed cast.
        jdbc.update("INSERT INTO fish_stock (chunk_id, remaining_units, last_fished_at) VALUES (?, 0, ?)", chunk, Timestamp.from(now));
        WildlifeEncounterService.EncounterResult out = wildlife.fish(chronicle, chunk, UUID.randomUUID(), now, "fish the water here");
        assertEquals("FAILED", out.outcome(), () -> "fished-out water must not yield: " + out.narration());
        assertTrue(out.narration().toLowerCase(Locale.ROOT).contains("fished out"),
                () -> "a spent stretch must read as fished out, not a mere failed cast: " + out.narration());

        // Fresh water: clear the record and work it until a fish comes (fishing has a success chance).
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", chunk);
        boolean caught = false;
        java.util.Random rnd = new java.util.Random(11);
        for (int i = 0; i < 60 && !caught; i++) {
            caught = "SUCCEEDED".equals(wildlife.fish(chronicle, chunk, new UUID(rnd.nextLong(), rnd.nextLong()), now, "fish the water here").outcome());
        }
        assertTrue(caught, "fresh water must still yield fish");

        // Working fresh water must record a stock, drawn down from full but not yet spent.
        Integer remaining = jdbc.queryForObject("SELECT remaining_units FROM fish_stock WHERE chunk_id=?", Integer.class, chunk);
        assertNotNull(remaining, "working fresh water must record a fish stock");
        assertTrue(remaining > 0 && remaining < 500,
                () -> "the recorded stretch must be drawn down from full and still hold more (got " + remaining + ") (#181/#36)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
