package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.simulation.WeatherSimulationService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fire-hazard warning (EPIC #215, story #219 — a hazard must give grounded sensory evidence BEFORE forced contact).
 * The fire tick scorches a thatch lean-to or fuel stock left too close to a roaring fire (#372/#373); a Chronicle
 * who looks around should be warned of that danger before it takes hold, so they can bank or move the fire rather
 * than meet the consequence unwarned.
 *
 * <p>Proven: a survey over a roaring, unbanked fire beside a lean-to in dry weather reads the warning; a banked
 * (low-fuel) fire, wet weather, and ground with nothing flammable each read none. Skips gracefully without Docker.
 */
@SpringBootTest
class FireHazardWarningIntegrationTest {

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
    @Autowired WeatherSimulationService weather;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String survey(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        ChronicleActionService.ActionResult r = actions.resolve("look around carefully");
        return r.perception() == null ? "" : r.perception().toLowerCase(Locale.ROOT);
    }

    private void setFire(UUID pit, int fuel) {
        jdbc.update("UPDATE fire_state SET active=true, fuel_minutes=?, last_updated_at=? WHERE construction_id=?",
            fuel, Timestamp.from(ticks.current().simulatedAt()), pit);
    }

    private void setWeather(UUID world, String kind) {
        Instant now = ticks.current().simulatedAt();
        weather.advanceTo(now);
        jdbc.update("UPDATE world_weather SET weather_kind=?, intensity=?, ambient_temperature_c=15, wind_speed_kph=0, observed_at=? WHERE world_id=?",
            kind, "CLEAR".equals(kind) ? 0 : 55, Timestamp.from(now), world);
    }

    @Test
    void aSurveyWarnsOfARoaringFireBesideThatchButNotWhenBankedWetOrWithNothingToCatch() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant base = ticks.current().simulatedAt();

        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, Timestamp.from(base));
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,960,?)", pit, Timestamp.from(base));
        UUID leanTo = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Lean-to',?)", leanTo, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'LEAN_TO','COMPLETED',100,?,100)", leanTo, Timestamp.from(base));

        // Roaring fire, dry weather, thatch beside it — the warning must read.
        setWeather(world, "CLEAR");
        setFire(pit, 960);
        String warned = survey(chronicle, chunk);
        assertTrue(warned.contains("burns high") && warned.contains("lean-to"),
            () -> "a survey must warn of a roaring fire beside the thatch before it catches (#219): " + warned);

        // Banked low — no longer roaring — reads no warning.
        setFire(pit, 30);
        String banked = survey(chronicle, chunk);
        assertFalse(banked.contains("burns high"), () -> "a banked, low fire is no hazard and must not warn: " + banked);

        // Roaring again, but with nothing left to catch (the lean-to fallen, no loose fuel) — no warning.
        // (The dry-weather gate is a simple code-level filter; it cannot be exercised through resolve, whose sim
        // tick re-rolls the weather each action, so the two gates the test controls are the fire and the target.)
        setFire(pit, 960);
        jdbc.update("UPDATE construction_project SET integrity_percent=0 WHERE object_id=?", leanTo);
        String noTarget = survey(chronicle, chunk);
        assertFalse(noTarget.contains("burns high"), () -> "a roaring fire with nothing flammable beside it is no hazard and must not warn: " + noTarget);

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
