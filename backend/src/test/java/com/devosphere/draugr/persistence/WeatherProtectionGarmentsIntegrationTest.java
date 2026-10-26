package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stories #146/#147 - improvised first-era clothing and rain/cold protection. Proves "make a grass cape" routes past
 * the Java CRAFT_GARMENT guard to the data-driven make process and yields a reachable, equippable grass rain cape,
 * and that all seven new weather garments carry insulation/water_resistance the body reads when worn. Skips without
 * Docker.
 */
@SpringBootTest
class WeatherProtectionGarmentsIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aGrassCapeIsWovenFromReachableMaterials() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "dry_grass_bundle", "Dry grass bundle", now, "TEST_FIXTURE");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");

        var made = actions.resolve("make a grass cape");
        assertEquals("SUCCEEDED", made.outcome(), () -> "weaving a grass cape must succeed (not be stolen by CRAFT_GARMENT): " + made.perception());
        assertTrue(items.hasAtLeast(chronicle, "grass_rain_cape", 1), "a grass rain cape must now be in reach");

        // Every new weather garment is an equippable CLOTHING piece carrying real climate stats the body reads.
        Integer withStats = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition WHERE equippable AND category='CLOTHING' AND (insulation_value>0 OR water_resistance>0) " +
            "AND item_key IN ('grass_rain_cape','bark_rain_cape','bark_sandals','grass_lined_bark_sandals','simple_hide_wrap','woven_headband','hide_hood')", Integer.class);
        assertEquals(7, (int) withStats, "all seven weather garments must be equippable clothing with climate stats");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The clothing reading says what the model charges (#37, V440).
     *
     * <p>The warmth rule caps insulation at 80% of the cold kept out and water resistance at 60% of the rain
     * shed, and makes wet clothing insulate 1.6x worse. Those three facts decide whether a Chronicle lives
     * through a night, and a player could not learn any of them: more layers simply stopped helping, nothing
     * ever stayed dry, and the cloak they were proud of was worth a fraction of itself while soaked.
     *
     * <p>So the reading says them out loud, and this holds it to the same numbers rather than to its prose: a
     * Chronicle wearing enough to pass the cap must be told the cap has been reached, and the same Chronicle
     * soaked must be told that it counts for little until they dry.
     */
    @Test
    void theClothingReadingSaysWhatTheModelCharges() {
        UUID chronicle = aDressedChronicle();
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        assertNotNull(here);
        Integer wetnessBefore = jdbc.queryForObject(
            "SELECT wetness_level FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        List<UUID> putOn = new ArrayList<>();
        try {
            readTheClothing(chronicle, putOn);
        } finally {
            // The suite shares this Chronicle: take off what was put on, and give back how wet they were.
            for (UUID id : putOn) {
                jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", id);
                jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_cause='TEST_TEARDOWN', " +
                            "current_location_id=NULL, current_owner_id=NULL WHERE id=?", id);
            }
            jdbc.update("UPDATE chronicle_physiology SET wetness_level=? WHERE chronicle_id=?", wetnessBefore, chronicle);
        }
    }

    private void readTheClothing(UUID chronicle, List<UUID> putOn) {
        // Enough insulation to pass the 80% cap, from the catalogue's own warmest garments rather than numbers
        // written here.
        String warmest = jdbc.queryForObject(
            "SELECT item_key FROM item_definition WHERE insulation_value >= 20 ORDER BY insulation_value DESC LIMIT 1", String.class);
        assertNotNull(warmest, "the catalogue must hold a warm garment for this to assert anything");
        for (int i = 0; i < 3; i++) putOn.add(wearOutright(chronicle, warmest));
        int worn = jdbc.queryForObject(
            "SELECT COALESCE(SUM(d.insulation_value + COALESCE(i.lining_bonus,0)),0) FROM equipment_attachment e " +
            "JOIN item_instance i ON i.object_id=e.item_id JOIN item_definition d ON d.item_key=i.item_key " +
            "WHERE e.chronicle_id=?", Integer.class, chronicle);
        assertTrue(worn >= 80, () -> "the fixture must pass the cap for the cap to be assertable, got " + worn);

        jdbc.update("UPDATE chronicle_physiology SET wetness_level=0 WHERE chronicle_id=?", chronicle);
        ChronicleActionService.ActionResult dry = actions.resolve("am I dressed for this weather");
        assertEquals("JUDGE_CLOTHING", dry.intent(), () -> "the question must reach the reading: " + dry.perception());
        assertTrue(dry.perception().contains("as much of the cold as any clothing can"),
            () -> "past the cap, the reading must say the cap has been reached: " + dry.perception());
        assertFalse(dry.perception().contains("worth a fraction"),
            () -> "and must not claim a dry Chronicle is soaked: " + dry.perception());

        // SOAKED, which is the fact that kills and the one nothing said.
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=75 WHERE chronicle_id=?", chronicle);
        ChronicleActionService.ActionResult wet = actions.resolve("am I dressed for this weather");
        assertEquals("JUDGE_CLOTHING", wet.intent());
        assertTrue(wet.perception().contains("worth a fraction"),
            () -> "wet through, the reading must say what that costs: " + wet.perception());
        assertFalse(wet.perception().contains("dressed for it so long as you keep dry"),
            () -> "and must not also call them dressed for it — two halves of one answer disagreeing: " + wet.perception());

        assertTrue(auditor.inspect().consistent(), () -> "a reading alters nothing: " + auditor.inspect().violations());
    }

    /** A Chronicle wearing their arrival clothes, awakened if the suite spent the last one. */
    private UUID aDressedChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var active = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(active, "a living Chronicle is needed");
        return active.id();
    }

    /**
     * Put a garment on outright, in an outer slot this Chronicle has free. The table allows one garment per
     * position and layer, and the shared Chronicle may already wear something on any of them.
     */
    private UUID wearOutright(UUID chronicle, String itemKey) {
        List<String> free = jdbc.queryForList(
            "SELECT t.p FROM unnest(ARRAY['TORSO','BACK','SHOULDER_LEFT','SHOULDER_RIGHT','WAIST','NECK','ARM_LEFT','ARM_RIGHT']) " +
            "WITH ORDINALITY AS t(p, n) WHERE NOT EXISTS (SELECT 1 FROM equipment_attachment e " +
            "WHERE e.chronicle_id=? AND e.body_position=t.p AND e.layer='OUTER') ORDER BY t.n", String.class, chronicle);
        assertFalse(free.isEmpty(), "the Chronicle must have an outer slot free for the fixture");
        String position = free.get(0);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_owner_id) " +
                    "VALUES (?,'ITEM',(SELECT display_name FROM item_definition WHERE item_key=?),'ACTIVE',?)", id, itemKey, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
        jdbc.update("INSERT INTO equipment_attachment (item_id,chronicle_id,body_position,layer,attached_at) " +
                    "VALUES (?,?,?,'OUTER',?)", id, chronicle, position, Timestamp.from(Instant.now()));
        return id;
    }
}
