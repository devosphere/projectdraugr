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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clay water-filter quality regression (M1 #59/#78 ceramics / #141 bark filter — closing the fired-filter's
 * token-superiority). Both the fired clay water filter and the improvised bark-and-charcoal cone yield
 * filtered_water, but the costly fired filter (fire + charcoal + tempered clay, ~110 min) bought exactly the same
 * safety as the free 12-minute bark cone — no reason to ever fire one. Now water drunk while a fired clay filter
 * is to hand carries less waterborne risk (1) than bark-only filtered water (2): the terminal edge that makes the
 * fired filter worth the work. Still short of a boil (0).
 *
 * <p>Proven: the same filtered water accrues less gut-illness with a fired clay filter in the pack than without.
 * Skips gracefully without Docker.
 */
@SpringBootTest
class ClayWaterFilterQualityIntegrationTest {

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

    private int illness(UUID chronicle) {
        return jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    /** Reset illness, give one carried filtered water, drink it through the public action, report illness gained. */
    private int illnessFromDrinkingFilteredWater(UUID chronicle, Instant at) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0 WHERE chronicle_id=?", chronicle);
        items.createCarriedItem(chronicle, "filtered_water", "Filtered water", at, "TEST_SEED");
        ChronicleActionService.ActionResult r = actions.resolve("I drink the water.");
        assertTrue("SUCCEEDED".equals(r.outcome()) || "DRINK".equals(r.intent()), () -> "drinking must resolve: " + r.perception());
        return illness(chronicle);
    }

    @Test
    void aFiredClayFilterMakesFilteredWaterCleanerToDrink() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Filtered through the improvised bark cone only (no fired filter to hand): filtered, but not beyond doubt.
        int barkOnly = illnessFromDrinkingFilteredWater(chronicle, now);
        assertTrue(barkOnly > 0, "filtered water short of a boil must still carry some gut-illness risk");

        // The same filtered water, but a fired clay filter in the pack: it runs cleaner and drinks safer.
        items.createCarriedItem(chronicle, "clay_water_filter", "Clay water filter", now, "TEST_SEED");
        int withFiredFilter = illnessFromDrinkingFilteredWater(chronicle, now);

        assertTrue(withFiredFilter < barkOnly,
                () -> "a fired clay filter must make filtered water cleaner to drink than the bark cone: fired=" + withFiredFilter + " vs bark=" + barkOnly + " (#59/#141)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
