package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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
 * Sewing-kit usefulness regression (dead-craft audit #257, EPIC #123). A bone needle and a bone awl were
 * craftable but read by nothing — sewing stitched the same with or without them. executeProcess now lets a
 * needle-and-awl kit lift a sew's workmanship one grade, the same minor bounded assist a workstation gives
 * (still capped against the leather's own grade). Proven deterministically: from the same FINE leather, a
 * plain-worded sew yields a SOUND sack, but a needle and awl to hand yield a FINE one.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class SewingKitIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** The grade of the most-recently-made hide sack the Chronicle carries. */
    private String newestSackGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='hide_sack' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void aNeedleAndAwlLiftTheWorkmanshipOfASewnSack() {
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

        // A blade to cut and stitch with (sewing is CUTTING-gated), and FINE leather so the workmanship — not
        // the stock — is what caps the result.
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "tanned_leather", "Tanned leather", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "leather_cord", "Leather cord", now, "TEST_SEED", QualityGrade.FINE);

        // A plain-worded sew, no kit: the workmanship is only SOUND, so the FINE leather is capped to a SOUND sack.
        ChronicleActionService.ActionResult plain = actions.resolve("I sew a hide sack.");
        assertEquals("SUCCEEDED", plain.outcome(), () -> "sewing a hide sack must succeed: " + plain.perception());
        assertEquals("SOUND", newestSackGrade(chronicle), "a plain sew of FINE leather yields a SOUND sack");

        // The same words and the same FINE leather, but a needle and awl to hand: the stitching lifts to FINE.
        items.createCarriedItem(chronicle, "tanned_leather", "Tanned leather", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "leather_cord", "Leather cord", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "bone_needle", "Bone needle", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "bone_awl", "Bone awl", now, "TEST_SEED");
        ChronicleActionService.ActionResult withKit = actions.resolve("I sew a hide sack.");
        assertEquals("SUCCEEDED", withKit.outcome(), () -> "sewing with a needle and awl must succeed: " + withKit.perception());
        assertEquals("FINE", newestSackGrade(chronicle), "a needle and awl lift the same FINE leather to a FINE sack (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
