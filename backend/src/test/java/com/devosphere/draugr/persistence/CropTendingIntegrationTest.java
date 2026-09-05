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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crop tending (EPIC #162 / story #165 tending). A sown stand is a living thing that must be worked across its season,
 * not a set-and-forget button: a stand worked clean fills out fuller, carrying an extra head over one left to the
 * weeds — the reward for returning to the field to work it across its season.
 *
 * <p>Proven through both the public action pipeline (a growing stand is weeded) and the harvest yield: a weeded stand
 * reaps more than an unweeded one grown under identical conditions. Skips gracefully without Docker.
 */
@SpringBootTest
class CropTendingIntegrationTest {

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

    private int heads(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='wild_grain_head' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private boolean weeded(UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT weeded_at IS NOT NULL FROM crop_stand WHERE chunk_id=? AND harvested=false", Boolean.class, chunk));
    }

    /** A ripe, untouched stand on clean ground — grazers cleared and soil pristine so weeding is the only variable. */
    private void ripeCrop(UUID chunk, Instant now) {
        // Control for pollination (V271): a hive or worm patch working this ground fills the stand out, and
        // this test is about tillage/weeding/grazing, not bees. Break the pollinators on this chunk so the
        // yield it measures is the documented baseline and the one variable it names.
        for (String kind : jdbc.queryForList("SELECT colony_kind FROM insect_colony_kind WHERE pollination_bonus > 0", String.class)) {
            UUID spent = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE',?,?)", spent, "Spent " + kind.replace('_',' '), chunk);
            jdbc.update("INSERT INTO insect_colony (object_id,colony_kind,chunk_id,health,last_disturbed_at) VALUES (?,?,?,0,now()) ON CONFLICT DO NOTHING", spent, kind, chunk);
        }
        jdbc.update("DELETE FROM wildlife_population WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested) VALUES (?,?,?,?,30,false)",
            UUID.randomUUID(), chunk, "wild_grain", Timestamp.from(now.minus(Duration.ofDays(35))));
    }

    @Test
    void anUnweededStandReapsLessThanAWeededOneAndTheActionWeedsIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        List<UUID> chunks = jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        UUID neglected = chunks.get(0), tended = chunks.get(1);
        Instant now = ticks.current().simulatedAt();

        ripeCrop(neglected, now);
        ripeCrop(tended, now);
        // Weed one stand; leave the other to the weeds. (Set weeded directly on the tended stand to isolate yield.)
        jdbc.update("UPDATE crop_stand SET weeded_at=? WHERE chunk_id=? AND harvested=false", Timestamp.from(now.minus(Duration.ofDays(20))), tended);

        assertEquals("SUCCEEDED", items.harvestCrop(chronicle, neglected, now)[0], "reaping the neglected stand still succeeds");
        int neglectedYield = heads(chronicle);
        assertEquals("SUCCEEDED", items.harvestCrop(chronicle, tended, now)[0], "reaping the weeded stand succeeds");
        int tendedYield = heads(chronicle) - neglectedYield;

        assertTrue(neglectedYield < tendedYield, () -> "a weeded stand must reap more than an unweeded one (neglected=" + neglectedYield + ", tended=" + tendedYield + ") (#165)");
        assertEquals(4, neglectedYield, "an unweeded, pristine, untilled stand reaps the untilled baseline");
        assertEquals(5, tendedYield, "a weeded, pristine, untilled stand reaps a fuller stand — the tending bonus");

        // The public action weeds a growing stand: move the Chronicle to fresh ground, sow, and tend it.
        UUID field = jdbc.queryForObject("SELECT id FROM world_chunk WHERE id NOT IN (?,?) ORDER BY grid_y, grid_x LIMIT 1", UUID.class, neglected, tended);
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", field);
        jdbc.update("DELETE FROM wildlife_population WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", field);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", field, chronicle);
        items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED");
        assertEquals("SUCCEEDED", actions.resolve("sow the seed grain in the field").outcome(), "sowing must succeed");
        assertEquals(false, weeded(field), "the fresh stand starts unweeded");
        ChronicleActionService.ActionResult tend = actions.resolve("weed the crop");
        assertEquals("SUCCEEDED", tend.outcome(), () -> "weeding a growing stand must succeed: " + tend.perception());
        assertEquals(true, weeded(field), "the stand now reads as weeded");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
