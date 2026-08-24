package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finite mineral deposits (EPIC #180 / #181 finite site depletion). Mineral gathering rolled against rarity with no
 * memory of the ground — a seam could be worked forever. Now a deposit holds a finite, generous stock per chunk,
 * recorded lazily at full on first working and drawn down as it is quarried; spent, it is worked out there.
 *
 * <p>Proven: a seam recorded as spent yields nothing (a distinct "worked out" outcome, not a failed search); and a
 * fresh seam still yields ore and is recorded drawn down from full. Skips gracefully without Docker.
 */
@SpringBootTest
class MineralDepletionIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aSeamIsFiniteAndWorksOutButFreshGroundStillYields() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        // Iron ore is gatherable in wetland ground; a wetland chunk is reliably present.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have wetland ground to work");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED"); // iron ore needs a striking tool

        // A seam recorded as spent gives nothing more — and says so, distinctly from a failed search.
        jdbc.update("INSERT INTO mineral_deposit (chunk_id, mineral_key, remaining_units) VALUES (?, 'iron_ore', 0)", chunk);
        String[] workedOut = items.gatherMineral(chronicle, chunk, "mine the iron ore here", now);
        assertEquals("FAILED", workedOut[0], () -> "a worked-out seam must not yield: " + workedOut[1]);
        assertTrue(workedOut[1].toLowerCase().contains("worked out"),
                () -> "a spent seam must read as worked out, not merely a failed search: " + workedOut[1]);

        // Fresh ground: clear the record and work it until a nodule comes free (mining has a success chance).
        jdbc.update("DELETE FROM mineral_deposit WHERE chunk_id=? AND mineral_key='iron_ore'", chunk);
        boolean got = false;
        for (int i = 0; i < 80 && !got; i++) {
            got = "SUCCEEDED".equals(items.gatherMineral(chronicle, chunk, "mine the iron ore here", now)[0]);
        }
        assertTrue(got, "fresh ground must still yield ore");

        // Working fresh ground must record a deposit, drawn down from full but not yet spent.
        Integer remaining = jdbc.queryForObject(
                "SELECT remaining_units FROM mineral_deposit WHERE chunk_id=? AND mineral_key='iron_ore'", Integer.class, chunk);
        assertNotNull(remaining, "working fresh ground must record a deposit");
        assertTrue(remaining < 60 && remaining > 0,
                () -> "the recorded seam must be drawn down from full and still hold more (got " + remaining + ") (#181)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
