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
 * Bronze pickaxe (EPIC #180 heavy industry — closing the extraction loop). The metal a Chronicle smelts lets them
 * forge a pickaxe, and a metal pick breaks a nodule out whole where a cobble crushes it — so a worker with one wins
 * a FINER mineral than bare stone tools could, and finer ore smelts to finer metal and finer tools.
 *
 * <p>Proven: a bronze pickaxe forges through the real router, and the same careful mining turns up a SOUND mineral
 * bare-handed of metal but a FINE one with the pick to hand. Skips gracefully without Docker.
 */
@SpringBootTest
class BronzePickaxeIntegrationTest {

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

    /** Mining has a success chance, so try until a nodule comes free (or give up after many tries). */
    private void mineUntilSuccess(UUID chronicle, UUID chunk, Instant now) {
        for (int i = 0; i < 60; i++) {
            if ("SUCCEEDED".equals(items.gatherMineral(chronicle, chunk, "mine the flint here", now)[0])) return;
        }
        throw new AssertionError("mining never succeeded across 60 attempts");
    }

    private String newestFlintGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='flint_stone' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void aBronzePickaxeForgesAndWinsFinerOreThanBareStoneTools() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        // Ground where flint weathers out of the rock (its affinity: mountain, highland, grassland).
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome IN ('MOUNTAIN','HIGHLAND','GRASSLAND') ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have flint-bearing ground to mine");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // Bare of a metal pick: plain work turns up only a SOUND nodule.
        mineUntilSuccess(chronicle, chunk, now);
        assertEquals("SOUND", newestFlintGrade(chronicle), "plain mining without a metal pick yields a SOUND flint");

        // Forge a bronze pickaxe from an ingot and a haft over a fire — through the real router.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "bronze_ingot", "Bronze ingot", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        String[] forge = items.runProcess(chronicle, chunk, "forge a bronze pickaxe", now);
        assertEquals("SUCCEEDED", forge[0], () -> "forging a bronze pickaxe must succeed: " + forge[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_pickaxe", 1), "forging must yield a bronze pickaxe");

        // The same plain mining, now with the pick to hand, breaks out a FINE nodule.
        mineUntilSuccess(chronicle, chunk, now);
        assertEquals("FINE", newestFlintGrade(chronicle), "a bronze pickaxe wins a FINE flint from the same rock (#180)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
