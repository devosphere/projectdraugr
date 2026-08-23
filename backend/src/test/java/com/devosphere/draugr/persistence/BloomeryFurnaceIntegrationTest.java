package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bloomery furnace (EPIC #180 heavy industry — smelting infrastructure). The metal smelts drive their heat from an
 * open fire; a bloomery — a clay-and-stone shaft that holds and concentrates the heat — smelts finer. The furnace is
 * marked as the workstation (station_kind) of the smelts, so working the ore at a furnace lifts the metal's grade
 * the way a bench eases bench work — never gating it (no furnace still smelts over the fire, just less finely).
 *
 * <p>Proven: a bloomery raises through the real router, and the same FINE ore and charcoal smelt to a SOUND copper
 * ingot over a bare fire but a FINE ingot at a furnace. Skips gracefully without Docker.
 */
@SpringBootTest
class BloomeryFurnaceIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String newestIngotGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='copper_ingot' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void aBloomeryFurnaceSmeltsFinerMetalThanABareFire() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);

        // Baseline: smelt FINE ore and FINE charcoal over a bare fire — no furnace, so the workmanship caps the
        // ingot to SOUND even though the stock is FINE.
        items.createCarriedItem(chronicle, "copper_ore", "Copper ore", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "copper_ore", "Copper ore", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED", QualityGrade.FINE);
        String[] bare = items.executeProcess(chronicle, chunk, "smelt_copper", "smelt the copper ore", now);
        assertEquals("SUCCEEDED", bare[0], () -> "smelting over a bare fire must succeed: " + bare[1]);
        assertEquals("SOUND", newestIngotGrade(chronicle), "a bare-fire smelt of FINE stock yields a SOUND ingot");

        // Raise a bloomery furnace from clay and stone — through the real router.
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_SEED");
        for (int i = 0; i < 8; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        String[] furnace = items.runProcess(chronicle, chunk, "make a bloomery furnace", now);
        assertEquals("SUCCEEDED", furnace[0], () -> "raising a bloomery furnace must succeed: " + furnace[1]);
        assertTrue(items.hasAtLeast(chronicle, "bloomery_furnace", 1), "raising must yield a bloomery furnace");

        // The same FINE ore and charcoal, now smelted at the furnace: the metal comes out FINE.
        items.createCarriedItem(chronicle, "copper_ore", "Copper ore", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "copper_ore", "Copper ore", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED", QualityGrade.FINE);
        String[] atFurnace = items.executeProcess(chronicle, chunk, "smelt_copper", "smelt the copper ore", now);
        assertEquals("SUCCEEDED", atFurnace[0], () -> "smelting at the furnace must succeed: " + atFurnace[1]);
        assertEquals("FINE", newestIngotGrade(chronicle), "a furnace lifts the same FINE stock to a FINE ingot (#180)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
