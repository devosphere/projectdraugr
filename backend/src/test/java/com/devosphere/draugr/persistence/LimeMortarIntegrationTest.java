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
 * The lime chain (EPIC #180 / #182 fuel chains → #183 masonry). Mortar was a stopgap of wood ash and clay; real
 * mortar is lime. This proves the deferred lime chain end to end: limestone is calcined in a charcoal fire into
 * quicklime, the quicklime is slaked with water into a setting lime mortar, and that lime mortar beds a mortared
 * course of a wall — standing in for the ash mortar through the new either/or input group.
 *
 * <p>All three steps route through the real router; the burn leans on the charcoal the clamp (V163) provides.
 * Skips gracefully without Docker.
 */
@SpringBootTest
class LimeMortarIntegrationTest {

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

    private int count(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void limestoneBurnsToQuicklimeSlakesToMortarAndBedsAWall() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        // A wetland chunk: it satisfies the slaking step's need for water, and a fire pit gives the calcining heat.
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have wetland water to slake lime");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,960,?)", pit, ts);

        // Step one: calcine limestone into quicklime, over the fire, burning charcoal.
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "limestone_chunk", "Limestone", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        String[] calcine = items.runProcess(chronicle, chunk, "calcine the limestone", now);
        assertEquals("SUCCEEDED", calcine[0], () -> "calcining limestone must succeed through the router: " + calcine[1]);
        assertTrue(items.hasAtLeast(chronicle, "quicklime", 1), "calcining must yield quicklime");

        // Step two: slake the quicklime with water and clay into lime mortar.
        items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_SEED");
        String[] slake = items.runProcess(chronicle, chunk, "mix lime mortar", now);
        assertEquals("SUCCEEDED", slake[0], () -> "slaking lime into mortar must succeed through the router: " + slake[1]);
        assertTrue(items.hasAtLeast(chronicle, "lime_mortar", 1), "slaking must yield lime mortar");

        // Step three: bed a mortared course with the lime mortar alone — no ash mortar present, so this proves lime
        // mortar is now an accepted mortar for the wall.
        assertEquals(0, count(chronicle, "mortar_mix"), "no ash mortar present — the course must be laid with lime mortar");
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "construction_stone", "Construction stone", now, "TEST_SEED");
        String[] course = items.runProcess(chronicle, chunk, "lay a mortared course of stone", now);
        assertEquals("SUCCEEDED", course[0], () -> "lime mortar must bed a mortared course: " + course[1]);
        assertEquals(0, count(chronicle, "lime_mortar"), "laying the course must consume the lime mortar");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
