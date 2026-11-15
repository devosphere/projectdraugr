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
 * THE WATER WAS NEVER PUT IN ANYTHING (#918), AND THE LADLE WAS NEVER A VESSEL (#915).
 *
 * <p>{@code makeWater} created water in the Chronicle's hands and nothing else, while the prose said "you
 * fill your vessel" — so Storage showed 0.0 L in the bark cup AND in the bark fold container, and Load listed
 * Raw water carried loose beside them. Three panels disagreeing about one act. A vessel the world checks for
 * before it will let you collect, and then never puts anything into, is a vessel in name only.
 *
 * <p>And the ladle: catalogued as a TOOL with no container row, so a Chronicle who carved one, equipped it and
 * stood at the marsh was told "nothing you carry will hold it; what you scoop up runs out between your
 * fingers" — while {@code drink water} in the same place described dipping that same ladle and drinking from
 * it. The world knew the ladle held water when it suited one action and not the other.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheWaterGoesInTheVesselIntegrationTest {

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

    /** Make a vessel of this kind and give it the capacity the catalogue says it has. */
    private UUID vessel(UUID chronicle, String key, String name, Instant at) {
        UUID id = items.createCarriedItem(chronicle, key, name, at, "TEST_SEED");
        jdbc.update("INSERT INTO container_properties (object_id, max_mass_grams, max_volume_ml, allows_nested_containers) " +
                    "SELECT ?, c.max_mass_grams, c.max_volume_ml, FALSE FROM container_capacity_default c WHERE c.item_key=? " +
                    "ON CONFLICT (object_id) DO NOTHING", id, key);
        return id;
    }

    /** Measures of water sitting INSIDE a vessel this Chronicle holds. */
    private int inVessels(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_containment ic " +
            "JOIN world_object w ON w.id=ic.item_id JOIN item_instance i ON i.object_id=w.id " +
            "JOIN world_object v ON v.id=ic.container_id " +
            "WHERE v.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND v.lifecycle_state='ACTIVE' " +
            "  AND i.item_key IN ('raw_water','filtered_water','clean_water')", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    /** Vessels belonging to other suites, lifted out of the pack for the length of this test and then returned. */
    private final java.util.List<UUID> setAside = new java.util.ArrayList<>();

    /** The thirteen keys this world counts as something that holds water (PhysicalItemService.WATER_VESSELS). */
    private static final String VESSEL_KEYS =
        "'waterskin','wooden_bucket','clay_pot','clay_jar','fired_bowl','fired_cup','clay_water_filter'," +
        "'wooden_bowl','wooden_trough','soapstone_bowl','folded_bark_cup','water_ladle','field_stone_bowl'";

    /**
     * Clear every measure of water this Chronicle can reach, recording how it went.
     *
     * <p>Two things the raw UPDATE this replaces got wrong. It left {@code destroyed_cause} NULL, which the
     * Auditor refuses in its own right — "destroyed object(s) do not record how they were destroyed" — and it
     * matched only water the Chronicle DIRECTLY owns, which, now that the water really goes into the vessel,
     * is no longer where the water is. The containment rows go first: a containment row pointing at a
     * destroyed object is a violation of its own.
     */
    private void clearWater(UUID chronicle) {
        String mine =
            "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE i.item_key IN ('raw_water','filtered_water','clean_water') AND w.lifecycle_state='ACTIVE' " +
            "  AND (w.current_owner_id=? OR w.current_owner_id IN " +
            "       (SELECT v.id FROM world_object v WHERE v.current_owner_id=?))";
        jdbc.update("DELETE FROM item_containment WHERE item_id IN (" + mine + ")", chronicle, chronicle);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=now(), " +
                    "destroyed_cause='TEST_TEARDOWN', current_owner_id=NULL, current_location_id=NULL " +
                    "WHERE id IN (" + mine + ")", chronicle, chronicle);
    }

    /**
     * Leave this Chronicle holding no water vessel at all, by setting every one it carries down in another
     * chunk — not by destroying it, because a vessel destroyed with water still inside leaves a containment
     * row pointing at a dead object.
     *
     * <p>THE SUITE SHARES ONE CHRONICLE across some 925 tests, and several of them leave a waterskin or a
     * soapstone bowl in its pack for good. This test asks a question about ALL of them at once — "every
     * vessel you have is brimful, so the rain has nowhere to go" — and a stranger's bowl, which can really be
     * filled now that an empty vessel is no longer reported brimful, would take the water this test is
     * following and the refusal would never come. A test that measures a global must own the global.
     */
    private void noVesselsButMine(UUID chronicle, UUID elsewhere) {
        for (UUID id : jdbc.queryForList(
                "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key IN (" + VESSEL_KEYS + ")",
                UUID.class, chronicle)) {
            jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", id);
            jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE id=?", elsewhere, id);
            setAside.add(id);
        }
    }

    /**
     * Hand back every vessel this test set aside, so the suites that run after it find the pack as they left
     * it. Taking a stranger's bowl out of the pack to measure a global is fair; keeping it is how one test
     * starts failing another.
     */
    private void handBackWhatIsNotMine(UUID chronicle) {
        for (UUID id : setAside)
            jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL " +
                        "WHERE id=? AND lifecycle_state='ACTIVE'", chronicle, id);
        setAside.clear();
    }

    /** Measures carried in the bare hands, which is what this whole issue is about not happening. */
    private int loose(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "LEFT JOIN item_containment ic ON ic.item_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND ic.item_id IS NULL " +
            "  AND i.item_key IN ('raw_water','filtered_water','clean_water')", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void collectedWaterGoesIntoAVesselAndAnEquippedLadleIsOne() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        Instant now = ticks.current().simulatedAt();
        UUID wetland = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", wetland, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        UUID elsewhere = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE id <> ? ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class, wetland);
        // Whatever this shared world left in the pack: this test measures its own water, in its own vessels.
        clearWater(chronicle);
        noVesselsButMine(chronicle, elsewhere);

        // A FOLDED BARK CUP AND NOTHING ELSE — the vessel #918 was reported against.
        vessel(chronicle, "folded_bark_cup", "Folded bark cup", now);
        ChronicleActionService.ActionResult filled = actions.resolve("collect water");
        assertEquals("SUCCEEDED", filled.outcome(), filled::perception);
        assertTrue(inVessels(chronicle) >= 1,
            () -> "the water must go IN the vessel the prose says it went in: " + filled.perception());
        assertEquals(0, loose(chronicle),
            () -> "and none of it may be left carried loose in the hands: " + filled.perception());
        assertTrue(filled.perception().contains("bark cup"),
            () -> "the prose must name the vessel it actually filled: " + filled.perception());

        // AND IT IS STILL WATER YOU CAN DRINK. Putting it inside something must not hide it from the acts
        // that look for it — that would trade one defect for a worse one.
        assertTrue(items.hasAtLeast(chronicle, "raw_water", 1), "water in a vessel is still water in reach");
        ChronicleActionService.ActionResult drunk = actions.resolve("drink water");
        assertEquals("SUCCEEDED", drunk.outcome(), drunk::perception);

        // AN EQUIPPED LADLE IS A VESSEL (#915). Nothing else that holds water, and it is in the hand.
        clearWater(chronicle);
        noVesselsButMine(chronicle, elsewhere);
        UUID ladle = vessel(chronicle, "water_ladle", "Water ladle", now);
        items.equip(ladle, "HAND_LEFT", "CARRIED");
        ChronicleActionService.ActionResult scooped = actions.resolve("collect water");
        assertEquals("SUCCEEDED", scooped.outcome(),
            () -> "a ladle in the hand holds water: " + scooped.perception());
        assertTrue(inVessels(chronicle) >= 1, () -> "and the scoop stays in it: " + scooped.perception());
        assertEquals(0, loose(chronicle), () -> "with nothing running through the fingers: " + scooped.perception());

        // FULL IS SAID PLAINLY. "When no eligible vessel has space, explain that no water was collected."
        for (int fill = 0; fill < 4; fill++) actions.resolve("collect water");
        ChronicleActionService.ActionResult brimful = actions.resolve("collect water");
        assertEquals("FAILED", brimful.outcome(), brimful::perception);
        assertTrue(brimful.perception().contains("brimful"), brimful::perception);

        handBackWhatIsNotMine(chronicle);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
