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
