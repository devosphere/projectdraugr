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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one isle hears of what a Chronicle did at the other (#114), through the action boundary and the world clock.
 *
 * <p>A theft seen at one isle is told at its kin isle once news has had time to travel, as hearsay: half the weight,
 * once only, and wary rather than hostile. A theft no one saw cannot be told. Skips without Docker.
 */
@SpringBootTest
class NewsBetweenIslesIntegrationTest {

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

    private int heard(UUID community) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind='HEARD_OF'", Integer.class, community);
    }

    @Test
    void whatWasSeenAtOneIsleIsToldAtTheOther() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        List<UUID> isles = jdbc.queryForList("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name", UUID.class);
        assertEquals(2, isles.size(), "the world has two kin isles");
        UUID seen = isles.get(0), told = isles.get(1);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, seen);
        // Put the kin isle a fixed walk away, so the news takes a known time: six chunks is two days' telling.
        UUID near = jdbc.queryForObject("SELECT k.id FROM world_chunk k JOIN world_chunk i ON i.id=? AND k.world_id=i.world_id " +
            "WHERE greatest(abs(k.grid_x-i.grid_x), abs(k.grid_y-i.grid_y)) = 6 ORDER BY k.grid_y, k.grid_x LIMIT 1", UUID.class, isle);
        jdbc.update("UPDATE native_community SET home_chunk_id=? WHERE id=?", near, told);
        Timestamp start = Timestamp.from(Instant.parse("2031-06-10T00:00:00Z"));
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture='WARY' WHERE species_key='reedkin'", start);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,0,30,?)",
            seen, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

        // Unseen, by night: nothing to tell.
        at("2031-06-10T23:00:00Z");
        assertEquals("SUCCEEDED", actions.resolve("steal from their store").outcome());
        // Seen, in daylight.
        at("2031-06-11T12:00:00Z");
        jdbc.update("UPDATE native_community SET security_posture='WARY' WHERE id=?", seen);
        actions.resolve("steal from their store");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind IN ('FOOD_THEFT','PROPERTY_THEFT') " +
            "AND payload->>'witnessed'='true'", Integer.class, seen) == 1, "one theft was seen");

        // Not yet: news takes time.
        natives.advanceTo(Instant.parse("2031-06-12T00:00:00Z"));
        assertEquals(0, heard(told), "news has not reached the kin isle in a day");

        // Two days on, it has been told there: once, as hearsay, and only the theft that was seen.
        natives.advanceTo(Instant.parse("2031-06-15T00:00:00Z"));
        assertEquals(1, heard(told), "the seen theft is told; the unseen one cannot be");
        String kind = jdbc.queryForObject("SELECT payload->>'what' FROM native_event WHERE community_id=? AND event_kind='HEARD_OF'", String.class, told);
        int standing = jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, told, chronicle);
        assertEquals("FOOD_THEFT".equals(kind) ? -15 : -10, standing, "hearsay weighs about half what was seen");
        assertTrue(jdbc.queryForObject("SELECT first_contact_at IS NULL FROM community_relation WHERE community_id=? AND chronicle_id=?", Boolean.class, told, chronicle),
            "they know of you before they have met you");
        natives.advanceTo(Instant.parse("2031-06-20T00:00:00Z"));
        assertEquals(1, heard(told), "told once, not every day");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
