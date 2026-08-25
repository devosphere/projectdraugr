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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seams have their own richness (EPIC #180 / #181 finite deposits). A deposit is no longer a flat figure but a seam
 * whose size scales with the mineral's commonness and varies from one patch of ground to the next — so some ground is
 * genuinely richer than other ground for the same ore, and a poor seam is worked out sooner than a rich one.
 *
 * <p>Proven: iron worked at several different chunks records seams within the expected richness band, and not all of
 * the same size — the ground varies. Skips gracefully without Docker.
 */
@SpringBootTest
class MineralRichnessIntegrationTest {

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
    void seamsVaryInRichnessFromOneGroundToTheNext() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED"); // iron needs a striking tool

        // Several different chunks of iron-bearing ground.
        List<UUID> chunks = jdbc.queryForList(
                "SELECT id FROM world_chunk WHERE biome IN ('WETLAND','MOUNTAIN','HIGHLAND') ORDER BY grid_y, grid_x LIMIT 6", UUID.class);
        Assumptions.assumeTrue(chunks.size() >= 2, "need at least two iron-bearing chunks to compare richness");

        List<Integer> seams = new ArrayList<>();
        for (UUID chunk : chunks) {
            boolean got = false;
            for (int i = 0; i < 80 && !got; i++) {
                got = "SUCCEEDED".equals(items.gatherMineral(chronicle, chunk, "mine the iron ore here", now)[0]);
            }
            if (!got) continue; // this ground did not give up ore in the attempts; skip it
            Integer remaining = jdbc.queryForObject(
                    "SELECT remaining_units FROM mineral_deposit WHERE chunk_id=? AND mineral_key='iron_ore'", Integer.class, chunk);
            assertNotNull(remaining, "working iron ore must record a seam");
            // The seam recorded is (its full richness) minus the little taken; iron's richness band is roughly 24..60.
            assertTrue(remaining >= 10 && remaining <= 72,
                    () -> "a recorded iron seam must lie within the richness band (got " + remaining + ")");
            seams.add(remaining);
        }

        assertTrue(seams.size() >= 2, "at least two chunks must have yielded iron to compare");
        Set<Integer> distinct = new HashSet<>(seams);
        assertTrue(distinct.size() >= 2,
                () -> "seams must vary in richness from one ground to the next, not all be the same size: " + seams);

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
