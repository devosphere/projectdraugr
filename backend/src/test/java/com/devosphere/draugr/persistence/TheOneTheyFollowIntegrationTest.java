package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one the group follows (#121).
 *
 * <p>A social group that hunts or holds ground does it behind somebody, and the animal that comes at a Chronicle is
 * that one. Killing it scatters the group: for ten days it is in none of the states the raid and ambush rules read,
 * so it takes no stock and lies in wait for nobody. Then another comes to the front of it — nothing is spawned to
 * replace anyone. Skips without Docker.
 */
@SpringBootTest
class TheOneTheyFollowIntegrationTest {

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
    @Autowired WildlifeEncounterService encounters;
    @Autowired WildlifeSimulationService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** A pack of four on this ground, hunting: a group with somebody at the front of it. */
    private UUID pack(UUID chunk, String species, String behaviour, int count) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Hunting ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Hunting ground',40)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,?,'CARNIVORE','DIURNAL',?,8,?,?)", pop, site, species, count, behaviour, ts);
        return pop;
    }

    /** Move the world clock, and the body with it, so the scheduled tick and the test agree on when it is. */
    private void at(Instant when) {
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(when));
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", Timestamp.from(when));
    }

    private String behaviour(UUID pop) {
        return jdbc.queryForObject("SELECT behavior_state FROM wildlife_population WHERE id=?", String.class, pop);
    }

    @Test
    void killingTheOneAtTheFrontScattersTheGroupUntilAnotherComesToIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2031-06-10T12:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(now));
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", Timestamp.from(now));
        // Nothing else alive on this ground, so the confrontation is with the pack and not with a passing hare.
        jdbc.update("UPDATE wildlife_population wp SET population_count=0 FROM ecology_site es WHERE es.id=wp.site_id AND es.chunk_id=?", chunk);

        UUID wolves = pack(chunk, "gray_wolf", "PACK_HUNT", 4);
        // Armed and rested, so the kill is decided by the animal and not by the Chronicle's condition.
        jdbc.update("UPDATE chronicle_physiology SET energy_level=100, injury_severity=0, pain_level=0 WHERE chronicle_id=?", chronicle);
        var killed = encounters.confront(chronicle, chunk, UUID.randomUUID(), now, 500);
        assertEquals("SUCCEEDED", killed.outcome(), killed::narration);
        assertEquals(3, (int) jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, wolves),
            "one animal is dead, and only one");
        assertEquals("SCATTERED", behaviour(wolves), "what held them in one shape is lying at the Chronicle's feet");
        assertNotNull(jdbc.queryForObject("SELECT leader_lost_at FROM wildlife_population WHERE id=?", Timestamp.class, wolves));
        assertTrue(killed.narration().contains("not together"), killed::narration);

        // The world's own day does not hand the hunt straight back. The clock moves with it, because the scheduled
        // tick runs on the world clock and would otherwise keep re-imposing the scatter from its own idea of "now".
        at(now.plus(Duration.ofDays(1)));
        wildlife.advanceTo(now.plus(Duration.ofDays(1)));
        assertEquals("SCATTERED", behaviour(wolves), "a scattered pack is still scattered tomorrow");

        // And while it is scattered it is in none of the states a raid reads, so the stock keeps.
        assertNull(encounters.raidUnprotectedStock(chronicle, now.plus(Duration.ofDays(1)), true),
            "a leaderless pack takes nothing: there is nothing leading it to the pen");

        // Ten days on, another comes to the front of it — and nobody was spawned to be that animal.
        at(now.plus(Duration.ofDays(WildlifeSimulationService.LEADERLESS_DAYS + 1)));
        wildlife.advanceTo(now.plus(Duration.ofDays(WildlifeSimulationService.LEADERLESS_DAYS + 1)));
        assertNull(jdbc.queryForObject("SELECT leader_lost_at FROM wildlife_population WHERE id=?", Timestamp.class, wolves),
            "the mark clears when the group is a group again");
        assertTrue(!"SCATTERED".equals(behaviour(wolves)), "and the pack goes back to what its hour and weather call for");
        assertTrue(jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, wolves) >= 3,
            "nothing was created to replace the one that was killed");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
