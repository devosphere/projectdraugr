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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who speaks for the isle (#121), through the public action boundary and the world clock.
 *
 * <p>The Chronicle is told who speaks for the isle only by asking. When the speaker dies the tenure ends and the
 * office is empty; while the isle mourns, nothing is traded with outsiders. After the mourning the elders give the
 * office to the eldest grown adult — someone who was already there — and the history records the loss and the choice.
 * Condolence is a kindness once, and from the one who did the killing it is refused. Skips without Docker.
 */
@SpringBootTest
class WhoSpeaksForTheIsleIntegrationTest {

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

    private void at(String iso) {
        Timestamp t = Timestamp.from(Instant.parse(iso));
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", t);
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", t);
    }

    private List<String> history(UUID community) {
        return jdbc.queryForList("SELECT event_kind FROM native_event WHERE community_id=? ORDER BY id", String.class, community);
    }

    @Test
    void theOfficeIsHeldLostMournedAndFilled() {
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
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,20,60,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

        // Asked, and told: the speaker comes down to the landing, by name.
        Map<String, Object> speaker = jdbc.queryForMap("SELECT object_id, given_name FROM native_individual WHERE community_id=? AND role='HEADSPERSON'", community);
        var asked = actions.resolve("ask who speaks for them");
        assertEquals("ADDRESS_PEOPLE", asked.intent(), asked::perception);
        assertTrue(asked.perception().contains((String) speaker.get("given_name")), asked::perception);
        var roles = actions.resolve("ask who does what");
        assertEquals("SUCCEEDED", roles.outcome(), roles::perception);
        assertTrue(roles.perception().contains("speaks for the isle"), roles::perception);

        // The daily step opens a tenure for the speaker who has held the office since before tenures existed.
        natives.advanceTo(Instant.parse("2031-06-11T00:00:00Z"));
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_role_tenure WHERE community_id=? AND role='HEADSPERSON' AND ended_at IS NULL",
            Integer.class, community));

        // The speaker dies. The tenure ends; the office is empty; nothing is traded while they mourn.
        jdbc.update("UPDATE native_individual SET condition='DEAD', available=FALSE WHERE object_id=?", speaker.get("object_id"));
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=now(), destroyed_location_id=?, destroyed_cause='BURIED', " +
            "current_location_id=NULL WHERE id=?", isle, speaker.get("object_id"));
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, payload) VALUES (?,?,'DIED_OF_AGE',jsonb_build_object('name',?::text))",
            community, Timestamp.from(Instant.parse("2031-06-11T06:00:00Z")), speaker.get("given_name"));
        natives.advanceTo(Instant.parse("2031-06-12T00:00:00Z"));
        assertEquals("DIED", jdbc.queryForObject("SELECT end_reason FROM native_role_tenure WHERE individual_id=?", String.class, speaker.get("object_id")));
        assertTrue(history(community).contains("SPEAKER_LOST"));
        at("2031-06-12T12:00:00Z");
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse("2031-06-12T12:00:00Z")), community);
        var trade = actions.resolve("trade with them");
        assertEquals("TRADE_WITH_PEOPLE", trade.intent(), trade::perception);
        assertTrue(trade.perception().contains("mourning"), () -> "no one decides for an isle without a speaker: " + trade.perception());
        assertTrue(actions.resolve("ask who speaks for them").perception().contains("No one speaks for them now"));

        // Condolence: a kindness once, not twice.
        int before = jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle);
        assertEquals("SUCCEEDED", actions.resolve("offer my condolences").outcome());
        assertEquals("PARTIAL", actions.resolve("offer my condolences").outcome());
        assertEquals(before + 3, (int) jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle));

        // After the mourning: the eldest grown adult, someone who was already there, speaks for them now.
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse("2031-06-12T00:00:00Z")), community);
        int living = jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND condition <> 'DEAD'", Integer.class, community);
        natives.advanceTo(Instant.parse("2031-06-16T00:00:00Z"));
        Map<String, Object> next = jdbc.queryForMap("SELECT object_id, given_name, life_stage FROM native_individual WHERE community_id=? AND role='HEADSPERSON' AND condition <> 'DEAD'", community);
        assertNotEquals(speaker.get("object_id"), next.get("object_id"));
        assertEquals("ADULT", next.get("life_stage"));
        assertTrue(history(community).contains("SUCCESSION"));
        assertEquals(living, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND condition <> 'DEAD'", Integer.class, community),
            "no one is created to fill the office");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_role_tenure WHERE community_id=? AND role='HEADSPERSON' AND ended_at IS NULL", Integer.class, community));
        at("2031-06-16T12:00:00Z");
        assertTrue(actions.resolve("ask who speaks for them").perception().contains((String) next.get("given_name")));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
