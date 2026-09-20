package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.people.NativeCommunityService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Homes that burn and are rebuilt (#111, #114), through the public action boundary and the world clock.
 *
 * <p>Fire needs fire: wishing the thatch alight does nothing. A torch put to the village by night, unwatched, is not
 * seen, though the damage is found and the watch doubled. Fire put to the store in daylight is seen by everyone, takes
 * half of what is kept inside, and a second burning brings the store down, leaving what it held on the ground. Then
 * the clock: the village is mended day by day, the store is raised again after a few days, and a people who have gone
 * hungry long enough to move on arrive on new marsh, taking the living and what they built, and leaving their dead.
 * Skips without Docker.
 */
@SpringBootTest
class HomesThatBurnAndAreRebuiltIntegrationTest {

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
    @Autowired NativeCommunityService natives;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockBefore;

    @BeforeEach
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    private void at(String iso) {
        Timestamp t = Timestamp.from(Instant.parse(iso));
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", t);
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", t);
    }

    private List<String> history(UUID community) {
        return jdbc.queryForList("SELECT event_kind FROM native_event WHERE community_id=? ORDER BY id", String.class, community);
    }

    private int condition(UUID community, String kind) {
        return jdbc.queryForObject("SELECT s.condition_percent FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.site_kind=? AND w.lifecycle_state='ACTIVE'", Integer.class, community, kind);
    }

    private int held(UUID store) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", Integer.class, store);
    }

    @Test
    void whatIsBurntIsMendedOrRaisedAgainAndAPeopleOnTheMoveArrives() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        at("2031-06-10T23:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture='WARY' WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        jdbc.update("UPDATE native_settlement_site SET condition_percent=100, access_rule='INVITED' WHERE community_id=?", community);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,0,30,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

        // Fire needs fire.
        var wish = actions.resolve("burn their village");
        assertEquals("CONDUCT_TOWARD_PEOPLE", wish.intent(), wish::perception);
        assertEquals("FAILED", wish.outcome(), wish::perception);
        assertEquals(100, condition(community, "VILLAGE"));

        // By night, unwatched: the village is scorched, the burning is not held against anyone, but the watch is doubled.
        items.createCarriedItem(chronicle, "firebrand", "Firebrand", Instant.now(), "TEST_FIXTURE");
        var night = actions.resolve("burn their village");
        assertEquals("SUCCEEDED", night.outcome(), night::perception);
        assertEquals(40, condition(community, "VILLAGE"));
        assertEquals(0, (int) jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle));
        assertTrue(history(community).containsAll(List.of("FIRE_DAMAGE", "SETTLEMENT_DAMAGED")));
        assertEquals("GUARDED", jdbc.queryForObject("SELECT security_posture FROM native_community WHERE id=?", String.class, community));

        // In daylight, seen: half the store's goods burn, the isle turns hostile, and a second burning brings it down.
        at("2031-06-11T12:00:00Z");
        UUID store = jdbc.queryForObject("SELECT object_id FROM native_settlement_site WHERE community_id=? AND holds_stores", UUID.class, community);
        assertTrue(held(store) >= 2, "the store holds something to lose");
        actions.resolve("set fire to their store");
        // Counted from what the fire destroyed rather than from a before-and-after count, because the day the action
        // costs also runs the isle's own day: they eat from this store and put the day's catch in it.
        int burned = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE destroyed_cause='BURNED'", Integer.class);
        assertTrue(burned >= 1, "fire takes what is kept inside");
        assertTrue(held(store) >= burned, "and never more than half: what is left is at least what burned");
        assertEquals(40, condition(community, "STORE_HOUSE"));
        assertEquals("HOSTILE", jdbc.queryForObject("SELECT security_posture FROM native_community WHERE id=?", String.class, community));
        assertTrue(jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle) <= -80);

        int survived = held(store) - held(store) / 2;
        actions.resolve("set fire to their store");
        survived = Math.max(survived, 0);
        assertEquals("DESTROYED", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, store));
        assertEquals("BURNED", jdbc.queryForObject("SELECT destroyed_cause FROM world_object WHERE id=?", String.class, store));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_location_id=? AND w.current_owner_id IS NULL AND w.lifecycle_state='ACTIVE' AND i.item_key IN ('dried_fish','reed_mat','fiber_cordage','woven_basket','fish_trap')",
            Integer.class, isle) >= survived, "what the fire spared lies on the ground where the store stood");

        // The clock: mending by hand, and after a few days the store stands again.
        jdbc.update("UPDATE world_object SET current_location_id=(SELECT id FROM world_chunk WHERE id <> ? AND world_id=(SELECT world_id FROM world_chunk WHERE id=?) LIMIT 1) WHERE id=?",
            isle, isle, chronicle);
        natives.advanceTo(Instant.parse("2031-06-12T00:00:00Z"));
        assertTrue(condition(community, "VILLAGE") > 40, "the village is mended a little each day");
        assertTrue(!history(community).contains("REBUILT"), "a store is not raised in a day");
        natives.advanceTo(Instant.parse("2031-06-16T00:00:00Z"));
        assertTrue(history(community).contains("REBUILT"), () -> "the store is raised again: " + history(community));
        UUID raised = jdbc.queryForObject("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", UUID.class, community);
        assertNotEquals(store, raised);
        assertEquals(isle, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, raised));

        // On the move: hungry for long enough to leave, and more mouths than the water feeds.
        jdbc.update("UPDATE native_community SET lifecycle='MOVING', shortage_days=15, daily_ration=6 WHERE id=?", community);
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind) VALUES (?,?,'LEFT_TO_FIND_FOOD')",
            community, Timestamp.from(Instant.parse("2031-06-12T00:00:00Z")));
        UUID dead = jdbc.queryForObject("SELECT object_id FROM native_individual WHERE community_id=? ORDER BY given_name LIMIT 1", UUID.class, community);
        jdbc.update("UPDATE native_individual SET condition='DEAD' WHERE object_id=?", dead);
        boolean somewhereToGo = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_chunk k JOIN world_chunk h ON h.id=? WHERE k.world_id=h.world_id AND k.biome=h.biome AND k.id <> h.id " +
            "AND greatest(abs(k.grid_x-h.grid_x), abs(k.grid_y-h.grid_y)) <= 8 " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site s WHERE s.chunk_id=k.id AND s.site_category='MONSTER') " +
            "AND NOT EXISTS (SELECT 1 FROM native_community o JOIN world_chunk oh ON oh.id=o.home_chunk_id WHERE o.id <> ? AND o.lifecycle <> 'DISPERSED' " +
            "                AND greatest(abs(oh.grid_x-k.grid_x), abs(oh.grid_y-k.grid_y)) < 4))", Boolean.class, isle, community));
        natives.advanceTo(Instant.parse("2031-06-17T00:00:00Z"));
        UUID home = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        if (somewhereToGo) {
            assertNotEquals(isle, home, "they arrive on new marsh");
            assertEquals("SETTLED", jdbc.queryForObject("SELECT lifecycle FROM native_community WHERE id=?", String.class, community));
            assertTrue(history(community).contains("RELOCATED"));
            assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                "WHERE n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE' AND w.current_location_id <> ?", Integer.class, community, home),
                "every living person goes with them");
            assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("SELECT current_location_id IS DISTINCT FROM ? FROM world_object WHERE id=?", Boolean.class, home, dead)), "the dead stay");
            assertEquals(home, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, raised), "what they built goes with them");
        } else {
            assertEquals(isle, home, "with nowhere in reach, they stay where they are");
            assertEquals("MOVING", jdbc.queryForObject("SELECT lifecycle FROM native_community WHERE id=?", String.class, community));
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
