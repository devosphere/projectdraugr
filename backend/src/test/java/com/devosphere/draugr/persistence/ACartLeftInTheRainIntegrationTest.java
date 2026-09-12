package com.devosphere.draugr.persistence;

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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #108/#100 — a cart left in the rain.
 *
 * <p>Metal rusts, hides rot, unfired pottery slakes back to mud — and a wooden cart left standing in the open
 * through a winter was exactly as good as the day it was built, for ever. It is the largest wooden thing a
 * Chronicle owns and the only one the weather could not touch.
 *
 * <p>And a broken one still hauled: {@code item_instance.condition_state} has carried SOUND/WORN/BROKEN all
 * along, and the haul and bulk bonuses asked only whether the object was ACTIVE.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class ACartLeftInTheRainIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** A cart set down on this ground, sound and unsheltered. */
    private UUID cartOnTheGround(UUID chunk) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ITEM','Cart',?)", id, chunk);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'cart','SOUND')", id);
        return id;
    }

    private UUID toolShed(UUID chunk, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE','Tool shed',?)", id, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                    "VALUES (?,'TOOL_SHED','COMPLETED',100,?,100)", id, Timestamp.from(at));
        return id;
    }

    private String condition(UUID item) {
        return jdbc.queryForObject("SELECT condition_state FROM item_instance WHERE object_id=?", String.class, item);
    }

    private Timestamp clock(UUID item) {
        return jdbc.query("SELECT weathered_at FROM item_instance WHERE object_id=?",
            rs -> rs.next() ? rs.getTimestamp(1) : null, item);
    }

    /** Ten days of rain takes a step out of it, and the step after that leaves it broken. */
    @Test
    void aCartLeftOutInTheOpenWeathersDown() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        Instant t0 = Instant.parse("2026-04-01T00:00:00Z");

        try {
            // Nothing roofed stands here — this test raises its own shelter when it wants one.
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                        "  AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.shelters_gear)", chunk);
            UUID cart = cartOnTheGround(chunk);

            // The first turn in the open starts the clock and does not bite.
            items.weatherExposedGear(t0);
            assertEquals("SOUND", condition(cart), "the first spell in the open starts a clock, it does not break anything");
            assertNotNull(clock(cart), "and the clock must actually start");

            // Well short of the span, still sound.
            items.weatherExposedGear(t0.plus(Duration.ofDays(5)));
            assertEquals("SOUND", condition(cart), "five days of weather is not ten");

            // Past it, and the weather takes a step out of it.
            items.weatherExposedGear(t0.plus(Duration.ofDays(11)));
            assertEquals("WORN", condition(cart), "ten days of rain on unprotected timber tells");

            // And the step after that.
            items.weatherExposedGear(t0.plus(Duration.ofDays(30)));
            assertEquals("BROKEN", condition(cart), "left long enough it goes from worn to broken");

            // No further — a cart is a job to do, not a thing lost. REPAIR_ITEM takes it back up again.
            items.weatherExposedGear(t0.plus(Duration.ofDays(90)));
            assertEquals("BROKEN", condition(cart), "the weather does not destroy it outright");
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, cart),
                "and it is still a thing in the world to be mended");
        } finally {
            jdbc.update("DELETE FROM object_transition WHERE object_id IN (SELECT object_id FROM item_instance WHERE item_key='cart')");
            jdbc.update("DELETE FROM item_instance WHERE item_key='cart'");
            jdbc.update("DELETE FROM world_object WHERE display_name='Cart'");
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * What protects it is a roofed store the keeper already builds. There is deliberately no cart shed: nothing
     * in this model distinguishes what will fit inside a building, so one would keep a cart dry exactly as a tool
     * shed does under another name.
     */
    @Test
    void aRoofOverItStopsTheWeatherAndBreaksTheSpell() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        Instant t0 = Instant.parse("2026-04-02T00:00:00Z");

        try {
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                        "  AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.shelters_gear)", chunk);
            UUID cart = cartOnTheGround(chunk);

            // Out in it long enough to be counting.
            items.weatherExposedGear(t0);
            assertNotNull(clock(cart), "the clock is running");

            // Put a roof over it: the spell breaks outright, so ten days under cover and ten days out is not
            // twenty days of weather.
            toolShed(chunk, t0);
            items.weatherExposedGear(t0.plus(Duration.ofDays(2)));
            assertEquals(null, clock(cart), "a roof over it ends the spell rather than pausing it");

            // And it never weathers while the shed stands, however long.
            items.weatherExposedGear(t0.plus(Duration.ofDays(120)));
            assertEquals("SOUND", condition(cart), "what is under cover does not weather at all");
        } finally {
            jdbc.update("DELETE FROM object_transition WHERE object_id IN (SELECT object_id FROM item_instance WHERE item_key='cart')");
            jdbc.update("DELETE FROM item_instance WHERE item_key='cart'");
            jdbc.update("DELETE FROM world_object WHERE display_name='Cart'");
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The declared-but-ignored half: a cart with its axle broken gave a keeper the full load of a sound one,
     * because the haul gate asked only whether the object was ACTIVE.
     */
    @Test
    void aBrokenCartHaulsNothing() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();

        try {
            UUID cart = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Cart',?)", cart, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'cart','SOUND')", cart);

            assertTrue(vehicleCounts(chronicle), "a sound cart is a cart a keeper can load");

            jdbc.update("UPDATE item_instance SET condition_state='BROKEN' WHERE object_id=?", cart);
            assertTrue(!vehicleCounts(chronicle), "a cart with its axle broken must not haul a sound cart's load");

            // Mending it brings the load back — the loop closes on machinery that already existed.
            jdbc.update("UPDATE item_instance SET condition_state='WORN' WHERE object_id=?", cart);
            assertTrue(vehicleCounts(chronicle), "mended back to worn, it pulls again");
        } finally {
            jdbc.update("DELETE FROM item_instance WHERE item_key='cart'");
            jdbc.update("DELETE FROM world_object WHERE display_name='Cart'");
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** The gate the haul and bulk bonuses use, asked directly. */
    private boolean vehicleCounts(UUID chronicle) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM item_instance ti JOIN world_object tw ON tw.id=ti.object_id " +
            "WHERE ti.item_key IN (SELECT item_key FROM draft_vehicle) AND ti.condition_state <> 'BROKEN' " +
            "  AND tw.current_owner_id=? AND tw.lifecycle_state='ACTIVE')", Boolean.class, chronicle));
    }

    /** The catalogue must keep something that shelters gear, and something that does not. */
    @Test
    void theCatalogueKeepsSomewhereToPutACartAndSomewhereNotTo() {
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE shelters_gear", Integer.class) > 0,
            "nothing keeps the weather off gear, so a cart could never be saved");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE NOT shelters_gear", Integer.class) > 0,
            "everything shelters gear, so nothing is ever exposed");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM draft_vehicle dv WHERE dv.item_key NOT IN (SELECT item_key FROM item_definition)", Integer.class),
            "a vehicle that is not an item cannot weather or haul");
    }
}
