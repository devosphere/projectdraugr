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
 * Cooked dishes keep by their nature (EPIC #45/#54 cooking; #220 spoilage). The V92 cooked dishes and the new grain
 * flatbread fell through the preservation map to null, so they carried no keeping state and never spoiled at all —
 * an immortal pot of stew. Now a made dish keeps by its kind: a dense dry baked bread keeps for weeks (DRIED), a
 * boiled or stewed wet dish only days (COOKED). Skips gracefully without Docker.
 */
@SpringBootTest
class CookedDishPreservationIntegrationTest {

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

    private String preparationOf(UUID chronicle, String itemKey) {
        return jdbc.query(
            "SELECT fps.preparation_kind FROM food_preservation_state fps " +
            "JOIN item_instance i ON i.object_id=fps.object_id JOIN world_object w ON w.id=fps.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, itemKey, chronicle);
    }

    @Test
    void aBakedBreadKeepsAsDriedAndABoiledDishAsCooked() {
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
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,480,?)", pit, ts);
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "grain_flour", "Grain flour", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "clean_water", "Boiled water", now, "TEST_SEED");

        // A dense baked bread keeps as dried.
        assertEquals("SUCCEEDED", items.runProcess(chronicle, chunk, "bake grain flatbread", now)[0], "baking bread must succeed");
        assertEquals("DRIED", preparationOf(chronicle, "grain_flatbread"), "a dense baked bread must keep as dried, not spoil-never");

        // A boiled porridge keeps only as a cooked dish.
        assertEquals("SUCCEEDED", items.runProcess(chronicle, chunk, "cook grain porridge", now)[0], "boiling porridge must succeed");
        assertEquals("COOKED", preparationOf(chronicle, "grain_porridge"), "a boiled dish must keep as a plain cooked food");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
