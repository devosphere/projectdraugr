package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
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
 * Bone-comb usefulness regression (dead-craft audit #257, EPIC #123). A bone comb was craftable
 * (carve_bone_comb) but read by nothing — spinning drew the same thread with or without it. executeProcess now
 * lets a bone comb to hand lift a spin's workmanship one grade (combed wool aligns to a finer, more even
 * thread), the same minor bounded assist a workstation gives, still capped against the wool's own grade — and
 * distinct from a drop spindle, which only speeds the yield. Proven deterministically: from the same FINE wool
 * tufts, a plain spin yields SOUND yarn, but a bone comb to hand yields FINE yarn.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class BoneCombSpinningIntegrationTest {

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

    /** The grade of the most-recently-spun wool yarn the Chronicle carries. */
    private String newestYarnGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='wool_yarn' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void aBoneCombLiftsTheWorkmanshipOfSpunYarn() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // FINE wool so the workmanship — not the stock — is what caps the yarn. (spin_wool_yarn is bare-hand.)
        for (int t = 0; t < 6; t++) items.createCarriedItem(chronicle, "wool_tuft", "Wool tuft", now, "TEST_SEED", QualityGrade.FINE);

        // A plain spin, no comb: the workmanship is only SOUND, so the FINE wool is capped to SOUND yarn.
        String[] plain = items.executeProcess(chronicle, chunk, "spin_wool_yarn", "spin wool into yarn", now);
        assertEquals("SUCCEEDED", plain[0], () -> "spinning yarn must succeed: " + plain[1]);
        assertEquals("SOUND", newestYarnGrade(chronicle), "a plain spin of FINE wool yields SOUND yarn");

        // The same fleece and the same words, but a bone comb to hand: the combed fibres spin to FINE yarn.
        for (int t = 0; t < 6; t++) items.createCarriedItem(chronicle, "wool_tuft", "Wool tuft", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "bone_comb", "Bone comb", now, "TEST_SEED");
        String[] withComb = items.executeProcess(chronicle, chunk, "spin_wool_yarn", "spin wool into yarn", now);
        assertEquals("SUCCEEDED", withComb[0], () -> "spinning with a comb must succeed: " + withComb[1]);
        assertEquals("FINE", newestYarnGrade(chronicle), "a bone comb lifts the same FINE wool to FINE yarn (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
