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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A place among them (#113), through the action boundary and the world clock.
 *
 * <p>A place is refused to someone they have not known long enough, and given to someone they have. It is a share
 * of a real store, taken openly and once a day; it makes the holder one more mouth the isle has to feed; it makes
 * walking onto the isle no longer trespass; and it ends when they give it up or when the isle stops trusting them.
 * Skips without Docker.
 */
@SpringBootTest
class APlaceAmongThemIntegrationTest {

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
    @Autowired NativeCommunityService natives;
    @Autowired PhysicalItemService items;
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

    private boolean member(UUID community, UUID chronicle) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_membership WHERE community_id=? AND chronicle_id=? AND left_at IS NULL)", Boolean.class, community, chronicle));
    }

    private int carried(UUID chronicle, String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, chronicle, key);
    }

    @Test
    void aPlaceIsGivenSharedAndGivenUp() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        at("2031-06-10T12:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        UUID store = jdbc.queryForObject("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", UUID.class, community);
        for (int i = 0; i < 20; i++) items.createHeldItem(store, "dried_fish", "Dried fish", Instant.parse("2031-06-10T06:00:00Z"), "GATHERED_BY_COMMUNITY");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

        // Newly met, however well liked: not yet.
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,80,80,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-08T12:00:00Z")));
        var tooSoon = actions.resolve("ask to join them");
        assertEquals("JOIN_PEOPLE", tooSoon.intent(), tooSoon::perception);
        assertEquals("PARTIAL", tooSoon.outcome(), tooSoon::perception);
        assertTrue(!member(community, chronicle));

        // Known a season, trusted, and the store full enough: a place.
        jdbc.update("UPDATE community_relation SET first_contact_at=? WHERE community_id=? AND chronicle_id=?",
            Timestamp.from(Instant.parse("2031-04-01T12:00:00Z")), community, chronicle);
        var given = actions.resolve("ask to join them");
        assertEquals("SUCCEEDED", given.outcome(), given::perception);
        assertTrue(member(community, chronicle));

        // A share: one a day, openly, and not theft.
        int had = carried(chronicle, "dried_fish");
        var share = actions.resolve("take my share");
        assertEquals("SUCCEEDED", share.outcome(), share::perception);
        assertEquals(had + 1, carried(chronicle, "dried_fish"));
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE transition_type='SHARE_OF_THE_STORE'", Integer.class));
        assertEquals("PARTIAL", actions.resolve("take my share").outcome(), "one share a day");
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind IN ('FOOD_THEFT','PROPERTY_THEFT')",
            Integer.class, community), "a member's share is not a theft");

        // One more mouth: the isle's day feeds them too.
        int ration = jdbc.queryForObject("SELECT daily_ration FROM native_community WHERE id=?", Integer.class, community);
        int eaten = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE destroyed_cause='EATEN_BY_COMMUNITY'", Integer.class);
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        natives.advanceTo(Instant.parse("2031-06-11T00:00:00Z"));
        int eatersOnTheIsle = jdbc.queryForObject("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE'", Integer.class, community);
        int ateToday = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE destroyed_cause='EATEN_BY_COMMUNITY'", Integer.class) - eaten;
        assertEquals((eatersOnTheIsle + 1) * ration, ateToday, "the isle feeds its member too");

        // Walking onto the isle is no longer walking in unasked.
        Map<String, Object> from = jdbc.queryForMap(
            "SELECT n.id, CASE WHEN n.grid_x < i.grid_x THEN 'walk east' WHEN n.grid_x > i.grid_x THEN 'walk west' " +
            "WHEN n.grid_y < i.grid_y THEN 'walk south' ELSE 'walk north' END AS way " +
            "FROM world_chunk i JOIN world_chunk n ON n.world_id=i.world_id AND abs(n.grid_x-i.grid_x)+abs(n.grid_y-i.grid_y)=1 " +
            "WHERE i.id=? AND n.biome <> 'OCEAN' ORDER BY n.grid_y, n.grid_x LIMIT 1", isle);
        at("2031-06-11T12:00:00Z");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", from.get("id"), chronicle);
        actions.resolve((String) from.get("way"));
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind='BOUNDARY_TRESPASS'",
            Integer.class, community), "someone with a place here is not a stranger walking in");

        // Trust lost: the isle asks them to go, on its own day.
        jdbc.update("UPDATE community_relation SET standing=10 WHERE community_id=? AND chronicle_id=?", community, chronicle);
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse("2031-06-11T00:00:00Z")), community);
        natives.advanceTo(Instant.parse("2031-06-12T00:00:00Z"));
        assertTrue(!member(community, chronicle), "a member they have stopped trusting is asked to go");
        assertEquals("ASKED_TO_LEAVE", jdbc.queryForObject("SELECT end_reason FROM native_membership WHERE community_id=? AND chronicle_id=? ORDER BY joined_at DESC LIMIT 1",
            String.class, community, chronicle));
        assertEquals("FAILED", actions.resolve("take my share").outcome(), "no place, no share");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
