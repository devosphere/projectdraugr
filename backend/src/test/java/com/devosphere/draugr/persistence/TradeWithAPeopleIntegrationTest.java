package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
 * Trade with a people (#113, V348), through the public action boundary.
 *
 * <p>Contact is #112's test, so this one starts from a relation already earned: first contact made, trusted enough to
 * trade, understood well enough to be followed. Then every answer an isle can give: a fair offer taken and both
 * sides moved at once, with provenance; an offer close enough to be met with fewer things, and that counter-offer
 * accepted; an offer not worth considering, declined; nothing done at all while a weapon is in the hand; no food
 * parted with while the isle is hungry; and a thing given back going home to their store. Skips without Docker.
 */
@SpringBootTest
class TradeWithAPeopleIntegrationTest {

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

    private Timestamp clockBefore;

    @BeforeEach
    void inDaylight() {
        clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(Instant.parse("2031-06-10T12:00:00Z")));
    }

    @AfterEach
    void restoreClock() {
        if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore);
    }

    private int owns(UUID owner, String key) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, owner, key);
        return n == null ? 0 : n;
    }

    private String response(UUID community, UUID chronicle) {
        return jdbc.queryForObject("SELECT payload->>'response' FROM native_event WHERE community_id=? AND subject_id=? ORDER BY id DESC LIMIT 1",
            String.class, community, chronicle);
    }

    @Test
    void goodsChangeHandsOnlyAsTheIsleIsWilling() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        UUID store = jdbc.queryForObject("SELECT object_id FROM native_settlement_site WHERE community_id=? AND holds_stores", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T12:00:00Z")), community);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,20,40,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        // Four mats in the store, whatever the makers have or have not added since founding.
        while (owns(store, "reed_mat") < 4) items.createHeldItem(store, "reed_mat", "Reed mat", Instant.now(), "TEST_FIXTURE");

        // Close enough to be met with fewer things: a knife for three mats is answered with two.
        UUID knife = items.createCarriedItem(chronicle, "flint_knife", "Flint knife", Instant.now(), "TEST_FIXTURE");
        var counter = actions.resolve("offer them my flint knife for three reed mats");
        assertEquals("TRADE_WITH_PEOPLE", counter.intent());
        assertEquals("COUNTERED", response(community, chronicle), () -> "a close offer is countered: " + counter.perception());
        assertEquals(0, owns(chronicle, "reed_mat"), "nothing changes hands on a counter-offer");

        // Accepted: both sides move at once, each with its own history.
        var took = actions.resolve("accept their offer");
        assertEquals("SUCCEEDED", took.outcome(), () -> "the counter-offer accepted: " + took.perception());
        assertEquals(2, owns(chronicle, "reed_mat"), "two mats in the Chronicle's hands");
        assertEquals(store, jdbc.queryForObject("SELECT current_owner_id FROM world_object WHERE id=?", UUID.class, knife), "the knife in their store");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='TRADED_TO_COMMUNITY'", Integer.class, knife),
            "the knife's history records where it went");
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM native_trade WHERE community_id=? AND chronicle_id=? ORDER BY offered_at DESC, settled_at DESC NULLS LAST LIMIT 1",
            String.class, community, chronicle));

        // Not worth considering: a bundle of reed for a fish trap.
        items.createCarriedItem(chronicle, "reed_bundle", "Reed Bundle", Instant.now(), "TEST_FIXTURE");
        actions.resolve("trade my reed bundle for the fish trap");
        assertEquals("DECLINED", response(community, chronicle));

        // A weapon in the hand: no trade at all.
        UUID spear = items.createCarriedItem(chronicle, "primitive_spear", "Primitive spear", Instant.now(), "TEST_FIXTURE");
        jdbc.update("INSERT INTO equipment_attachment (item_id, chronicle_id, body_position, layer, attached_at) VALUES (?,?,'HAND_RIGHT','OUTER',now())", spear, chronicle);
        actions.resolve("ask to trade");
        assertEquals("REFUSED", response(community, chronicle));
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", spear);

        // A hungry isle will not part with food.
        jdbc.update("UPDATE native_community SET shortage_days=2 WHERE id=?", community);
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", Instant.now(), "TEST_FIXTURE");
        actions.resolve("offer them my flint knife for dried fish");
        assertEquals("WILL_NOT_PART_WITH_FOOD", response(community, chronicle));
        jdbc.update("UPDATE native_community SET shortage_days=0 WHERE id=?", community);

        // A thing given back goes home to their store.
        int theirMats = owns(store, "reed_mat");
        actions.resolve("return the reed mat");
        assertEquals("RETURNED_PROPERTY", response(community, chronicle));
        assertEquals(theirMats + 1, owns(store, "reed_mat"), "the mat is back in their store");
        assertEquals(1, owns(chronicle, "reed_mat"));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
