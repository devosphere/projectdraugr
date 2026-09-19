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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paid work for a people (#113), through the public action boundary and the world clock.
 *
 * <p>An offer of work for goods is weighed and set at a number of days; each day worked is a day at their nets, with
 * the catch going to their store; the last day is paid out of the store into the Chronicle's hands; work left undone
 * past its date is a broken promise; an honest release costs a little; and a wage the store cannot pay is owed, and
 * paid when it can be. Skips without Docker.
 */
@SpringBootTest
class PaidWorkForAPeopleIntegrationTest {

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

    private int standing(UUID community, UUID chronicle) {
        return jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle);
    }

    private String status(UUID community, UUID chronicle) {
        return jdbc.queryForObject("SELECT status FROM native_agreement WHERE community_id=? AND chronicle_id=? ORDER BY agreed_at DESC LIMIT 1", String.class, community, chronicle);
    }

    private int carried(UUID chronicle, String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, chronicle, key);
    }

    private int stored(UUID store, String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, store, key);
    }

    @Test
    void workIsAgreedDonePaidOwedAndBroken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        at("2031-06-10T08:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,10,40,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        UUID store = jdbc.queryForObject("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", UUID.class, community);

        // Agreed: two dried fish are worth two days' work to a fed isle.
        var offer = actions.resolve("offer to work for them for two dried fish");
        assertEquals("AGREE_WITH_PEOPLE", offer.intent(), offer::perception);
        assertEquals("SUCCEEDED", offer.outcome(), offer::perception);
        assertEquals(2, (int) jdbc.queryForObject("SELECT days_owed FROM native_agreement WHERE chronicle_id=? AND status='OPEN'", Integer.class, chronicle));

        // A day at their nets: the catch goes to their store. Not twice in one day.
        int fishBefore = stored(store, "dried_fish");
        var day1 = actions.resolve("work for them");
        assertEquals("WORK_FOR_PEOPLE", day1.intent(), day1::perception);
        assertEquals("SUCCEEDED", day1.outcome(), day1::perception);
        assertTrue(stored(store, "dried_fish") > fishBefore, "the day's catch goes up to their store");
        assertEquals("PARTIAL", actions.resolve("work for them").outcome(), "one day's work a day");

        // The last day is paid out of the store into the Chronicle's hands.
        int fishCarried = carried(chronicle, "dried_fish");
        int kept = standing(community, chronicle);
        at("2031-06-11T08:00:00Z");
        var day2 = actions.resolve("work for them");
        assertEquals("SUCCEEDED", day2.outcome(), day2::perception);
        assertEquals("COMPLETED", status(community, chronicle));
        assertEquals(fishCarried + 2, carried(chronicle, "dried_fish"));
        assertEquals(2, (int) jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE transition_type='PAID_BY_COMMUNITY' AND payload->>'community'=?",
            Integer.class, community.toString()), "each object paid carries its own history");
        assertEquals(kept + 5, standing(community, chronicle), "a promise kept");

        // An honest release costs a little.
        actions.resolve("offer to work for them for two dried fish");
        kept = standing(community, chronicle);
        assertEquals("SUCCEEDED", actions.resolve("ask to be released from the work").outcome());
        assertEquals("RELEASED", status(community, chronicle));
        assertEquals(kept - 3, standing(community, chronicle));

        // Owed: two mats promised, and the mats gone from the store before the last day.
        items.createHeldItem(store, "reed_mat", "Reed mat", Instant.parse("2031-06-11T08:00:00Z"), "MADE_BY_COMMUNITY");
        items.createHeldItem(store, "reed_mat", "Reed mat", Instant.parse("2031-06-11T08:00:00Z"), "MADE_BY_COMMUNITY");
        var mats = actions.resolve("offer my labour for two reed mats");
        assertEquals("SUCCEEDED", mats.outcome(), mats::perception);
        int matDays = jdbc.queryForObject("SELECT days_owed FROM native_agreement WHERE chronicle_id=? AND status='OPEN'", Integer.class, chronicle);
        for (int d = 0; d < matDays; d++) {
            at("2031-06-1" + (2 + d) + "T08:00:00Z");
            if (d == matDays - 1)
                jdbc.update("UPDATE world_object w SET current_owner_id=NULL, current_location_id=? FROM item_instance i " +
                    "WHERE i.object_id=w.id AND w.current_owner_id=? AND i.item_key='reed_mat' AND w.lifecycle_state='ACTIVE'", isle, store);
            actions.resolve("work for them");
        }
        assertEquals("OWED", status(community, chronicle), "the store could not pay");
        int matsCarried = carried(chronicle, "reed_mat");
        items.createHeldItem(store, "reed_mat", "Reed mat", Instant.parse("2031-06-15T08:00:00Z"), "MADE_BY_COMMUNITY");
        items.createHeldItem(store, "reed_mat", "Reed mat", Instant.parse("2031-06-15T08:00:00Z"), "MADE_BY_COMMUNITY");
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse("2031-06-15T00:00:00Z")), community);
        natives.advanceTo(Instant.parse("2031-06-16T00:00:00Z"));
        assertEquals("COMPLETED", status(community, chronicle), "the debt is settled when the store can");
        assertTrue(carried(chronicle, "reed_mat") >= matsCarried + 2);

        // Broken: work agreed and never done, past its date.
        at("2031-06-16T08:00:00Z");
        actions.resolve("offer to work for them for two dried fish");
        kept = standing(community, chronicle);
        natives.advanceTo(Instant.parse("2031-06-25T00:00:00Z"));
        assertEquals("BROKEN", status(community, chronicle));
        assertEquals(kept - 15, standing(community, chronicle), "a broken promise is remembered");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind='BROKEN_PROMISE'", Integer.class, community));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
