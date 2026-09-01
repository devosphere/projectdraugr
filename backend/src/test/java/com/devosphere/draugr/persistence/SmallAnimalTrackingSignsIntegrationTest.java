package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #152 - small-animal tracking signs. Proves the small-fauna roster leaves trackable sign: a gopher at the
 * ground is found by tracking and, read with a practised eye, named; and every one of the six named #152 signs
 * resolves to a plausible species with the right sign kind. Skips without Docker.
 */
@SpringBootTest
class SmallAnimalTrackingSignsIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aGopherLeavesTrackableSign() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = Instant.now();

        // Isolate the ground to one small animal so the sign read is unambiguously its own.
        jdbc.update("DELETE FROM wildlife_population WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Ground',100)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'gopher','HERBIVORE','DIURNAL',1,3,'FORAGING',?)", UUID.randomUUID(), site, Timestamp.from(now));

        // Read with a practised eye names the animal from its sign.
        WildlifeEncounterService.EncounterResult read = wildlife.track(chronicle, chunk, UUID.randomUUID(), now, "HIGH", 1.0);
        assertEquals("SUCCEEDED", read.outcome(), () -> "tracking a gopher must find sign: " + read.narration());
        assertTrue(read.narration().toLowerCase(Locale.ROOT).contains("gopher"),
            () -> "a practised read must name the gopher: " + read.narration());

        // Tracking is reachable through the real dispatch: "search the ground for tracks" routes to the TRACK
        // handler, whose narration is distinctive whether or not a sign is present after the intervening tick.
        var tracked = actions.resolve("carefully search the ground for tracks");
        String track = tracked.perception().toLowerCase(Locale.ROOT);
        assertTrue(track.contains("you find") || track.contains("holds nothing that anything has left"),
            () -> "the phrase must route to the TRACK handler: " + tracked.perception());

        // Every named #152 sign resolves to a plausible species with the right kind.
        assertEquals(6, (int) jdbc.queryForObject(
            "SELECT (SELECT count(*) FROM wildlife_sign WHERE species_key='hare' AND sign_kind='PRINTS') " +
            "+ (SELECT count(*) FROM wildlife_sign WHERE species_key='red_squirrel' AND sign_kind='FEEDING_SIGN') " +
            "+ (SELECT count(*) FROM wildlife_sign WHERE species_key='gopher' AND sign_kind='BURROW') " +
            "+ (SELECT count(*) FROM wildlife_sign WHERE species_key='forest_rat' AND sign_kind='SCAT') " +
            "+ (SELECT count(*) FROM wildlife_sign WHERE species_key='sparrow' AND sign_kind='FEATHERS') " +
            "+ (SELECT count(*) FROM wildlife_sign WHERE species_key='bank_vole' AND sign_kind='GRASS_RUNWAY')", Integer.class),
            "all six named tracking signs must be catalogued");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
