package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.FireService;
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
 * The steel striker — unlocking the iron-age fire (EPIC #180 heavy industry / #187 steel; #49 fire). V49 catalogued
 * flint-and-steel as the reliable iron-age way to make fire but its striker had no way to be made (V51: "no smelting
 * exists yet, so flint_and_steel stays unreachable until it does"). Smelting now exists, so a striker is case-hardened
 * from a bloom exactly as a steel axe is, and making it turns the whole method live.
 *
 * <p>Proven end-to-end: before, flint-and-steel is missing its striker; a steel striker case-hardened through the real
 * router closes that gap, and the method then lights a fire from cold — with the striker surviving as reusable kit.
 * Skips gracefully without Docker.
 */
@SpringBootTest
class SteelStrikerIntegrationTest {

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
    @Autowired FireService fire;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aCaseHardenedStrikerMakesTheFlintAndSteelFireReachable() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // Before smelting reached fire-making, flint-and-steel is blocked on the striker it cannot make.
        assertTrue(fire.profile(chronicle, "flint_and_steel").missing().contains("steel_striker"),
                "flint-and-steel must start out missing its steel striker");

        // A fire pit with a live fire and the metal stock to case-harden a striker over it.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,480,?)", pit, ts);
        items.createCarriedItem(chronicle, "iron_bloom", "Iron bloom", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");

        // Case-harden the striker through the real router.
        String[] forge = items.runProcess(chronicle, chunk, "carburise a steel striker", now);
        assertEquals("SUCCEEDED", forge[0], () -> "case-hardening a steel striker must succeed through the router: " + forge[1]);
        assertTrue(items.hasAtLeast(chronicle, "steel_striker", 1), "case-hardening must yield a steel striker");

        // With a flint in hand as well, flint-and-steel is now fully kitted.
        items.createCarriedItem(chronicle, "flint_stone", "Flint", now, "TEST_SEED");
        assertTrue(fire.profile(chronicle, "flint_and_steel").missing().isEmpty(),
                "with a made striker and a flint, flint-and-steel must want for nothing");

        // Light a fire with it from cold: put the carburising fire out, then strike a fresh one by flint and steel.
        items.createCarriedItem(chronicle, "tinder_nest", "Tinder nest", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        fire.extinguish(chunk, now);
        FireService.LightResult lit = fire.light(chronicle, chunk, now, true, "flint_and_steel");
        assertEquals(FireService.LightResult.LIT, lit, "flint and steel must light a fire once the striker is made");

        // The striker is reusable kit — it is not spent by lighting a fire.
        assertTrue(items.hasAtLeast(chronicle, "steel_striker", 1), "a steel striker must survive lighting a fire (reusable kit)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
