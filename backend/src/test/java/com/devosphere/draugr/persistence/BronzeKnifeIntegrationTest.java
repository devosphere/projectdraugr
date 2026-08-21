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
 * Bronze knife (EPIC #180 / #185 bronze objects). The era's everyday blade: forged from one ingot, it is a proper
 * CUTTING tool whose fine, edge-holding blade parts hide cleaner than knapped stone — lifting the workmanship of
 * that close cutting one grade.
 *
 * <p>Proven: the knife forges through the real process router, and the same cut of the same FINE rawhide comes out
 * SOUND worked with a stone edge but FINE with the bronze knife to hand. Skips gracefully without Docker.
 */
@SpringBootTest
class BronzeKnifeIntegrationTest {

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

    private String newestSoleGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='leather_boot_sole' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void aBronzeKnifeForgesAndCutsTruerThanStone() {
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
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");

        // Baseline: cut boot soles from FINE rawhide with only a stone edge — the workmanship caps it to SOUND.
        items.createCarriedItem(chronicle, "rawhide", "Rawhide", now, "TEST_SEED", QualityGrade.FINE);
        String[] plain = items.executeProcess(chronicle, chunk, "cut_boot_soles", "cut boot soles", now);
        assertEquals("SUCCEEDED", plain[0], () -> "cutting boot soles must succeed: " + plain[1]);
        assertEquals("SOUND", newestSoleGrade(chronicle), "a plain cut of FINE rawhide yields a SOUND sole");

        // Forge a bronze knife from an ingot — routed through the real process matcher.
        items.createCarriedItem(chronicle, "bronze_ingot", "Bronze ingot", now, "TEST_SEED");
        String[] forge = items.runProcess(chronicle, chunk, "forge a bronze knife", now);
        assertEquals("SUCCEEDED", forge[0], () -> "forging a bronze knife must succeed: " + forge[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_knife", 1), "forging must yield a bronze knife");

        // The same cut of the same FINE rawhide, now with the bronze knife to hand, comes out FINE.
        items.createCarriedItem(chronicle, "rawhide", "Rawhide", now, "TEST_SEED", QualityGrade.FINE);
        String[] keen = items.executeProcess(chronicle, chunk, "cut_boot_soles", "cut boot soles", now);
        assertEquals("SUCCEEDED", keen[0], () -> "cutting with the bronze knife must succeed: " + keen[1]);
        assertEquals("FINE", newestSoleGrade(chronicle), "the bronze knife cuts truer than stone, lifting the sole to FINE (#180/#185)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
