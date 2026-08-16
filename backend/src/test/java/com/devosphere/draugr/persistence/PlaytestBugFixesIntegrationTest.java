package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end regression for the playtest bug fixes (#29, #34, #35, #36, #39, #40, #41, #43, #44),
 * driven through the real action boundary against a PostgreSQL container. Each scenario reproduces
 * the reported gap and asserts the corrected physical outcome — a persistent object, a moved
 * containment, or a graceful non-error — so a regression fails here in CI rather than in play.
 *
 * The container starts behind a Docker-availability assumption, so the class skips gracefully where
 * no Docker engine is reachable and never fails a local `mvn test`.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaytestBugFixesIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    private UUID chronicle() { return chronicles.active().id(); }
    private Instant now() { return ticks.current().simulatedAt(); }
    private int ownedCount(String itemKey) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey);
    }

    /**
     * The twelve ordered scenarios share one awakened chronicle, so its carried load accumulates
     * across methods. Left unchecked it exceeds carry capacity, which both makes a freshly crafted
     * container ground itself instead of being carried and makes seeding more items throw. Set the
     * loosely-carried items (except the named essentials, e.g. the reusable blade) down on the ground
     * so a capacity-sensitive scenario starts with honest headroom; grounded items stay ACTIVE and
     * valid, and anything nested inside a kept container is untouched.
     */
    private void shedLooseCarriedExcept(String... keepKeys) {
        UUID chronicle = chronicle();
        UUID chunk = chronicles.active().locationId();
        java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(keepKeys));
        for (java.util.Map<String,Object> row : jdbc.queryForList(
                "SELECT w.id, i.item_key FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND w.id NOT IN (SELECT item_id FROM equipment_attachment)", chronicle)) {
            if (!keep.contains((String) row.get("item_key")))
                jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE id=?", chunk, row.get("id"));
        }
    }

    @Test @Order(0)
    void awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary c = chronicles.awaken();
        assertNotNull(c, "awakening must produce a living Chronicle");
        // A blade is the common gate for these crafts; grant one for the whole run.
        items.createCarriedItem(chronicle(), "stone_knife", "Stone knife", now(), "TEST_SEED");
    }

    /** #36/#43/#44 — weaving a net creates a persistent net object, not water-observation narration. */
    @Test @Order(1)
    void weavingANetCreatesAPersistentNet() {
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle(), "fiber_cordage", "Processed fiber cordage", now(), "TEST_SEED");
        ChronicleActionService.ActionResult r = actions.resolve("I weave a fishing net from the cordage.");
        assertEquals("SUCCEEDED", r.outcome(), () -> "weaving a net must succeed: " + r.perception());
        assertEquals(1, ownedCount("fishing_net"), "a persistent fishing net must exist after weaving one");
    }

    /** #35 — the primitive utility belt has a runnable make-route and produces a real object. */
    @Test @Order(2)
    void makingAUtilityBeltCreatesTheObject() {
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle(), "fiber_cordage", "Processed fiber cordage", now(), "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle(), "plant_fiber", "Plant fiber bundle", now(), "TEST_SEED");
        ChronicleActionService.ActionResult r = actions.resolve("I make a primitive utility belt.");
        assertEquals("SUCCEEDED", r.outcome(), () -> "making a belt must succeed: " + r.perception());
        assertEquals(1, ownedCount("utility_belt"), "a persistent utility belt must exist after making one");
    }

    /** #34/#31 — a basket weaves from vine + cordage (no plant fibre) and produces a real container. */
    @Test @Order(3)
    void basketWeavesFromVineAndCordage() {
        // Clear accumulated load so the woven basket is carried (not grounded for want of capacity),
        // since the store/drop and access-state scenarios below rely on carrying it.
        shedLooseCarriedExcept("stone_knife");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle(), "vine", "Vine", now(), "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle(), "fiber_cordage", "Processed fiber cordage", now(), "TEST_SEED");
        ChronicleActionService.ActionResult r = actions.resolve("I weave a basket.");
        assertEquals("SUCCEEDED", r.outcome(), () -> "weaving a basket from vine+cordage must succeed: " + r.perception());
        assertTrue(ownedCount("woven_basket") >= 1, "a persistent woven basket must exist after weaving one");
    }

    /** #40/#29/#41 — store an item in the basket, drop the basket, then pick it back up with its contents. */
    @Test @Order(4)
    void storeDropAndPickUpKeepTheObjectAndItsContents() {
        UUID chronicle = chronicle();
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now(), "TEST_SEED");
        UUID basket = jdbc.queryForObject(
            "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='woven_basket' AND w.lifecycle_state='ACTIVE' LIMIT 1", UUID.class);
        UUID stone = jdbc.queryForObject(
            "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='field_stone' AND w.lifecycle_state='ACTIVE' AND w.current_owner_id=? LIMIT 1", UUID.class, chronicle);

        // STORE: the stone moves into the basket's containment.
        ChronicleActionService.ActionResult stored = actions.resolve("I put the field stone in the woven basket.");
        assertEquals("SUCCEEDED", stored.outcome(), () -> "storing the stone must succeed: " + stored.perception());
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM item_containment WHERE item_id=? AND container_id=?", Integer.class, stone, basket),
            "the stone must be inside the basket after storing");

        // DROP: the basket grounds at the chunk, keeping its contents; it is not destroyed.
        ChronicleActionService.ActionResult dropped = actions.resolve("I drop the woven basket.");
        assertEquals("SUCCEEDED", dropped.outcome(), () -> "dropping the basket must succeed: " + dropped.perception());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, basket),
            "a dropped basket must persist, not be destroyed");
        assertNotNull(jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, basket),
            "a dropped basket must sit at a ground location");

        // PICK_UP: the basket returns to the carried load, still holding the stone.
        ChronicleActionService.ActionResult picked = actions.resolve("I pick up the woven basket.");
        assertEquals("SUCCEEDED", picked.outcome(), () -> "picking the basket up must succeed: " + picked.perception());
        assertEquals(chronicle, jdbc.queryForObject("SELECT current_owner_id FROM world_object WHERE id=?", UUID.class, basket),
            "the picked-up basket must be carried by the chronicle again");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM item_containment WHERE item_id=? AND container_id=?", Integer.class, stone, basket),
            "picking the basket back up must keep its contents");
    }

    /** M1 #67 — a closed/sealed container gates store and retrieve until it is opened; state is recorded. */
    @Test @Order(6)
    void containerAccessStateGatesStoreAndRetrieve() {
        UUID chronicle = chronicle();
        UUID basket = jdbc.queryForObject(
            "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='woven_basket' AND w.lifecycle_state='ACTIVE' AND w.current_owner_id=? LIMIT 1", UUID.class, chronicle);

        // Close the basket; its access state is recorded.
        ChronicleActionService.ActionResult closed = actions.resolve("I close the woven basket.");
        assertEquals("SUCCEEDED", closed.outcome(), () -> "closing the basket must succeed: " + closed.perception());
        assertEquals("CLOSED", jdbc.queryForObject("SELECT access_state FROM container_properties WHERE object_id=?", String.class, basket));

        // Retrieving from a closed container fails until it is opened.
        ChronicleActionService.ActionResult blockedTake = actions.resolve("I take the field stone out of the woven basket.");
        assertEquals("FAILED", blockedTake.outcome(), "taking from a closed basket must fail");

        // Storing into a closed container also fails.
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now(), "TEST_SEED");
        ChronicleActionService.ActionResult blockedStore = actions.resolve("I put the field stone in the woven basket.");
        assertEquals("FAILED", blockedStore.outcome(), "storing into a closed basket must fail");

        // Open it, and both work again.
        ChronicleActionService.ActionResult opened = actions.resolve("I open the woven basket.");
        assertEquals("SUCCEEDED", opened.outcome(), () -> "opening the basket must succeed: " + opened.perception());
        assertEquals("OPEN", jdbc.queryForObject("SELECT access_state FROM container_properties WHERE object_id=?", String.class, basket));
        ChronicleActionService.ActionResult take = actions.resolve("I take the field stone out of the woven basket.");
        assertEquals("SUCCEEDED", take.outcome(), () -> "taking from the reopened basket must succeed: " + take.perception());
    }

    /** M1 #69 — repairing a worn/broken reachable item mends it one condition step, consuming cordage. */
    @Test @Order(7)
    void repairMendsAWornItemStepByStep() {
        UUID chronicle = chronicle();
        // Seed a broken hammer directly and the cordage to mend it.
        UUID hammer = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Stone hammer',?)", hammer, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'stone_hammer','BROKEN')", hammer);
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Processed fiber cordage", now(), "TEST_SEED");

        ChronicleActionService.ActionResult r1 = actions.resolve("I repair the stone hammer.");
        assertEquals("SUCCEEDED", r1.outcome(), () -> "repairing a broken hammer must succeed: " + r1.perception());
        assertEquals("WORN", jdbc.queryForObject("SELECT condition_state FROM item_instance WHERE object_id=?", String.class, hammer),
            "a broken item must mend one step to WORN");
        ChronicleActionService.ActionResult r2 = actions.resolve("I reinforce the stone hammer.");
        assertEquals("SUCCEEDED", r2.outcome(), () -> "mending again must succeed: " + r2.perception());
        assertEquals("SOUND", jdbc.queryForObject("SELECT condition_state FROM item_instance WHERE object_id=?", String.class, hammer),
            "a worn item must mend the last step to SOUND");
    }

    /** M1 #70 — dismantling a construction recovers a fraction of its material and preserves its history. */
    @Test @Order(8)
    void dismantleRecoversMaterialAndPreservesHistory() {
        UUID chronicle = chronicle();
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now(), "TEST_SEED");
        ChronicleActionService.ActionResult built = actions.resolve("I build a stone fire pit.");
        assertEquals("SUCCEEDED", built.outcome(), () -> "building a fire pit must succeed: " + built.perception());
        UUID pit = jdbc.queryForObject(
            "SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE cp.project_kind='STONE_FIRE_PIT' AND w.lifecycle_state='ACTIVE' ORDER BY w.created_at DESC LIMIT 1", UUID.class);

        ChronicleActionService.ActionResult r = actions.resolve("I dismantle the stone fire pit.");
        assertEquals("SUCCEEDED", r.outcome(), () -> "dismantling must succeed: " + r.perception());
        assertEquals("DESTROYED", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, pit),
            "a dismantled construction is marked DESTROYED, not deleted");
        assertEquals("DISMANTLED", jdbc.queryForObject("SELECT destroyed_cause FROM world_object WHERE id=?", String.class, pit));
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='DISMANTLED'", Integer.class, pit),
            "history preserves the dismantle event");
        assertTrue(ownedCount("field_stone") >= 3, "dismantling a stone fire pit recovers some of its stones");
    }

    /** M1 #56 — a process-made container (hide sack) gets container_properties and can actually hold things. */
    @Test @Order(9)
    void processMadeContainerCanHoldThings() {
        UUID chronicle = chronicle();
        items.createCarriedItem(chronicle, "tanned_leather", "Tanned leather", now(), "TEST_SEED");
        items.createCarriedItem(chronicle, "leather_cord", "Leather cord", now(), "TEST_SEED");
        ChronicleActionService.ActionResult made = actions.resolve("I sew a hide sack.");
        assertEquals("SUCCEEDED", made.outcome(), () -> "sewing a hide sack must succeed: " + made.perception());
        UUID sack = jdbc.queryForObject(
            "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='hide_sack' AND w.lifecycle_state='ACTIVE' AND w.current_owner_id=? LIMIT 1", UUID.class, chronicle);
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM container_properties WHERE object_id=?", Integer.class, sack),
            "a process-made container must have container_properties so it can hold things (#56)");
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now(), "TEST_SEED");
        ChronicleActionService.ActionResult stored = actions.resolve("I put the field stone in the hide sack.");
        assertEquals("SUCCEEDED", stored.outcome(), () -> "storing into the process-made sack must succeed: " + stored.perception());
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_containment ic JOIN item_instance i ON i.object_id=ic.item_id WHERE ic.container_id=? AND i.item_key='field_stone'", Integer.class, sack),
            "the stone is now inside the hide sack");
    }

    /** M1 #57 — a crafted, equipped carrying aid raises carry capacity; the bonus applies only while equipped. */
    @Test @Order(10)
    void carryingAidRaisesCapacityWhileEquipped() {
        UUID chronicle = chronicle();
        shedLooseCarriedExcept("stone_knife"); // start from honest headroom on the shared chronicle
        int baseline = items.sustainedMassCapacity(chronicle);
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now(), "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Processed fiber cordage", now(), "TEST_SEED");
        ChronicleActionService.ActionResult made = actions.resolve("I lash a burden frame.");
        assertEquals("SUCCEEDED", made.outcome(), () -> "lashing a burden frame must succeed: " + made.perception());
        // Not equipped yet -> capacity unchanged.
        assertEquals(baseline, items.sustainedMassCapacity(chronicle), "an unequipped aid does not raise capacity");
        ChronicleActionService.ActionResult worn = actions.resolve("I sling the burden frame on my back.");
        assertEquals("SUCCEEDED", worn.outcome(), () -> "equipping the burden frame must succeed: " + worn.perception());
        assertTrue(items.sustainedMassCapacity(chronicle) > baseline, "an equipped carrying aid raises sustained mass capacity (#57)");
    }

    /** M1 #71 — boiling converts raw water to clean, and DRINK prefers the safest water carried. */
    @Test @Order(11)
    void waterBoilsCleanAndDrinkPrefersIt() {
        UUID chronicle = chronicle();
        shedLooseCarriedExcept("stone_knife"); // start from honest headroom on the shared chronicle
        items.createCarriedItem(chronicle, "raw_water", "Raw water", now(), "TEST_SEED");
        items.createCarriedItem(chronicle, "raw_water", "Raw water", now(), "TEST_SEED");
        int boiled = items.convertWater(chronicle, "raw_water", "clean_water", "Boiled water", 3, now());
        assertEquals(2, boiled, "boiling converts all carried raw water to clean water");
        assertEquals("clean_water", items.bestWaterCarried(chronicle), "boiled water is the safest carried");
        // Add a raw unit back; DRINK must still prefer the clean water first.
        items.createCarriedItem(chronicle, "raw_water", "Raw water", now(), "TEST_SEED");
        int cleanBefore = (int) jdbc.queryForObject("SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE i.item_key='clean_water' AND w.lifecycle_state='ACTIVE' AND w.current_owner_id=?", Integer.class, chronicle);
        ChronicleActionService.ActionResult drank = actions.resolve("I drink from my waterskin.");
        assertEquals("SUCCEEDED", drank.outcome(), () -> "drinking must succeed: " + drank.perception());
        int cleanAfter = (int) jdbc.queryForObject("SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE i.item_key='clean_water' AND w.lifecycle_state='ACTIVE' AND w.current_owner_id=?", Integer.class, chronicle);
        assertEquals(cleanBefore - 1, cleanAfter, "DRINK consumes the boiled water first, not the raw");
    }

    /** #39 — carving a mark and naming the place in one act must not raise a raw persistence error. */
    @Test @Order(5)
    void markAndNameDoesNotRaiseAPersistenceError() {
        ChronicleActionService.ActionResult r = actions.resolve("I carve a blaze into a tree and name this place Camp Site.");
        assertEquals("SUCCEEDED", r.outcome(), () -> "carving and naming must succeed: " + r.perception());
        assertNotEquals("", r.perception());
        assertTrue(r.perception().toLowerCase().contains("camp site"), "the named place must be acknowledged in the narration");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_named_location WHERE chronicle_id=? AND name='Camp Site'", Integer.class, chronicle()),
            "the named location must be persisted exactly once");
    }
}
