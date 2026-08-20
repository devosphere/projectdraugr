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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Refuse pest-draw regression (EPIC #215, story #218 — waste draws pests). Refuse now breeds illness (V140); this
 * adds its wildlife-facing consequence: a camp choked with refuse carries the scent of rot on the wind and draws
 * hungry animals in the way a fresh kill does, so a hunting predator lands more ambushes on filthy ground than on
 * clean ground. Keeping the camp clean — a latrine drains the refuse — takes the draw away with the filth.
 *
 * <p>Proven deterministically: with a hunting predator present, a refuse-choked chunk lands strictly more ambushes
 * over a fixed set of encounters than the same chunk clean. Skips gracefully without Docker.
 */
@SpringBootTest
class RefusePestDrawIntegrationTest {

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

    /** Landed ambushes over a fixed id set, resetting the hunter to HUNTING and the body to unhurt before each roll. */
    private int ambushes(UUID chronicle, UUID chunk, UUID hunter, Instant now, java.util.List<UUID> ids) {
        int hits = 0;
        for (UUID a : ids) {
            jdbc.update("UPDATE wildlife_population SET behavior_state='HUNTING' WHERE id=?", hunter);
            jdbc.update("UPDATE chronicle_physiology SET injury_severity=0, blood_loss_ml=0, pain_level=0 WHERE chronicle_id=?", chronicle);
            if (wildlife.passiveEncounter(chronicle, chunk, a, now, "LOW") != null) hits++;
        }
        return hits;
    }

    @Test
    void aRefuseChokedCampDrawsTheHuntMoreThanACleanOne() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Wolf hunting ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Wolf hunting ground',300)", site, world, chunk);
        UUID hunter = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'gray_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", hunter, site, ts);

        java.util.Random rnd = new java.util.Random(11);
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 220; i++) ids.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // Clean ground: the baseline draw of the hunting predator.
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        int clean = ambushes(chronicle, chunk, hunter, now, ids);
        assertTrue(clean > 0, "the hunting predator must land some ambushes even on clean ground (else the test proves nothing)");

        // The same ground choked with refuse: the scent of rot draws the hunt in harder.
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,90,?) ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=90, last_updated_at=?", chunk, ts, ts);
        int foul = ambushes(chronicle, chunk, hunter, now, ids);

        assertTrue(foul > clean, () -> "a refuse-choked camp must draw the hunt more than a clean one (foul=" + foul + ", clean=" + clean + ") (#218)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
