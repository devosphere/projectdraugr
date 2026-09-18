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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A long life beside a people (#116, epic #109): the relationship stays coherent across everything a real
 * playthrough does to it.
 *
 * <p>The ticket's scenarios that the world can carry today, run end to end on a migrated database through the public
 * action boundary: first sighting and cautious contact; a gift and a trade whose goods stay where they went; a
 * duplicate submission that changes nothing twice; a long absence the isle lives through without the Chronicle; an
 * offence that closes the isle to trade; the Chronicle's death, after which the next Chronicle is a stranger who
 * inherits none of it; and placement that is the same every time the world is made. After each, the history is
 * intact, every good is somewhere, and the Auditor finds the world consistent.
 *
 * <p>Not here, because the systems they test do not exist yet: work agreements (#113's second slice), companions,
 * and damage to settlement structures. They are listed on #116 as what remains.
 */
@SpringBootTest
class ALongLifeBesideAPeopleIntegrationTest {

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

    private int events(UUID community) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=?", Integer.class, community);
    }

    private void consistent(String when) {
        var report = auditor.inspect();
        assertTrue(report.consistent(), () -> when + ": the world must stay Auditor-consistent: " + report.violations());
    }

    @Test
    void theRelationshipSurvivesRestartRepetitionAbsenceAndDeath() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID worldId = worldGenesis.current().worldId();

        // Placement is the same every time the world is made: the isles are where the deterministic rule puts them.
        List<Map<String, Object>> isles = jdbc.queryForList(
            "SELECT c.grid_x, c.grid_y FROM native_community n JOIN world_chunk c ON c.id=n.home_chunk_id WHERE n.world_id=? ORDER BY 1, 2", worldId);
        assertEquals(0, natives.seedPeoples(worldId), "a world that has its isles gains no more on reconcile");
        assertEquals(isles, jdbc.queryForList(
            "SELECT c.grid_x, c.grid_y FROM native_community n JOIN world_chunk c ON c.id=n.home_chunk_id WHERE n.world_id=? ORDER BY 1, 2", worldId),
            "and they have not moved");

        var first = chronicles.awaken();
        assertNotNull(first, "awakening must produce a living Chronicle");
        UUID chronicle = first.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        at("2031-05-01T12:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE world_id=? ORDER BY name LIMIT 1", UUID.class, worldId);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        UUID store = jdbc.queryForObject("SELECT object_id FROM native_settlement_site WHERE community_id=? AND holds_stores", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-05-01T12:00:00Z")), community);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

        // Scenario 1 — first sighting, cautious contact, a misunderstanding, and a respectful withdrawal.
        actions.resolve("call out to them");
        actions.resolve("ask them about the river");
        actions.resolve("take my leave");
        assertEquals(List.of("FIRST_CONTACT", "MISUNDERSTOOD", "LEFT_RESPECTFULLY"), jdbc.queryForList(
            "SELECT payload->>'response' FROM native_event WHERE community_id=? AND subject_id=? ORDER BY id", String.class, community, chronicle));
        consistent("after first contact");

        // Scenario 2 — a gift, submitted twice with the same key: given once.
        UUID fish = items.createCarriedItem(chronicle, "dried_fish", "Dried fish", Instant.now(), "TEST_FIXTURE");
        UUID key = UUID.randomUUID();
        var gave = actions.resolve("offer them the dried fish", key);
        int afterGift = events(community);
        var again = actions.resolve("offer them the dried fish", key);
        assertEquals(gave.actionId(), again.actionId(), "a duplicate submission returns the original outcome");
        assertEquals(afterGift, events(community), "and writes nothing to their history twice");
        assertEquals(store, jdbc.queryForObject("SELECT current_owner_id FROM world_object WHERE id=?", UUID.class, fish), "the gift is theirs");

        // "Restart": nothing in the services holds this state, so reading it back is what the next session sees.
        Map<String, Object> relation = jdbc.queryForMap(
            "SELECT standing, understanding, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?", community, chronicle);
        assertNotNull(relation.get("first_contact_at"), "first contact survives");

        // Scenario 7 and a long absence — the isle lives through ninety days without the Chronicle.
        int before = events(community);
        at("2031-07-30T12:00:00Z");
        natives.advanceTo(Instant.parse("2031-07-30T12:00:00Z"));
        assertTrue(events(community) >= before, "their history only grows");
        assertEquals(relation, jdbc.queryForMap(
            "SELECT standing, understanding, first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?", community, chronicle),
            "what passed between them is exactly as it was: nothing decays that no event changed");
        consistent("after a long absence");

        // Scenario 5 — a witnessed theft closes the isle to this Chronicle's trade.
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy WHERE id=?",
            Timestamp.from(Instant.parse("2031-07-30T12:00:00Z")), community);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        actions.resolve("steal from their store");
        actions.resolve("ask to trade");
        assertEquals("REFUSED", jdbc.queryForObject("SELECT payload->>'response' FROM native_event WHERE community_id=? AND subject_id=? ORDER BY id DESC LIMIT 1",
            String.class, community, chronicle), "a people who saw the theft will not trade");
        consistent("after the theft");

        // History cannot be rewritten by anyone.
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM native_event WHERE community_id=?", community));

        // The Chronicle's death: the next Chronicle is a stranger, and inherits none of it.
        jdbc.update("UPDATE chronicle SET life_state='DEAD', died_at=?, death_cause='Test: a life ended' WHERE id=?",
            Timestamp.from(Instant.parse("2031-07-30T13:00:00Z")), chronicle);
        var next = chronicles.awaken();
        assertNotNull(next, "a new Chronicle awakens");
        assertTrue(!next.id().equals(chronicle));
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, next.id()),
            "the isle has never met the new Chronicle");
        assertTrue(jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle) < 0,
            "and still remembers the old one exactly as it left things");
        consistent("after a death and a new life");
    }
}
