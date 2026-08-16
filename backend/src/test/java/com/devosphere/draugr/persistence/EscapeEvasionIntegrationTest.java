package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.simulation.SimulationTickService;
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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Escape mechanic regression (M1 #126, EPIC #123). Breaking contact with a hunting predator — a run for
 * distance, or going to ground to break its line — used to be pure narration: passiveEncounter ran on the
 * disengage and rolled the same ambush chance as any other exposed act, so flee/hide changed nothing.
 *
 * <p>This proves the escape is now mechanical: for one and the same threat and the same deterministic roll,
 * going to ground averts an ambush that lands when the Chronicle does not break contact. It is a reduction,
 * not immunity — the roll still stands — but a deliberate disengage is now a real choice.
 *
 * <p>Skips gracefully where no Docker engine is reachable; runs for real in CI.
 */
@SpringBootTest
class EscapeEvasionIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void goingToGroundAvertsAnAmbushThatLandsWithoutBreakingContact() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        // A predator actively hunting this ground — the worst case a disengage is meant to answer.
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dusk prowler territory',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Dusk prowler territory',400)", site, worldId, chunk);
        UUID pack = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", pack, site, ts);

        // A deterministic action id whose roll lands inside the base ambush band (< the HUNTING chance of
        // 25, or 35 for an ambush hunter) but at/above the concealed band (>= 0..5 after the -30 for going
        // to ground) — so the ONLY difference between an ambush and a clean break is breaking contact.
        UUID actionId;
        int roll;
        do { actionId = UUID.randomUUID(); roll = Math.floorMod(actionId.hashCode() >>> 8, 100); } while (roll < 8 || roll > 20);

        // Going to ground: the hunt does not close.
        String concealedOutcome = wildlife.passiveEncounter(chronicle, chunk, actionId, now, "LOW", true, true);
        assertNull(concealedOutcome, "going to ground must break a hunting predator's close for this encounter (#126 escape)");

        // Same threat, same roll, no break from contact: the ambush lands — proving the danger was real,
        // and that it was the disengage, not the absence of a threat, that averted it above.
        String exposedOutcome = wildlife.passiveEncounter(chronicle, chunk, actionId, now, "LOW", false, false);
        assertNotNull(exposedOutcome, "with no break from contact the hunting predator reaches the Chronicle");

        PersistentStateAuditor.AuditReport report = auditor.inspect();
        assertTrue(report.consistent(), () -> "the world must stay Auditor-consistent after the encounter: " + report.violations());
    }
}
