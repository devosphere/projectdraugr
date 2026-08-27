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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cultivated grain — the seed→soil→crop→harvest spine of physical agriculture (EPIC #162). Grain was wild forage
 * only; now a Chronicle can sow seed grain into open ground, and reap a stand that yields far more than was sown,
 * feeding the grain heads straight into the existing thresh→grind→flour chain.
 *
 * <p>Proven through the full action pipeline: sowing consumes the seed and starts a crop; reaping a green crop is
 * refused; and once the stand has had its season it is reaped for a multiplied yield of grain heads. Skips gracefully
 * without Docker.
 */
@SpringBootTest
class CropCultivationIntegrationTest {

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

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    private int growingStands(UUID chunk) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM crop_stand WHERE chunk_id=? AND harvested=false", Integer.class, chunk);
        return n == null ? 0 : n;
    }

    @Test
    void sowSeedGrainOnOpenGroundThenReapAMultipliedHarvest() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        // Open, workable ground for a crop. Force a tile to grassland so the test does not depend on genesis biomes.
        UUID field = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", field);
        // Keep grazers off this test's field so the yield is the sowing's alone, not depredation's (#166 is its own test).
        jdbc.update("DELETE FROM wildlife_population WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", field);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", field, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant sownTime = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "wild_grain", "Wild grain", sownTime, "TEST_SEED");
        assertEquals(1, owned(chronicle, "wild_grain"), "the Chronicle starts with a handful of seed grain");

        // Sow it — the seed is worked into the ground and a crop begins.
        ChronicleActionService.ActionResult sow = actions.resolve("sow the seed grain in the field");
        assertEquals("SUCCEEDED", sow.outcome(), () -> "sowing on open ground must succeed: " + sow.perception());
        assertEquals(0, owned(chronicle, "wild_grain"), "sowing consumes the seed grain");
        assertEquals(1, growingStands(field), "a crop stand must now be growing on the field");

        // Reaped green — the season has not turned — it is refused.
        ChronicleActionService.ActionResult early = actions.resolve("reap the crop");
        assertEquals("FAILED", early.outcome(), () -> "a green crop must not be reapable: " + early.perception());
        assertEquals(0, owned(chronicle, "wild_grain_head"), "a refused reaping yields nothing");
        assertEquals(1, growingStands(field), "the crop still stands, unharvested");

        // Give it its season, then reap — a multiplied yield of grain heads.
        jdbc.update("UPDATE crop_stand SET sown_at=? WHERE chunk_id=? AND harvested=false",
            Timestamp.from(sownTime.minus(Duration.ofDays(40))), field);
        ChronicleActionService.ActionResult reap = actions.resolve("reap the crop");
        assertEquals("SUCCEEDED", reap.outcome(), () -> "a ripe crop must be reapable: " + reap.perception());
        assertTrue(owned(chronicle, "wild_grain_head") >= 4, () -> "reaping a ripe stand must yield several grain heads — more than the one seed sown, got " + owned(chronicle, "wild_grain_head"));
        assertEquals(0, growingStands(field), "the reaped ground reads as spent until it is sown afresh");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
