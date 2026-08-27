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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reaping in time matters (EPIC #162 agriculture). A stand reaped promptly comes in full; one left standing past the
 * clean window sheds much of its grain to shattering and the birds, so a late harvest saves less — a real reason to
 * bring it in on time, short of losing it outright.
 *
 * <p>Proven: a stand reaped a few days after ripening yields the full harvest, while one reaped weeks late yields
 * markedly less. Skips gracefully without Docker.
 */
@SpringBootTest
class CropReapTimingIntegrationTest {

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

    private int heads(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='wild_grain_head' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private void sow(UUID chunk, Instant sownAt, int maturityDays) {
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested) VALUES (?,?,?,?,?,false)",
            UUID.randomUUID(), chunk, "wild_grain", Timestamp.from(sownAt), maturityDays);
    }

    @Test
    void aPromptHarvestComesInFullWhileALateOneShedsMuchOfTheGrain() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        List<UUID> chunks = jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        Instant now = ticks.current().simulatedAt();

        // A stand ripe five days ago (well within the clean window), and one ripe eighteen days ago (past it,
        // shattering, but not yet lost). Both maturity 30.
        sow(chunks.get(0), now.minus(Duration.ofDays(35)), 30); // ripe at now-5
        sow(chunks.get(1), now.minus(Duration.ofDays(48)), 30); // ripe at now-18

        String[] prompt = items.harvestCrop(chronicle, chunks.get(0), now);
        assertEquals("SUCCEEDED", prompt[0], () -> "the prompt harvest must succeed: " + prompt[1]);
        int afterPrompt = heads(chronicle);
        assertEquals(4, afterPrompt, "a stand reaped promptly comes in full");

        String[] late = items.harvestCrop(chronicle, chunks.get(1), now);
        assertEquals("SUCCEEDED", late[0], () -> "a late but not-yet-lost harvest must still succeed: " + late[1]);
        int fromLate = heads(chronicle) - afterPrompt;
        assertTrue(fromLate < 4 && fromLate >= 2, () -> "a late harvest must save markedly less than a prompt one, got " + fromLate);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
