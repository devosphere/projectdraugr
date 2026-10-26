package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.construction.ConstructionService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storm damage (#108) — the last of the regressions that acceptance criterion lists by name with no test of its
 * own. {@code ConstructionWeatheringIntegrationTest} proves a field structure wears and finally collapses, but
 * it pins the sky to CLEAR and asserts only that wearing happens at all: the weathering rule's own
 * {@code CASE ww.weather_kind WHEN 'STORM' THEN 3 WHEN 'RAIN' THEN 2 WHEN 'SNOW' THEN 2 ELSE 1 END} was never
 * read for anything but the ELSE.
 *
 * <p>So a storm might as well have been a fair day, and the thing a keeper would most want to know about #108's
 * pens — that a gale takes more off them than a quiet week does, and can bring one down — was asserted nowhere.
 *
 * <p>The sky is set directly rather than waited for: {@code WeatherSimulationService.advanceTo} derives the
 * weather from (season, seed, day) and rewrites it on every action, so a storm cannot be asked for through the
 * action boundary at all. {@code ConstructionService.advanceTo} is called on its own here for the same reason
 * the weathering test calls it — the pass under test is the structural one, and nothing else may run between
 * setting the sky and reading the damage. Skips without Docker.
 */
@SpringBootTest
class WhatAStormTakesOffAPenIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired com.devosphere.draugr.simulation.WeatherSimulationService weather;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private static final int DAYS = 10;

    /** A pen of #108's own, whole, with its structural clock set so the days counted are the days meant. */
    private UUID pen(UUID chunk, int integrity, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Goat fold',?)", id, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
                    "VALUES (?,'GOAT_FOLD','COMPLETED',100,?,?,?)", id, Timestamp.from(at), integrity, Timestamp.from(at));
        return id;
    }

    private int integrity(UUID pen) {
        return jdbc.queryForObject("SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, pen);
    }

    private void sky(UUID world, String kind) {
        jdbc.update("UPDATE world_weather SET weather_kind=? WHERE world_id=?", kind, world);
    }

    @Test
    void aGaleTakesMoreOffAPenThanAQuietWeekAndCanBringOneDown() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
        }
        UUID chunk = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have grassland to stand a fold on");
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant base = Instant.parse("2026-06-01T12:00:00Z");
        weather.advanceTo(base);   // the world must have a weather row, as it does in play

        // ---- A QUIET WEEK AND A HALF. The fold is declared as decaying field work, so it wears at the fair-day
        // rate and nothing more.
        sky(world, "CLEAR");
        UUID fair = pen(chunk, 100, base);
        construction.advanceTo(base.plus(Duration.ofDays(DAYS)));
        int fairLoss = 100 - integrity(fair);
        assertTrue(fairLoss > 0,
            () -> "ten fair days must still tell on a hurdle fold left untended (took " + fairLoss + ")");

        // ---- THE SAME TEN DAYS IN A GALE, on a pen raised after the fair pass so it is those days it weathers.
        Instant stormFrom = base.plus(Duration.ofDays(DAYS));
        sky(world, "STORM");
        UUID battered = pen(chunk, 100, stormFrom);
        construction.advanceTo(stormFrom.plus(Duration.ofDays(DAYS)));
        int stormLoss = 100 - integrity(battered);
        assertTrue(stormLoss > fairLoss,
            () -> "a gale must take more off a pen than fair weather does (" + fairLoss + " fair against " + stormLoss + " storm)");

        // ---- AND IT CAN BRING ONE DOWN, with history rather than a silent disappearance. A fold already coming
        // apart does not survive a gale it would have survived in fair weather.
        Instant lastFrom = stormFrom.plus(Duration.ofDays(DAYS));
        UUID doomed = pen(chunk, ConstructionService.COMING_APART_BELOW - 1, lastFrom);
        sky(world, "STORM");
        construction.advanceTo(lastFrom.plus(Duration.ofDays(60)));
        assertEquals("DESTROYED", jdbc.queryForObject(
            "SELECT state FROM construction_project WHERE object_id=?", String.class, doomed),
            "a pen left coming apart through storms must finally collapse");
        assertEquals("DESTROYED", jdbc.queryForObject(
            "SELECT lifecycle_state FROM world_object WHERE id=?", String.class, doomed),
            "and the collapsed pen's object is retired rather than deleted");
        assertTrue(jdbc.queryForObject(
            "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='CONSTRUCTION_COLLAPSED'",
            Integer.class, doomed) >= 1, "the collapse is kept in history, with its cause");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** A roofed house of #108's own, for the other of the two weathering passes. */
    private UUID byre(UUID chunk, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Cattle byre',?)", id, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
                    "VALUES (?,'CATTLE_BYRE','COMPLETED',100,?,100,?)", id, Timestamp.from(at), Timestamp.from(at));
        return id;
    }

    /**
     * The other half of the same rule, and the other kind of husbandry building. A hurdle fold is field work
     * and wears a little every day; a roofed byre is a far more substantial thing and was given its own pass
     * (V-era #77/#220) that wears it <b>only to weather</b> — nothing on a fair day, something in the wet, more
     * in a gale. That ELSE 0 means a byre in a dry summer is genuinely maintenance-free, and nothing asserted
     * either end of it.
     */
    @Test
    void aRoofedByreCostsNothingInFairWeatherAndWearsInAGale() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
        }
        UUID chunk = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have grassland to stand a byre on");
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant base = Instant.parse("2026-07-01T12:00:00Z");
        weather.advanceTo(base);

        sky(world, "CLEAR");
        UUID dry = byre(chunk, base);
        construction.advanceTo(base.plus(Duration.ofDays(DAYS)));
        assertEquals(100, integrity(dry),
            "ten dry days must cost a roofed byre nothing — a shelter wears to weather, not to time");

        Instant stormFrom = base.plus(Duration.ofDays(DAYS));
        sky(world, "STORM");
        UUID battered = byre(chunk, stormFrom);
        construction.advanceTo(stormFrom.plus(Duration.ofDays(DAYS)));
        int stormLoss = 100 - integrity(battered);
        assertTrue(stormLoss > 0,
            () -> "ten days of gale must tell on a byre, or its upkeep is unreachable (took " + stormLoss + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
