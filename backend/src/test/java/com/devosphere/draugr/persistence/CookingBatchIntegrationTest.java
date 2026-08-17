package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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
 * Batch-cooking regression (dead-craft audit #257, EPIC #123). A cooking tripod and a stone griddle were
 * craftable but read by nothing — cooking turned out one piece of meat per turn whether or not you had a way
 * to cook several at once. cookGameMeat now cooks up to three pieces in a turn when a tripod (skewers) or a
 * griddle (a hot surface) is to hand, and one over a bare fire. Proven end to end: with a griddle a Chronicle
 * cooks more meat in a single turn than bare-handed over the same fire.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class CookingBatchIntegrationTest {

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
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int cooked(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='cooked_game_meat' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
    }

    @Test
    void aGriddleCooksSeveralPiecesInOneTurnWhereBareFireDoesOne() {
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
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // An active fire pit at the location to cook over.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);

        // Plenty of raw meat to cook.
        for (int i = 0; i < 8; i++) items.createCarriedItem(chronicle, "raw_game_meat", "Raw game meat", now, "TEST_SEED");

        // Over a bare fire, one piece cooks in a turn.
        int before1 = cooked(chronicle);
        ChronicleActionService.ActionResult bare = actions.resolve("I cook the meat over the fire.");
        assertEquals("SUCCEEDED", bare.outcome(), () -> "cooking over an active fire must succeed: " + bare.perception());
        int bareBatch = cooked(chronicle) - before1;
        assertEquals(1, bareBatch, "over a bare fire only one piece cooks per turn");

        // With a stone griddle, several cook at once.
        items.createCarriedItem(chronicle, "stone_griddle", "Stone griddle", now, "TEST_SEED");
        int before2 = cooked(chronicle);
        ChronicleActionService.ActionResult withGriddle = actions.resolve("I cook the meat over the fire.");
        assertEquals("SUCCEEDED", withGriddle.outcome(), () -> "cooking on a griddle must succeed: " + withGriddle.perception());
        int griddleBatch = cooked(chronicle) - before2;

        assertTrue(griddleBatch > bareBatch,
                () -> "a griddle must cook more pieces in a turn than a bare fire (griddle=" + griddleBatch + ", bare=" + bareBatch + ") — the griddle must be USED (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
