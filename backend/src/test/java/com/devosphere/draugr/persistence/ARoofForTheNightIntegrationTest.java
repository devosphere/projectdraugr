package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A roof for the night (#113), through the action boundary and the body that feels the weather.
 *
 * <p>Standing in the middle of a village in a storm used to be the same as standing on open marsh, because shelter
 * meant something the Chronicle had built. A people who know you will put you under cover, their roof shelters you
 * exactly as your own would, and the welcome runs out. Skips without Docker.
 */
@SpringBootTest
class ARoofForTheNightIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
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

    @Test
    void theirRoofShelltersAGuestUntilTheWelcomeRunsOut() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        at("2031-06-10T20:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        jdbc.update("UPDATE native_settlement_site SET condition_percent=100 WHERE community_id=?", community);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

        // A stranger they have never spoken to sleeps in the weather, in the middle of their village.
        assertFalse(physiology.sleep(chronicle, 60), "their houses are not a stranger's shelter");

        // Known, and welcome: a door is stood aside from.
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,20,40,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        var asked = actions.resolve("ask for shelter");
        assertEquals("JOIN_PEOPLE", asked.intent(), asked::perception);
        assertEquals("SUCCEEDED", asked.outcome(), asked::perception);
        assertTrue(physiology.sleep(chronicle, 60), "their roof keeps the weather off a guest exactly as one you built would");

        // The welcome is for a night, not for a season.
        jdbc.update("UPDATE native_guest_right SET welcome_until=now() - interval '1 hour' WHERE community_id=? AND chronicle_id=?", community, chronicle);
        assertFalse(physiology.sleep(chronicle, 60), "a welcome that has run out is not a roof");

        // Hospitality has a limit: three nights in ten days.
        for (int night = 0; night < 2; night++) assertEquals("SUCCEEDED", actions.resolve("ask for shelter").outcome());
        var fourth = actions.resolve("ask for shelter");
        assertEquals("PARTIAL", fourth.outcome(), fourth::perception);
        assertTrue(fourth.perception().contains("three times"), fourth::perception);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
