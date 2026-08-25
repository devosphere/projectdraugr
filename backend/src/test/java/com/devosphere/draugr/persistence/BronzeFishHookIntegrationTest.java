package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bronze fish hook (EPIC #180 heavy industry / #184 copper objects, #36 fishing). A bone hook bends and loses a good
 * fish; a forged metal hook holds it, so a line fishes better with one. This gives another use to the smelted metal
 * and completes the angling tackle beside the lead sinker.
 *
 * <p>Proven: a handful of bronze hooks forge through the real router, and over a fixed battery a line with a metal
 * hook lands more fish than one with a bone hook. Skips gracefully without Docker.
 */
@SpringBootTest
class BronzeFishHookIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int catches(UUID chronicle, UUID chunk, Instant now, List<UUID> actionIds) {
        // Reset the finite fish stock (#181/#36) so this battery reflects the tackle's catch chance, not how much
        // prior fishing (here or in another test sharing the database) has drawn the water down.
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", chunk);
        int taken = 0;
        for (UUID action : actionIds) {
            if ("SUCCEEDED".equals(wildlife.fish(chronicle, chunk, action, now, "fish the water here").outcome())) taken++;
        }
        return taken;
    }

    @Test
    void aMetalHookLandsMoreFishThanABoneHook() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have wetland water to fish");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        items.createCarriedItem(chronicle, "bone_fish_hook", "Bone fish hook", now, "TEST_SEED");
        java.util.Random rnd = new java.util.Random(4242);
        List<UUID> actionIds = new ArrayList<>();
        for (int i = 0; i < 140; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // A bone hook: a baseline of how often a line takes.
        int boneHook = catches(chronicle, chunk, now, actionIds);
        assertTrue(boneHook > 0, "a bone hook must land some fish (else the test proves nothing)");

        // Forge a handful of bronze hooks — through the real router.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "bronze_ingot", "Bronze ingot", now, "TEST_SEED");
        String[] forge = items.runProcess(chronicle, chunk, "forge a bronze fish hook", now);
        assertEquals("SUCCEEDED", forge[0], () -> "forging bronze fish hooks must succeed: " + forge[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_fish_hook", 1), "forging must yield bronze fish hooks");

        // The same battery, now with a metal hook on the line: it takes more.
        int metalHook = catches(chronicle, chunk, now, actionIds);

        assertTrue(metalHook > boneHook,
                () -> "a metal hook must land more fish than a bone one: metal=" + metalHook + " bone=" + boneHook + " (#184)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
