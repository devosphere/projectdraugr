package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Land clearing (EPIC #162 / story #165 land-clearing). Cultivation began only on open grassland; a Chronicle standing
 * in forest had no way to make a field. With an axe, wooded ground can be cleared into arable land that tilling and
 * sowing will then take — a Chronicle wins farmland by labour, the oldest way a settled people made a field.
 *
 * <p>Proven through the public action pipeline: sowing on standing forest is refused; clearing without an axe is
 * refused; clearing with an axe opens the ground; and the cleared ground then tills and sows. Skips without Docker.
 */
@SpringBootTest
class LandClearingIntegrationTest {

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

    private boolean cleared(UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cleared_ground WHERE chunk_id=?)", Boolean.class, chunk));
    }

    private int growingStands(UUID chunk) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM crop_stand WHERE chunk_id=? AND harvested=false", Integer.class, chunk);
        return n == null ? 0 : n;
    }

    @Test
    void forestGroundMustBeClearedWithAnAxeBeforeItCanBeTilledAndSown() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID plot = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        // Standing forest — not open, not yet a field.
        jdbc.update("UPDATE world_chunk SET biome='TEMPERATE_FOREST' WHERE id=?", plot);
        jdbc.update("DELETE FROM cleared_ground WHERE chunk_id=?", plot);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", plot, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Seed to hand, but the forest floor will not take it: sowing on standing forest is refused.
        items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED");
        assertEquals("FAILED", actions.resolve("sow the seed grain in the field").outcome(), "grain cannot be sown on standing forest");

        // No axe: clearing is refused — the trees cannot be taken off by hand.
        assertEquals("FAILED", actions.resolve("clear the forest for a field").outcome(), "clearing wooded ground wants an axe");
        assertFalse(cleared(plot), "no ground is cleared without an axe");

        // With an axe, the ground is cleared into arable land.
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", now, "TEST_AXE");
        ChronicleActionService.ActionResult clear = actions.resolve("clear the forest for a field");
        assertEquals("SUCCEEDED", clear.outcome(), () -> "clearing wooded ground with an axe must succeed: " + clear.perception());
        assertTrue(cleared(plot), "the ground now reads as cleared, arable");

        // Cleared ground tills and sows as open ground does.
        assertEquals("SUCCEEDED", actions.resolve("till the seedbed").outcome(), "cleared ground must be tillable");
        ChronicleActionService.ActionResult sow = actions.resolve("sow the seed grain in the field");
        assertEquals("SUCCEEDED", sow.outcome(), () -> "cleared, tilled ground must be sowable: " + sow.perception());
        assertEquals(1, growingStands(plot), "a crop now grows on the ground won from the forest");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
