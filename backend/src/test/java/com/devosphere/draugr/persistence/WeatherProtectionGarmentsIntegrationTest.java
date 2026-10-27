package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterEach;
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

        // Enough insulation to pass the 80% cap, from the catalogue's own warmest garments rather than numbers
        // written here.
        String warmest = jdbc.queryForObject(
            "SELECT item_key FROM item_definition WHERE insulation_value >= 20 ORDER BY insulation_value DESC LIMIT 1", String.class);
        assertNotNull(warmest, "the catalogue must hold a warm garment for this to assert anything");
        // One to a place on the body: three wears into TORSO/OUTER left ONE garment on, and CI read 72 against the
        // 80 cap. So each goes to its own outer place, as many as it takes to pass the cap.
        int worn = wornWarmth(chronicle);
        for (String place : OUTER_PLACES) {
            if (worn >= 80) break;
            wearOutright(chronicle, warmest, place);
            worn = wornWarmth(chronicle);
        }
        final int wornNow = worn;
        assertTrue(wornNow >= 80, () -> "the fixture must pass the cap for the cap to be assertable, got " + wornNow);

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
     * Put a garment on outright, in A FREE PLACE ON THE BODY.
     *
     * <p>Two CI rounds went on this fixture, and both failures were the same mistake seen from different
     * sides. It first inserted straight into TORSO/OUTER three times over: {@code equipment_attachment} is
     * unique on (chronicle, position, layer), so the second insert failed on the key — and locally nothing
     * showed it, because every @SpringBootTest skips here for want of Docker. The repair displaced whatever
     * was in the slot first, which made all three garments land in the SAME place: only the last one stayed
     * on, the keeper's arrival shirt had been taken off to make room, and the insulation total came to 72
     * where the test needs 80 to assert anything about the cap.
     *
     * <p>So: find a place that is free and hang it there. The suite shares one Chronicle, who arrives dressed,
     * and a fixture that wants to ADD insulation must add rather than replace.
     */
    /** The outer places on the body a fixture may hang a garment, in the order it fills them. */
    private static final java.util.List<String> OUTER_PLACES = java.util.List.of(
        "TORSO", "BACK", "SHOULDER_LEFT", "SHOULDER_RIGHT", "LEGS", "ARM_LEFT", "ARM_RIGHT", "NECK");

    /** The insulation the keeper is wearing, counted the way the warmth model counts it. */
    private int wornWarmth(UUID chronicle) {
        Integer sum = jdbc.queryForObject(
            "SELECT COALESCE(SUM(d.insulation_value + COALESCE(i.lining_bonus,0)),0) FROM equipment_attachment e " +
            "JOIN item_instance i ON i.object_id=e.item_id JOIN item_definition d ON d.item_key=i.item_key " +
            "WHERE e.chronicle_id=?", Integer.class, chronicle);
        return sum == null ? 0 : sum;
    }

    private void wearOutright(UUID chronicle, String itemKey, String place) {
        Integer taken = jdbc.queryForObject(
            "SELECT COUNT(*) FROM equipment_attachment WHERE chronicle_id=? AND body_position=? AND layer='OUTER'",
            Integer.class, chronicle, place);
        if (taken != null && taken > 0) return;   // somebody is already wearing something there

        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_owner_id) " +
                    "VALUES (?,'ITEM',(SELECT display_name FROM item_definition WHERE item_key=?),'ACTIVE',?)", id, itemKey, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
        jdbc.update("INSERT INTO equipment_attachment (item_id,chronicle_id,body_position,layer,attached_at) " +
                    "VALUES (?,?,?,'OUTER',?)", id, chronicle, place, Timestamp.from(Instant.now()));
        woreForTheTest.add(id);
    }

    /** What this test hung on the keeper, so the next test finds them dressed as they were. */
    private final java.util.List<UUID> woreForTheTest = new java.util.ArrayList<>();

    @AfterEach
    void undress() {
        for (UUID id : woreForTheTest)
            jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", id);
        woreForTheTest.clear();
        // Nothing is displaced any more: a garment goes to a FREE place or not at all, so there is nothing
        // to put back. Only what this test hung on the keeper comes off.
    }
}
