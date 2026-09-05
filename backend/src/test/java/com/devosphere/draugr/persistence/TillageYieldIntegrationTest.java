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
 * Tillage (EPIC #162 / story #165 land preparation). Seed cast on unbroken sod comes up thinner than seed worked
 * into a tilled seedbed; breaking and turning the ground first — with a digging tool — is rewarded at harvest with a
 * fuller stand. Tillage is optional, so seed still comes up on unbroken ground.
 *
 * <p>Proven through the full pipeline: tilling then sowing marks the stand tilled and yields the fuller harvest,
 * while an untilled sowing of the same crop yields less. Skips gracefully without Docker.
 */
@SpringBootTest
class TillageYieldIntegrationTest {

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

    @Test
    void aTilledSeedbedYieldsAFullerStandThanUnbrokenGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        List<UUID> chunks = jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        UUID tilledField = chunks.get(0), plainField = chunks.get(1);
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id IN (?,?)", tilledField, plainField);
        // Control for pollination (V271): a hive or worm patch working this ground fills the stand out, and this
        // test is about tillage, not bees. Break the pollinators on both fields so what it measures is the
        // documented tilled/untilled baseline and the one variable it names.
        for (UUID field : List.of(tilledField, plainField))
            for (String kind : jdbc.queryForList("SELECT colony_kind FROM insect_colony_kind WHERE pollination_bonus > 0", String.class)) {
                UUID spent = UUID.randomUUID();
                jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE',?,?)",
                    spent, "Spent " + kind.replace('_', ' '), field);
                jdbc.update("INSERT INTO insect_colony (object_id,colony_kind,chunk_id,health,last_disturbed_at) VALUES (?,?,?,0,now())",
                    spent, kind, field);
            }
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", tilledField, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "digging_stick", "Digging stick", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED");

        // Till the ground, then sow it — the stand is marked tilled.
        ChronicleActionService.ActionResult till = actions.resolve("till the ground into a seedbed");
        assertEquals("SUCCEEDED", till.outcome(), () -> "tilling open ground with a tool must succeed: " + till.perception());
        ChronicleActionService.ActionResult sowTilled = actions.resolve("sow the seed grain in the field");
        assertEquals("SUCCEEDED", sowTilled.outcome(), () -> "sowing must succeed: " + sowTilled.perception());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT tilled FROM crop_stand WHERE chunk_id=? AND harvested=false", Boolean.class, tilledField),
            "sowing on freshly tilled ground must mark the stand tilled");

        // An untilled crop of the same kind on the other field.
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested, tilled) VALUES (?,?,?,?,?,false,false)",
            UUID.randomUUID(), plainField, "wild_grain", Timestamp.from(now), 30);

        // Ripen both to five days past maturity (well within the clean window) and reap each.
        Instant reapAt = ticks.current().simulatedAt();
        jdbc.update("UPDATE crop_stand SET sown_at=? WHERE harvested=false", Timestamp.from(reapAt.minus(Duration.ofDays(35))));

        String[] tilledReap = items.harvestCrop(chronicle, tilledField, reapAt);
        assertEquals("SUCCEEDED", tilledReap[0], () -> "reaping the tilled stand must succeed: " + tilledReap[1]);
        int tilledYield = heads(chronicle);

        String[] plainReap = items.harvestCrop(chronicle, plainField, reapAt);
        assertEquals("SUCCEEDED", plainReap[0], () -> "reaping the untilled stand must succeed: " + plainReap[1]);
        int plainYield = heads(chronicle) - tilledYield;

        assertEquals(6, tilledYield, "a tilled seedbed reaped in time yields the fuller stand");
        assertEquals(4, plainYield, "unbroken ground yields the thinner stand");
        assertTrue(tilledYield > plainYield, "tilling must be rewarded with a fuller harvest (#165)");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
