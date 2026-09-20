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
 * What a people's classification permits (#110), asserted where it is read.
 *
 * <p>V344 declared four columns saying which social acts are meaningful toward a species, and for a while nothing
 * read them: being PEOPLE was enough to reach every act. This turns each column off in turn against a people who
 * otherwise would say yes, and asserts the act is refused — and then turns it back on and asserts it is not. A
 * declaration nothing reads is the defect this guards. Skips without Docker.
 */
@SpringBootTest
class WhatTheirClassificationPermitsIntegrationTest {

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
    void restoreClock() {
        jdbc.update("UPDATE cognition_profile SET trade_eligible=TRUE, agreement_eligible=TRUE, companionship_eligible=TRUE, " +
            "community_membership=TRUE, working_relationship_eligible=TRUE WHERE species_key='reedkin'");
        if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore);
    }

    private void off(String column) {
        jdbc.update("UPDATE cognition_profile SET " + column + "=FALSE WHERE species_key='reedkin'");
    }

    private void on(String column) {
        jdbc.update("UPDATE cognition_profile SET " + column + "=TRUE WHERE species_key='reedkin'");
    }

    @Test
    void anActTheirKindDoesNotDoIsRefusedHoweverWellLikedYouAre() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Timestamp t = Timestamp.from(Instant.parse("2031-06-10T12:00:00Z"));
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", t);
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", t);
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        UUID store = jdbc.queryForObject("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", UUID.class, community);
        for (int i = 0; i < 20; i++) items.createHeldItem(store, "dried_fish", "Dried fish", Instant.parse("2031-06-10T06:00:00Z"), "GATHERED_BY_COMMUNITY");
        // Known a long time and trusted as far as anyone is: nothing below is refused for want of standing.
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,90,90,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-01-01T12:00:00Z")));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

        off("trade_eligible");
        var noTrade = actions.resolve("see what they have");
        assertEquals("TRADE_WITH_PEOPLE", noTrade.intent(), noTrade::perception);
        assertTrue(noTrade.perception().contains("not barter"), noTrade::perception);
        on("trade_eligible");
        assertEquals("SUCCEEDED", actions.resolve("see what they have").outcome(), "and with the same standing, they do");

        off("agreement_eligible");
        var noWork = actions.resolve("offer to work for them for two dried fish");
        assertEquals("AGREE_WITH_PEOPLE", noWork.intent(), noWork::perception);
        assertTrue(noWork.perception().contains("do not bind themselves"), noWork::perception);
        on("agreement_eligible");

        off("companionship_eligible");
        var noCompany = actions.resolve("ask them to travel with me");
        assertEquals("COMPANION_PEOPLE", noCompany.intent(), noCompany::perception);
        assertTrue(noCompany.perception().contains("do not leave with strangers"), noCompany::perception);
        on("companionship_eligible");

        off("community_membership");
        var noPlace = actions.resolve("ask to join them");
        assertEquals("JOIN_PEOPLE", noPlace.intent(), noPlace::perception);
        assertTrue(noPlace.perception().contains("no door in it"), noPlace::perception);
        on("community_membership");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
