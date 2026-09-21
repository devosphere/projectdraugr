package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
 * A second people (#115, #118): the grovebound.
 *
 * <p>The first people took a migration and five services. The second is supposed to take a migration, and that is
 * what this asserts: a grove in old-growth forest, away from the isles, with its own staple, its own made goods and
 * its own answers — and every system built for the reedkin working on it without being taught about it.
 *
 * <p>The two refusals are the point of the whole classification (#110). The grovebound do not leave their grove
 * with strangers and nobody becomes one of them, and those are columns in \`cognition_profile\`, not code. Skips
 * without Docker.
 */
@SpringBootTest
class ASecondPeopleIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockBefore;

    @BeforeEach
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    @Test
    void theGroveStandsWithItsOwnLifeAndItsOwnRefusals() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant noon = Instant.parse("2031-06-10T12:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(noon));
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", Timestamp.from(noon));

        // The world has both peoples, and only one grove.
        UUID grove = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='grovebound'", UUID.class);
        assertNotNull(grove, "the second people is placed at genesis");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_community WHERE species_key='grovebound'", Integer.class),
            "a rooted people is one place, not a population");
        assertEquals(0, natives.seedPeoples(worldGenesis.current().worldId()), "a world that has its peoples gains no more on reconcile");

        // The defect this shape exists to prevent (#115): a world that already has its isles must still be asked
        // about the grove. While the grove was seeded inside the reedkin's own placement, a full set of isles
        // returned early and the second people was never placed in any world already being played.
        UUID worldId = worldGenesis.current().worldId();
        jdbc.update("DELETE FROM native_event WHERE community_id=?", grove);
        jdbc.update("DELETE FROM native_individual WHERE community_id=?", grove);
        jdbc.update("DELETE FROM native_kin_group WHERE community_id=?", grove);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=now(), destroyed_location_id=current_location_id, " +
            "destroyed_cause='TEST_FIXTURE', current_location_id=NULL, current_owner_id=NULL FROM native_settlement_site s " +
            "WHERE s.object_id=world_object.id AND s.community_id=?", grove);
        jdbc.update("DELETE FROM native_settlement_site WHERE community_id=?", grove);
        jdbc.update("DELETE FROM native_community WHERE id=?", grove);
        assertEquals(1, natives.seedPeoples(worldId), "a world with its isles is still asked about the grove");
        grove = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='grovebound'", UUID.class);
        assertNotNull(grove, "and the grove is placed into a world that already had its first people");

        UUID ground = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, grove);
        assertEquals("TEMPERATE_FOREST", jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, ground));
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM native_community o JOIN world_chunk oh ON oh.id=o.home_chunk_id, world_chunk g " +
            "WHERE g.id=? AND o.species_key <> 'grovebound' AND greatest(abs(oh.grid_x-g.grid_x), abs(oh.grid_y-g.grid_y)) < 6",
            Integer.class, ground), "a grove stands well clear of another people's ground");

        // Their own life: eight of them, their own staple in the store, their own goods made by their own hands.
        assertEquals(8, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=?", Integer.class, grove));
        assertEquals("dried_mushroom", jdbc.queryForObject("SELECT staple_item_key FROM native_community WHERE id=?", String.class, grove));
        assertEquals("CANOPY_LOSS", jdbc.queryForObject("SELECT grave_encroachment_kind FROM native_community WHERE id=?", String.class, grove),
            "a people of the old wood cannot forgive the taking of it");
        java.util.List<String> makings = natives.madeGoods(grove);
        assertTrue(makings.contains("bark_sheet"), () -> "they work bark, not reed: " + makings);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "JOIN native_settlement_site s ON s.object_id=w.current_owner_id WHERE s.community_id=? AND i.item_key='dried_mushroom'",
            Integer.class, grove) > 0, "their store holds what they eat");

        // The world's own day runs them exactly as it runs the isles: they gather, they eat, and it is written down.
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(noon.minus(java.time.Duration.ofDays(1))), grove);
        natives.advanceTo(noon);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=?", Integer.class, grove) > 0,
            "their history is kept like anyone's");

        // And their refusals, which are columns and not code (#110).
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground, chronicle);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,90,90,?)",
            grove, chronicle, Timestamp.from(Instant.parse("2031-01-01T12:00:00Z")));

        var asked = actions.resolve("ask them to travel with me");
        assertEquals("COMPANION_PEOPLE", asked.intent(), asked::perception);
        assertEquals("PARTIAL", asked.outcome(), asked::perception);
        assertTrue(asked.perception().contains("do not leave with strangers"), asked::perception);

        var place = actions.resolve("ask to join them");
        assertEquals("JOIN_PEOPLE", place.intent(), place::perception);
        assertEquals("PARTIAL", place.outcome(), place::perception);
        assertTrue(place.perception().contains("no door in it"), place::perception);

        // What they do not refuse: trade, which is open to them and reads their own goods.
        var goods = actions.resolve("see what they have");
        assertEquals("TRADE_WITH_PEOPLE", goods.intent(), goods::perception);
        assertEquals("SUCCEEDED", goods.outcome(), goods::perception);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
