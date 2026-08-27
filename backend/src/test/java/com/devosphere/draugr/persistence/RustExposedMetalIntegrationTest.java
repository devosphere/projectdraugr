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
 * Rust (EPIC #215 / story #220 — maintenance, neglect, decay ... rust). Use-wear (V139) degrades a tool only as it is
 * worked; corrosion is the other half — iron and steel left on wet ground rust by exposure alone, whether or not they
 * are used. Only unowned ground stock at a wet biome corrodes; metal carried on the body is kept and maintained, and
 * stone/bone/bronze/copper do not rust.
 *
 * <p>Proven through the real world tick: an iron axe abandoned on wetland corrodes down the SOUND→WORN condition
 * ladder (logged RUSTED), while the same axe carried on the body and a stone axe beside it in the mud stay sound.
 * Skips gracefully without Docker.
 */
@SpringBootTest
class RustExposedMetalIntegrationTest {

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

    private String condition(UUID obj) {
        return jdbc.queryForObject("SELECT condition_state FROM item_instance WHERE object_id=?", String.class, obj);
    }

    private int rustedEvents(UUID obj) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='RUSTED'", Integer.class, obj);
        return n == null ? 0 : n;
    }

    private UUID groundItem(UUID chunk, String key, String name, Instant weatheredAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ITEM',?,?)", id, name, chunk);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,use_count,weathered_at) VALUES (?,?,'SOUND',0,?)",
            id, key, weatheredAt == null ? null : Timestamp.from(weatheredAt));
        return id;
    }

    private UUID carriedItem(UUID chronicle, String key, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, name, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,use_count) VALUES (?,?,'SOUND',0)", id, key);
        return id;
    }

    @Test
    void ironLeftOnWetGroundRustsButCarriedMetalAndStoneDoNot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID wetland = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(wetland, "the world must have wetland ground to rust metal on");
        Instant base = ticks.current().simulatedAt();

        // An iron axe abandoned in the bog, its corrosion clock started a wet fortnight-and-more ago; a second iron
        // axe carried on the body; and a stone axe lying in the same mud — the two controls.
        UUID abandoned = groundItem(wetland, "iron_axe", "Iron axe", base.minus(Duration.ofDays(20)));
        UUID carried = carriedItem(chronicle, "iron_axe", "Iron axe");
        UUID stone = groundItem(wetland, "stone_axe", "Stone axe", base.minus(Duration.ofDays(20)));

        // The world turns one hour — the weathering pass accounts the elapsed exposure.
        ticks.advanceBy(Duration.ofHours(1));

        assertTrue(!"SOUND".equals(condition(abandoned)), () -> "iron left on wet ground must rust from sound, now " + condition(abandoned));
        assertEquals("WORN", condition(abandoned), "twenty days in the wet corrodes an iron axe to WORN (not yet broken)");
        assertTrue(rustedEvents(abandoned) >= 1, "the corrosion must be kept as RUSTED evidence (#208/#220)");
        assertEquals("SOUND", condition(carried), "metal carried on the body is kept dry and maintained — it does not rust");
        assertEquals("SOUND", condition(stone), "stone does not rust");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
