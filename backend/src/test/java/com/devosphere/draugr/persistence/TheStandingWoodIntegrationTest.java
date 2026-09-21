package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
 * The standing wood (#211, and the grovebound's last missing system, #118).
 *
 * <p>Working a people's ground has been a small, answerable encroachment since V351. Felling is not that: it does
 * not disturb the ground, it takes it. A reed-isle people's poles, withies, fuel and cover all come off the carr
 * around their water, so the standing wood is the thing their life is made of, and they answer its loss six times
 * harder than an ordinary notice — and not at all forgivingly, because leave to cut reeds is not leave to fell
 * alders. Three fellings inside a month make an enemy. Skips without Docker.
 */
@SpringBootTest
class TheStandingWoodIntegrationTest {

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
    @Autowired NativeCommunityService natives;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockBefore;

    @BeforeEach
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    private int standing(UUID community, UUID chronicle) {
        return jdbc.queryForObject("SELECT standing FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle);
    }

    private int told(UUID community, String kind) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind=?", Integer.class, community, kind);
    }

    @Test
    void takingTheStandingWoodIsNotTheSameAsWorkingTheirGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        assertEquals("CANOPY_LOSS", jdbc.queryForObject("SELECT grave_encroachment_kind FROM native_community WHERE id=?", String.class, community),
            "a reed-isle people answers the loss of the carr");

        // Ground of theirs, one chunk off the isle.
        UUID theirGround = jdbc.queryForObject(
            "SELECT n.id FROM world_chunk i JOIN world_chunk n ON n.world_id=i.world_id AND n.id <> i.id " +
            "AND greatest(abs(n.grid_x-i.grid_x), abs(n.grid_y-i.grid_y)) = 1 WHERE i.id=? ORDER BY n.grid_y, n.grid_x LIMIT 1", UUID.class, isle);

        Instant day = Instant.parse("2031-06-11T12:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(day));
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', security_posture='WARY', " +
            "trade_policy=base_trade_policy WHERE id=?", Timestamp.from(day.minus(java.time.Duration.ofDays(1))), community);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,20,40,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));

        // Given leave to work their ground — which covers reeds and does not cover the alders.
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, subject_id, payload) VALUES (?,?,'CONTACT_ASK_ACCESS',?,jsonb_build_object('response','ACCESS_GRANTED'))",
            community, Timestamp.from(Instant.parse("2031-06-10T12:00:00Z")), chronicle);

        int before = standing(community, chronicle);
        wildlife.recordDisturbance(theirGround, "CANOPY_LOSS", 25, day.minus(java.time.Duration.ofHours(6)));
        natives.advanceTo(day);
        assertEquals(1, told(community, "TREES_TAKEN"), "a felling on their ground is its own grievance");
        assertEquals(before - 12, standing(community, chronicle), "and it weighs what taking their withies and poles is worth");
        assertEquals(0, told(community, "ENCROACHMENT_NOTICED"), "leave covers the work they gave leave for");

        // Three inside a month, and the isle stops treating it as a grievance.
        for (int felling = 2; felling <= 3; felling++) {
            Instant next = day.plus(java.time.Duration.ofDays(felling));
            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(next));
            jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(next.minus(java.time.Duration.ofDays(1))), community);
            wildlife.recordDisturbance(theirGround, "CANOPY_LOSS", 25, next.minus(java.time.Duration.ofHours(6)));
            natives.advanceTo(next);
        }
        assertEquals(3, told(community, "TREES_TAKEN"));
        assertEquals("HOSTILE", jdbc.queryForObject("SELECT security_posture FROM native_community WHERE id=?", String.class, community),
            "three fellings inside a month make an enemy");
        assertEquals("CLOSED", jdbc.queryForObject("SELECT access_rule FROM native_settlement_site WHERE community_id=? AND holds_stores", String.class, community));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
