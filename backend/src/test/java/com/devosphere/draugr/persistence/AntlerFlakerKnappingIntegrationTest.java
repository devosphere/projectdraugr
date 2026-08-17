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
 * Antler-flaker usefulness regression (dead-craft audit #257, EPIC #123). An antler pressure flaker was
 * craftable (make_antler_flaker) but read by nothing — knapping fine points struck the same with or without
 * it. executeProcess now lets a flaker to hand lift a fine knap's workmanship one grade (arrowheads, a
 * scraper, a burin — the pressure-flaked tools), the same minor bounded assist a workstation gives, still
 * capped against the stone's own grade. Proven deterministically: from the same FINE precision tool stone, a
 * plain-worded knap yields SOUND arrowheads, but a flaker to hand yields FINE ones.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class AntlerFlakerKnappingIntegrationTest {

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

    /** The grade of the most-recently-knapped arrowhead the Chronicle carries. */
    private String newestArrowheadGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='stone_arrowhead' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void anAntlerFlakerLiftsTheWorkmanshipOfAKnappedPoint() {
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
        // Ample capacity so the 4–10 knapped points stay carried (owned), not grounded for want of carrying room.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // A cobble to strike with (knapping is STRIKING-gated), and FINE tool stone so the workmanship — not
        // the stock — is what caps the result.
        items.createCarriedItem(chronicle, "granite_cobble", "Granite cobble", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "precision_tool_stone", "Precision tool stone", now, "TEST_SEED", QualityGrade.FINE);

        // A plain-worded knap, no flaker: the workmanship is only SOUND, so the FINE stone is capped to SOUND points.
        ChronicleActionService.ActionResult plain = actions.resolve("I knap arrowheads.");
        assertEquals("SUCCEEDED", plain.outcome(), () -> "knapping arrowheads must succeed: " + plain.perception());
        assertEquals("SOUND", newestArrowheadGrade(chronicle), "a plain knap of FINE stone yields SOUND arrowheads");

        // The same words and the same FINE stone, but an antler flaker to hand: the pressure flaking lifts to FINE.
        items.createCarriedItem(chronicle, "precision_tool_stone", "Precision tool stone", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "antler_flaker", "Antler pressure flaker", now, "TEST_SEED");
        ChronicleActionService.ActionResult withFlaker = actions.resolve("I knap arrowheads.");
        assertEquals("SUCCEEDED", withFlaker.outcome(), () -> "knapping with a flaker must succeed: " + withFlaker.perception());
        assertEquals("FINE", newestArrowheadGrade(chronicle), "an antler flaker lifts the same FINE stone to FINE arrowheads (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
