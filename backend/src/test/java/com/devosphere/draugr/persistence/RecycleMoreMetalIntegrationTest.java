package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recycling more metal (EPIC #180 heavy industry — completing the recycling loop). V148 melted a worn bronze axe
 * back to an ingot; now a worn bronze spear or pickaxe, and a worn copper axe, can be reclaimed too — so no smelted
 * metal object is ever truly spent.
 *
 * <p>Proven through the real process router: a bronze spear melts to a bronze ingot, and a copper axe melts to a
 * copper ingot. Skips gracefully without Docker.
 */
@SpringBootTest
class RecycleMoreMetalIntegrationTest {

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

    @Test
    void aBronzeSpearAndACopperAxeCanBothBeMeltedBackToIngots() {
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

        // A worn bronze spear melts back to a bronze ingot (broadened from the axe-only recycling of V148).
        items.createCarriedItem(chronicle, "bronze_spear", "Bronze spear", now, "TEST_SEED");
        String[] spear = items.runProcess(chronicle, chunk, "melt down the bronze spear", now);
        assertEquals("SUCCEEDED", spear[0], () -> "melting a bronze spear must succeed: " + spear[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_ingot", 1), "melting a bronze spear must return a bronze ingot");
        assertFalse(items.hasAtLeast(chronicle, "bronze_spear", 1), "the spear is consumed in the melt");

        // And a worn copper axe melts back to a copper ingot (new copper recycling).
        items.createCarriedItem(chronicle, "copper_axe", "Copper axe", now, "TEST_SEED");
        String[] axe = items.runProcess(chronicle, chunk, "melt down the copper axe", now);
        assertEquals("SUCCEEDED", axe[0], () -> "melting a copper axe must succeed: " + axe[1]);
        assertTrue(items.hasAtLeast(chronicle, "copper_ingot", 1), "melting a copper axe must return a copper ingot");
        assertFalse(items.hasAtLeast(chronicle, "copper_axe", 1), "the axe is consumed in the melt");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
