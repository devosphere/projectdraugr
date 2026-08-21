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
 * Water-ladle regression (EPIC #54 water-handling / #257 dead-craft audit — closing the last dead-craft). A ladle
 * could be carved from a dry branch (carve_water_ladle, V78) but nothing ever read it — a craftable tool that
 * bought nothing. A ladle draws the clearer water off the top instead of dipping the whole vessel into the silt,
 * so it takes the edge off drinking UNTREATED water: never as safe as boiling or filtering, but a cheap, real
 * improvement on a careless gulp.
 *
 * <p>Proven: the same Chronicle drinking the same raw water accrues less waterborne illness with a ladle in hand
 * than without. Skips gracefully without Docker.
 */
@SpringBootTest
class WaterLadleIntegrationTest {

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

    /** Reset illness to 0, give one carried raw water, drink it through the public action, report illness gained. */
    private int illnessFromDrinkingRawWater(UUID chronicle, Instant at) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0 WHERE chronicle_id=?", chronicle);
        assertTrue(items.makeWater(chronicle, "raw_water", "Raw water", 1, at) > 0, "must carry one raw water to drink");
        ChronicleActionService.ActionResult r = actions.resolve("I drink the water.");
        assertTrue("SUCCEEDED".equals(r.outcome()) || "DRINK".equals(r.intent()), () -> "drinking must resolve: " + r.perception());
        return illness(chronicle);
    }

    @Test
    void aWaterLadleTakesTheEdgeOffDrinkingRawWater() {
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

        // Bare-handed: drinking the raw water you carry sits uneasy in the gut.
        int without = illnessFromDrinkingRawWater(chronicle, now);
        assertTrue(without > 0, "drinking untreated water must carry a real gut-illness risk");

        // The same raw water, but a carved ladle in hand to skim the clearer water off the top.
        items.createCarriedItem(chronicle, "water_ladle", "Water ladle", now, "TEST_SEED");
        int with = illnessFromDrinkingRawWater(chronicle, now);

        assertTrue(with < without,
                () -> "a water ladle must take the edge off raw water: with=" + with + " vs without=" + without + " (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
