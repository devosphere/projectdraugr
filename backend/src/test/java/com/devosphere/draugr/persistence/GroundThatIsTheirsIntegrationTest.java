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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground that is theirs (#211).
 *
 * <p>Work in a people's territory is noticed, day by day, from the disturbance it leaves; three notices bring a
 * demand for compensation and trust falls far enough for a boycott; restitution lifts the demand; leave, once earned,
 * makes the same work no offence; and refuse on the water by the isle is its own grievance. The work is written to
 * the disturbance ledger directly so its size is exact; everything the Chronicle does about it goes through the
 * public action boundary. Skips without Docker.
 */
@SpringBootTest
class GroundThatIsTheirsIntegrationTest {

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

    /** A morning's work on their fishing water: disturbance the ledger records, of a known size. */
    private void workedOn(UUID chunk, String iso) {
        jdbc.update("INSERT INTO chunk_disturbance_event (id, chunk_id, source_kind, amount, occurred_at) VALUES (?,?,'FISH',6,?)",
            UUID.randomUUID(), chunk, Timestamp.from(Instant.parse(iso)));
    }

    private int count(UUID community, String kind) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind=?", Integer.class, community, kind);
    }

    @Test
    void workOnTheirGroundIsNoticedAnsweredAndMadeRight() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        at("2031-06-01T12:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        UUID water = jdbc.queryForObject(
            "SELECT n.id FROM world_chunk i JOIN world_chunk n ON n.world_id=i.world_id AND greatest(abs(n.grid_x-i.grid_x), abs(n.grid_y-i.grid_y))=1 " +
            "WHERE i.id=? ORDER BY n.grid_y, n.grid_x LIMIT 1", UUID.class, isle);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-01T00:00:00Z")), community);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,12,40,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-05-20T12:00:00Z")));

        // Three days of work on their water, unasked: noticed each day, then a demand, and trust too low to trade.
        workedOn(water, "2031-06-01T09:00:00Z");
        workedOn(water, "2031-06-02T09:00:00Z");
        workedOn(water, "2031-06-03T09:00:00Z");
        natives.advanceTo(Instant.parse("2031-06-04T00:00:00Z"));
        assertEquals(3, count(community, "ENCROACHMENT_NOTICED"), "each day's work on their ground is noticed");
        assertEquals(1, count(community, "COMPENSATION_DEMANDED"), "three notices bring a demand");
        assertNotNull(jdbc.queryForObject("SELECT obligation FROM community_relation WHERE community_id=? AND chronicle_id=?", String.class, community, chronicle),
            "the demand is kept as what is owed");
        assertEquals(1, count(community, "BOYCOTT"), "and trust falls too low to trade");

        // Restitution pays what is owed, and the demand is lifted.
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        at("2031-06-04T12:00:00Z");
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", Instant.now(), "TEST_FIXTURE");
        actions.resolve("make restitution");
        assertNull(jdbc.queryForObject("SELECT obligation FROM community_relation WHERE community_id=? AND chronicle_id=?", String.class, community, chronicle),
            "compensation paid lifts the demand");

        // Leave, once earned, makes the same work no offence.
        jdbc.update("UPDATE community_relation SET standing=20 WHERE community_id=? AND chronicle_id=?", community, chronicle);
        var asked = actions.resolve("ask leave to fish their waters");
        assertEquals("SUCCEEDED", asked.outcome(), asked::perception);
        int noticed = count(community, "ENCROACHMENT_NOTICED");
        workedOn(water, "2031-06-05T09:00:00Z");
        natives.advanceTo(Instant.parse("2031-06-06T00:00:00Z"));
        assertEquals(noticed, count(community, "ENCROACHMENT_NOTICED"), "work done under leave is no offence");

        // Refuse left on the water by the isle is its own grievance.
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,80,now()) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=80", isle);
        natives.advanceTo(Instant.parse("2031-06-07T00:00:00Z"));
        assertEquals(1, count(community, "WATER_FOULED"), "fouling their water is held against whoever did it");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
