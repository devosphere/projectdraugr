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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lime kiln (EPIC #180 / #182 fuel / #183 kiln infrastructure). Calcining over an open charge works but is
 * wasteful; a built stone-and-clay kiln holds the heat close around the stone for a fuller yield. It is the lime
 * counterpart to the bloomery furnace — a workstation that only eases the burn, never gates it.
 *
 * <p>Proven through the real router: a lime kiln is raised from clay and stone, and limestone calcines at it into
 * quicklime (the kiln standing as calcining's workstation). Skips gracefully without Docker.
 */
@SpringBootTest
class LimeKilnIntegrationTest {

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
    void aLimeKilnIsRaisedAndCalcinesLimestoneAtIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // Raise a lime kiln from clay and stone — through the real router.
        for (int i = 0; i < 5; i++) items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_SEED");
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        String[] build = items.runProcess(chronicle, chunk, "make a lime kiln", now);
        assertEquals("SUCCEEDED", build[0], () -> "raising a lime kiln must succeed through the router: " + build[1]);
        assertTrue(items.hasAtLeast(chronicle, "lime_kiln", 1), "raising must yield a lime kiln (the calcining workstation)");

        // Calcine limestone at the kiln — a hot fire, the stone, and the charcoal charge.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,480,?)", pit, ts);
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "limestone_chunk", "Limestone", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");

        String[] calcine = items.runProcess(chronicle, chunk, "calcine the limestone", now);
        assertEquals("SUCCEEDED", calcine[0], () -> "calcining at the kiln must succeed: " + calcine[1]);
        assertTrue(items.hasAtLeast(chronicle, "quicklime", 1), "calcining must yield quicklime");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
