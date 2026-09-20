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
 * The dead keep what was theirs (#122), through the action boundary and the world clock.
 *
 * <p>Everyone grown on an isle owns the tool of their work. Killed, they still own it: taking it is robbing the
 * dead and is remembered as such, giving it back is restitution, and burying them counts for something without
 * undoing anything. Left alone, their own people bury them and gather up what was theirs. Skips without Docker.
 */
@SpringBootTest
class TheDeadKeepWhatWasTheirsIntegrationTest {

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

    private int standing(UUID community, UUID chronicle) {
        return jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle);
    }

    @Test
    void whatTheyOwnedIsStillTheirsWhenTheyAreDead() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        at("2031-06-10T12:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,20,40,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

        // Everyone grown owns the tool of their work.
        natives.advanceTo(Instant.parse("2031-06-11T00:00:00Z"));
        int grown = jdbc.queryForObject("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.condition <> 'DEAD' AND n.life_stage <> 'CHILD' AND w.lifecycle_state='ACTIVE'", Integer.class, community);
        int owned = jdbc.queryForObject("SELECT COUNT(*) FROM world_object o JOIN native_individual n ON n.object_id=o.current_owner_id " +
            "WHERE n.community_id=? AND o.lifecycle_state='ACTIVE'", Integer.class, community);
        assertTrue(owned >= grown, () -> "each grown person keeps something of their own: " + owned + " things among " + grown + " people");

        // A killing. The body lies there, and what they owned is still theirs.
        at("2031-06-11T12:00:00Z");
        UUID spear = items.createCarriedItem(chronicle, "primitive_spear", "Primitive spear", Instant.now(), "TEST_FIXTURE");
        jdbc.update("INSERT INTO equipment_attachment (item_id, chronicle_id, body_position, layer, attached_at) VALUES (?,?,'HAND_RIGHT','OUTER',now())", spear, chronicle);
        actions.resolve("attack the reedkin");
        Map<String, Object> dead = jdbc.queryForMap("SELECT n.object_id, n.given_name FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.condition='DEAD' AND w.lifecycle_state='ACTIVE'", community);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'",
            Integer.class, dead.get("object_id")) >= 1, "the dead still own what was theirs");

        // Robbing them: seen, and never forgotten.
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicle);
        int before = standing(community, chronicle);
        var robbed = actions.resolve("take their belongings");
        assertEquals("CONDUCT_TOWARD_PEOPLE", robbed.intent(), robbed::perception);
        assertTrue(standing(community, chronicle) <= before - 60 || standing(community, chronicle) == -100, robbed::perception);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind='GRAVE_ROBBING'", Integer.class, community) == 1);
        UUID taken = jdbc.queryForObject("SELECT o.id FROM world_object o JOIN object_transition t ON t.object_id=o.id " +
            "WHERE t.transition_type='ROBBED_FROM_THE_DEAD' AND o.current_owner_id=? LIMIT 1", UUID.class, chronicle);
        assertNotNull(taken, "what was taken is carried, with its history");

        // Giving it back is restitution, on the same footing as returning anything else of theirs.
        String name = jdbc.queryForObject("SELECT display_name FROM world_object WHERE id=?", String.class, taken);
        before = standing(community, chronicle);
        var back = actions.resolve("return the " + name.toLowerCase(java.util.Locale.ROOT));
        assertTrue(standing(community, chronicle) > before, () -> "giving back what was robbed counts: " + back.perception());

        // Burying them counts for something, and the grave is a place with their name on it.
        before = standing(community, chronicle);
        var buried = actions.resolve("bury them");
        assertEquals("SUCCEEDED", buried.outcome(), buried::perception);
        assertEquals(before + 10, standing(community, chronicle), "a killer who buries them is not forgiven, but it counts");
        assertEquals("BURIED", jdbc.queryForObject("SELECT destroyed_cause FROM world_object WHERE id=?", String.class, dead.get("object_id")));
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE object_type='NATIVE_GRAVE' AND display_name=? AND current_location_id=?",
            Integer.class, "The grave of " + dead.get("given_name"), isle));

        // And what the dead still owned, their own people gather up.
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse("2031-06-11T00:00:00Z")), community);
        natives.advanceTo(Instant.parse("2031-06-16T00:00:00Z"));
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM world_object o JOIN native_individual n ON n.object_id=o.current_owner_id " +
            "WHERE n.community_id=? AND n.condition='DEAD' AND o.lifecycle_state='ACTIVE'", Integer.class, community),
            "nothing is left owned by the dead");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
