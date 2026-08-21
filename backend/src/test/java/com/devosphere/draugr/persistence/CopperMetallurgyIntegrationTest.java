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
 * Copper metallurgy vertical (EPIC #180 heavy industry / #184 copper extraction). The first complete ore → metal →
 * tool chain: copper_ore (mineable), smelt_copper (ore + charcoal over a fire → copper_ingot), forge_copper_chisel
 * (ingot hammered and annealed → copper_chisel), and the chisel's terminal payoff — a finer, controlled edge that
 * lifts the workmanship of a carve one grade over stone.
 *
 * <p>Proven end to end through the real process router and the crafting core: ore smelts to an ingot, the ingot
 * forges to a chisel, and the same carve of the same FINE stock comes out SOUND worked with a stone edge but FINE
 * with the copper chisel to hand. Skips gracefully without Docker.
 */
@SpringBootTest
class CopperMetallurgyIntegrationTest {

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

    private String newestLadleGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='water_ladle' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void copperOreSmeltsForgesAndTheChiselWorksTruerThanStone() {
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

        // An active fire to smelt and forge over, and the tools the work turns on: a knife to carve, a hammer to forge.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");

        // Baseline: a plain carve of a FINE branch with only a stone edge — the workmanship caps it to SOUND.
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED", QualityGrade.FINE);
        String[] plain = items.executeProcess(chronicle, chunk, "carve_water_ladle", "carve a water ladle", now);
        assertEquals("SUCCEEDED", plain[0], () -> "carving a ladle must succeed: " + plain[1]);
        assertEquals("SOUND", newestLadleGrade(chronicle), "a plain carve of a FINE branch yields a SOUND ladle");

        // Smelt copper: ore + charcoal reduced over the fire into an ingot — routed through the real process matcher.
        items.createCarriedItem(chronicle, "copper_ore", "Copper ore", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "copper_ore", "Copper ore", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        String[] smelt = items.runProcess(chronicle, chunk, "smelt the copper ore", now);
        assertEquals("SUCCEEDED", smelt[0], () -> "smelting copper ore must succeed: " + smelt[1]);
        assertTrue(items.hasAtLeast(chronicle, "copper_ingot", 1), "smelting must yield a copper ingot");

        // Forge the ingot into a chisel — needs the fire and a striking tool, routed through the real matcher.
        String[] forge = items.runProcess(chronicle, chunk, "forge a copper chisel", now);
        assertEquals("SUCCEEDED", forge[0], () -> "forging a copper chisel must succeed: " + forge[1]);
        assertTrue(items.hasAtLeast(chronicle, "copper_chisel", 1), "forging must yield a copper chisel");

        // Terminal payoff: the same carve of the same FINE branch, now with the copper chisel to hand, comes out FINE.
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED", QualityGrade.FINE);
        String[] copper = items.executeProcess(chronicle, chunk, "carve_water_ladle", "carve a water ladle", now);
        assertEquals("SUCCEEDED", copper[0], () -> "carving with the copper chisel must succeed: " + copper[1]);
        assertEquals("FINE", newestLadleGrade(chronicle), "the copper chisel works truer than stone, lifting the carve to FINE (#180/#184)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
