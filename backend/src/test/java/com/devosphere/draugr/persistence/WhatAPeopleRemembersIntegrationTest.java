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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a people remembers (#114), through the public action boundary.
 *
 * <p>One Chronicle and one reedkin isle, and the whole arc the ticket asks for: walking onto the isle unasked is
 * trespass; a theft by night from an unwatched store is not seen, though the loss is found and the watch doubled; a
 * theft in daylight is seen by everyone and turns the isle against the Chronicle, so contact is refused; returning
 * what was taken, apologising once, making restitution and working beside them each rebuild a little; binding a
 * person is resisted and fails; a killing leaves a body where it fell and a people who drive the Chronicle off with
 * stones. The history holds all of it, and nobody is anybody's loot. Skips without Docker.
 */
@SpringBootTest
class WhatAPeopleRemembersIntegrationTest {

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
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    /** Set the hour; the body keeps pace, so what is measured is conduct and not thirst. */
    private void at(String iso) {
        Timestamp t = Timestamp.from(Instant.parse(iso));
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", t);
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", t);
    }

    private int standing(UUID community, UUID chronicle) {
        return jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle);
    }

    private String last(UUID community) {
        return jdbc.queryForObject("SELECT event_kind FROM native_event WHERE community_id=? ORDER BY id DESC LIMIT 1", String.class, community);
    }

    private boolean happened(UUID community, String kind) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND event_kind=?)", Boolean.class, community, kind));
    }

    private String posture(UUID community) {
        return jdbc.queryForObject("SELECT security_posture FROM native_community WHERE id=?", String.class, community);
    }

    @Test
    void offenceIsRememberedAndAmendsCountForLittleAndSlowly() {
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
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T12:00:00Z")), community);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,0,30,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));

        // Trespass: stepping onto the isle from the next chunk over, unasked.
        // From whichever neighbour is walkable ground, walking the way that leads onto the isle (east is +x, south +y).
        Map<String, Object> from = jdbc.queryForMap(
            "SELECT n.id, CASE WHEN n.grid_x < i.grid_x THEN 'walk east' WHEN n.grid_x > i.grid_x THEN 'walk west' " +
            "WHEN n.grid_y < i.grid_y THEN 'walk south' ELSE 'walk north' END AS way " +
            "FROM world_chunk i JOIN world_chunk n ON n.world_id=i.world_id AND abs(n.grid_x-i.grid_x)+abs(n.grid_y-i.grid_y)=1 " +
            "WHERE i.id=? AND n.biome <> 'OCEAN' ORDER BY n.grid_y, n.grid_x LIMIT 1", isle);
        UUID beside = (UUID) from.get("id");
        String inward = (String) from.get("way");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", beside, chronicle);
        var walkedIn = actions.resolve(inward);
        assertEquals(isle, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle), () -> "onto the isle: " + walkedIn.perception());
        assertTrue(happened(community, "BOUNDARY_TRESPASS"), () -> "walking in unasked is trespass: " + walkedIn.perception());
        assertEquals(-10, standing(community, chronicle));
        assertEquals("GUARDED", posture(community));

        // By night, from a store no one is watching: unseen. Nothing is held against the Chronicle, but the loss is found.
        jdbc.update("UPDATE native_community SET security_posture='WARY' WHERE id=?", community);
        at("2031-06-10T23:00:00Z");
        var night = actions.resolve("steal from their store");
        assertEquals("CONDUCT_TOWARD_PEOPLE", night.intent());
        assertEquals("SUCCEEDED", night.outcome(), night::perception);
        assertEquals(-10, standing(community, chronicle), "an unseen theft is not held against anyone");
        assertTrue(happened(community, "GOODS_MISSING"), "but the store keeper finds the loss");
        assertEquals("GUARDED", posture(community), "and posts a watch");

        // In daylight, seen by the whole isle: the isle turns against the Chronicle, and contact is refused.
        at("2031-06-11T12:00:00Z");
        actions.resolve("steal from their store");
        assertTrue(standing(community, chronicle) <= -30, "a witnessed food theft is grave");
        assertEquals("HOSTILE", posture(community));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT access_rule FROM native_settlement_site WHERE community_id=? AND holds_stores", String.class, community));
        actions.resolve("call out to them");
        assertEquals("CONTACT_ANNOUNCE_PRESENCE", last(community));
        assertEquals("WARNED_AWAY", jdbc.queryForObject("SELECT payload->>'response' FROM native_event WHERE community_id=? ORDER BY id DESC LIMIT 1", String.class, community));

        // Amends: each counts a little. Returning what was taken, apologising (once), restitution, shared work.
        int before = standing(community, chronicle);
        actions.resolve("return the dried fish");
        assertEquals(before + 8, standing(community, chronicle), "returning stolen food is restitution");
        before = standing(community, chronicle);
        actions.resolve("apologise to them");
        assertEquals(before + 5, standing(community, chronicle));
        actions.resolve("apologise to them");
        assertEquals(before + 5, standing(community, chronicle), "words said again are not more sorrow");
        before = standing(community, chronicle);
        actions.resolve("make restitution");
        assertEquals(before + 8, standing(community, chronicle), "a thing given in payment for what is owed");

        // Seizing a person: resisted, and among the gravest acts.
        var bind = actions.resolve("tie up the elder");
        assertEquals("FAILED", bind.outcome(), () -> "a person cannot be bound and led: " + bind.perception());
        assertTrue(happened(community, "RESTRAINT_ATTEMPT"));

        // A killing: the body stays where it fell, marked dead, and the people become hostile for good.
        UUID spear = items.createCarriedItem(chronicle, "primitive_spear", "Primitive spear", Instant.now(), "TEST_FIXTURE");
        jdbc.update("INSERT INTO equipment_attachment (item_id, chronicle_id, body_position, layer, attached_at) VALUES (?,?,'HAND_RIGHT','OUTER',now())", spear, chronicle);
        actions.resolve("attack the reedkin");
        assertTrue(happened(community, "MURDER"));
        // The one killed here is the one whose body still lies on the ground; anyone the isle has buried since (#121)
        // is DESTROYED and is not what this assertion is about.
        Map<String, Object> dead = jdbc.queryForMap("SELECT n.object_id, w.lifecycle_state, w.current_location_id, w.display_name FROM native_individual n " +
            "JOIN world_object w ON w.id=n.object_id WHERE n.community_id=? AND n.condition='DEAD' AND w.lifecycle_state='ACTIVE'", community);
        assertEquals("ACTIVE", dead.get("lifecycle_state"), "the body is not destroyed or looted");
        assertEquals(isle, dead.get("current_location_id"), "it lies where it fell");
        assertTrue(((String) dead.get("display_name")).startsWith("The body of"));
        assertTrue(jdbc.queryForObject("SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle) > 0,
            "they answer in kind");

        // Back onto the isle in daylight, and they do not wait to be spoken to.
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", beside, chronicle);
        at("2031-06-12T12:00:00Z");
        actions.resolve(inward);
        assertTrue(happened(community, "DROVE_OFF"), "a people who hate you drive you off");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
