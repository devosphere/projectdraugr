package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rot (EPIC #215 / story #220 — decay, ... rot). A fresh hide, pelt, or length of sinew is raw meat still: dropped in
 * a bog or left out in the wet, it putrefies and is lost — the reason a hide must be fleshed, dried, or salted before
 * it rots. Only unowned ground stock at a wet biome rots; carried stock is kept and worked, a salted hide is
 * preserved, and stone does not rot at all. Unlike metal (which weakens by degrees), raw tissue rots away entirely.
 *
 * <p>Proven through the real world tick: a raw hide abandoned on wetland past its rotting time is destroyed (ROTTED),
 * while a pelt carried on the body, a salted hide, and a field stone in the same mud all remain. Skips without Docker.
 */
@SpringBootTest
class RotExposedHideIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String lifecycle(UUID obj) {
        return jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, obj);
    }

    private UUID groundItem(UUID chunk, String key, Instant weatheredAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ITEM',?,?)", id, key, chunk);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,use_count,weathered_at) VALUES (?,?,'SOUND',0,?)",
            id, key, weatheredAt == null ? null : Timestamp.from(weatheredAt));
        return id;
    }

    private UUID carriedItem(UUID chronicle, String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, key, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,use_count) VALUES (?,?,'SOUND',0)", id, key);
        return id;
    }

    @Test
    void aRawHideLeftInTheWetRotsAwayButCarriedSaltedAndStoneEndure() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID wetland = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(wetland, "the world must have wetland ground for a hide to rot on");
        Instant base = ticks.current().simulatedAt();

        // A raw hide dropped in the bog five days ago; a pelt carried on the body; a salted hide and a field stone
        // lying in the same mud — the three controls.
        UUID rawHide = groundItem(wetland, "animal_hide", base.minus(Duration.ofDays(5)));
        UUID carriedPelt = carriedItem(chronicle, "wolf_pelt");
        UUID saltedHide = groundItem(wetland, "salted_hide", base.minus(Duration.ofDays(5)));
        UUID stone = groundItem(wetland, "field_stone", base.minus(Duration.ofDays(5)));

        // The world turns one hour — the rot pass accounts the elapsed exposure.
        ticks.advanceBy(Duration.ofHours(1));

        assertEquals("DESTROYED", lifecycle(rawHide), "a raw hide left past its rotting time in the wet must be lost");
        Integer rotted = jdbc.queryForObject(
            "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='ROTTED'", Integer.class, rawHide);
        assertTrue(rotted != null && rotted >= 1, "the rot must be kept as ROTTED evidence (#208/#220)");
        String cause = jdbc.queryForObject("SELECT destroyed_cause FROM world_object WHERE id=?", String.class, rawHide);
        assertEquals("ROTTED", cause, "the hide must record how it was lost");

        assertEquals("ACTIVE", lifecycle(carriedPelt), "a pelt carried on the body is kept and does not rot");
        assertEquals("ACTIVE", lifecycle(saltedHide), "a salted hide is preserved and does not rot");
        assertEquals("ACTIVE", lifecycle(stone), "stone does not rot");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
