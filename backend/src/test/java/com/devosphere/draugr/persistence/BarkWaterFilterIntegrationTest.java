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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First-hours water filtration regression (M1 #141, EPIC #123). Before this, every way to improve water
 * leant on fired clay, so a newly arrived Chronicle at standing water could do nothing to it without the
 * whole pottery chain. This proves the fire-free path end to end: a Chronicle with a bark sheet, a lump of
 * charcoal, and a grass bundle makes a bark-and-charcoal filter through the real action pipeline and then
 * pours raw water through it to get filtered water.
 *
 * <p>It also guards the intent boundary: "make a bark and charcoal filter" must build the filter (a craft),
 * while "filter the water" must run the filtering — the two must not collide. Skips gracefully without Docker.
 */
@SpringBootTest
class BarkWaterFilterIntegrationTest {

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

    private int carried(UUID chronicle, String itemKey) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey, chronicle);
    }

    @Test
    void aBarkAndCharcoalFilterIsMadeBareHandedAndClarifiesRawWater() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant now = ticks.current().simulatedAt();

        // The physical inputs the forest floor provides — a stripped bark sheet, a lump of charcoal from a
        // spent fire, a bundle of dry grass. No tool, no kiln.
        items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_grass_bundle", "Dry grass bundle", now, "TEST_SEED");

        // MAKE the filter — this must be the craft, not the filtering action, even though it names "filter".
        ChronicleActionService.ActionResult made = actions.resolve("I make a bark and charcoal filter.");
        assertEquals("SUCCEEDED", made.outcome(), () -> "making a bark-and-charcoal filter must succeed: " + made.perception());
        assertEquals(1, carried(chronicle, "bark_water_filter"), "a persistent bark water filter must exist after making one");
        assertEquals(0, carried(chronicle, "bark_sheet"), "the bark sheet is consumed into the filter");
        assertEquals(0, carried(chronicle, "charcoal"), "the charcoal is consumed into the filter");

        // FILTER raw water THROUGH it — this must be the filtering action, and it must accept the bark filter.
        items.createCarriedItem(chronicle, "raw_water", "Raw water", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "raw_water", "Raw water", now, "TEST_SEED");
        ChronicleActionService.ActionResult filtered = actions.resolve("I filter the water through it.");
        assertEquals("SUCCEEDED", filtered.outcome(), () -> "filtering raw water through the bark filter must succeed: " + filtered.perception());
        assertTrue(carried(chronicle, "filtered_water") >= 1, "pouring raw water through the bark filter yields filtered water (#141)");
        assertEquals(1, carried(chronicle, "bark_water_filter"), "the filter itself is not consumed by a pour-through");

        PersistentStateAuditor.AuditReport report = auditor.inspect();
        assertTrue(report.consistent(), () -> "the world must stay Auditor-consistent: " + report.violations());
    }
}
