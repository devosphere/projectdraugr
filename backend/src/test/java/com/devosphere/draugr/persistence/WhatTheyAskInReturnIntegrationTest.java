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
 * What they ask in return (#211), through the action boundary and the world clock.
 *
 * <p>A witnessed wrong is priced at the moment it is seen. The Chronicle can ask what would put it right and be
 * told; part payment is part paid; paying it in full is the largest single step back, and lifts the isle from
 * hostile to watchful. A claim nobody answers hardens into a grievance a month later. Skips without Docker.
 */
@SpringBootTest
class WhatTheyAskInReturnIntegrationTest {

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
    void aWrongIsPricedAskedAboutPaidOrLeftStanding() {
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
            "security_posture='WARY' WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,0,40,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        UUID store = jdbc.queryForObject("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", UUID.class, community);
        for (int i = 0; i < 6; i++) items.createHeldItem(store, "dried_fish", "Dried fish", Instant.parse("2031-06-10T06:00:00Z"), "GATHERED_BY_COMMUNITY");

        // Seen taking their food: priced the moment it is seen.
        actions.resolve("steal from their store");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_claim WHERE community_id=? AND chronicle_id=? AND status='OPEN'",
            Integer.class, community, chronicle), "a witnessed wrong is a claim");
        int price = jdbc.queryForObject("SELECT price FROM native_claim WHERE community_id=? AND chronicle_id=? AND status='OPEN'",
            Integer.class, community, chronicle);
        assertTrue(price >= 6, "and it has a price in it");

        // Asked, and told.
        var asked = actions.resolve("ask what they demand");
        assertEquals("SETTLE_CLAIM", asked.intent(), asked::perception);
        assertEquals("SUCCEEDED", asked.outcome(), asked::perception);
        assertTrue(asked.perception().contains(String.valueOf(price)), asked::perception);

        // Part payment is part paid: what is in hand is the three fish just taken from them, and three fish do
        // not answer a food theft.
        int before = standing(community, chronicle);
        var part = actions.resolve("pay compensation");
        assertEquals("PARTIAL", part.outcome(), part::perception);
        assertTrue(standing(community, chronicle) > before, "part payment counts for something");
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM native_claim WHERE community_id=? AND chronicle_id=?", String.class, community, chronicle));

        // Paid in full: the largest single step back, and the isle stops treating them as an enemy.
        jdbc.update("UPDATE native_community SET security_posture='HOSTILE' WHERE id=?", community);
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "bone_knife", "Bone knife", Instant.parse("2031-06-10T12:00:00Z"), "TEST_FIXTURE");
        before = standing(community, chronicle);
        var paid = actions.resolve("pay what they ask");
        assertEquals("SUCCEEDED", paid.outcome(), paid::perception);
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM native_claim WHERE community_id=? AND chronicle_id=? ORDER BY demanded_at DESC LIMIT 1",
            String.class, community, chronicle));
        assertEquals(before + 25, standing(community, chronicle));
        assertEquals("GUARDED", jdbc.queryForObject("SELECT security_posture FROM native_community WHERE id=?", String.class, community));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE transition_type='PAID_IN_COMPENSATION'", Integer.class) > 0,
            "what was paid went to them, object by object");

        // A second wrong, left standing: a month later it hardens.
        at("2031-06-11T12:00:00Z");
        for (int i = 0; i < 6; i++) items.createHeldItem(store, "dried_fish", "Dried fish", Instant.parse("2031-06-11T06:00:00Z"), "GATHERED_BY_COMMUNITY");
        actions.resolve("steal from their store");
        before = standing(community, chronicle);
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse("2031-07-12T00:00:00Z")), community);
        natives.advanceTo(Instant.parse("2031-07-13T00:00:00Z"));
        assertEquals("UNANSWERED", jdbc.queryForObject("SELECT status FROM native_claim WHERE community_id=? AND chronicle_id=? ORDER BY demanded_at DESC LIMIT 1",
            String.class, community, chronicle));
        assertTrue(standing(community, chronicle) < before, "a claim nobody answers is not forgotten");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
