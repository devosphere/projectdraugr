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
 * Silver and clean water (EPIC #180 heavy industry / #188 gold, silver, lead). Silver is too soft for a tool, but
 * it keeps water sweet — the bright metal holds off the rot — so this gives silver its own chain (mine → smelt →
 * cast a cup) and a distinctive use: the safest way to drink untreated water short of boiling it.
 *
 * <p>Proven: silver ore smelts and casts to a cup through the real router, and the same raw water drunk from a
 * silver cup carries markedly less gut-illness than drunk from a bare skin. Skips gracefully without Docker.
 */
@SpringBootTest
class SilverCupIntegrationTest {

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

    /** Reset illness, give one carried raw water, drink it through the public action, report illness gained. */
    private int illnessFromDrinkingRawWater(UUID chronicle, Instant at) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0 WHERE chronicle_id=?", chronicle);
        assertTrue(items.makeWater(chronicle, "raw_water", "Raw water", 1, at) > 0, "must carry one raw water to drink");
        ChronicleActionService.ActionResult r = actions.resolve("I drink the water.");
        assertTrue("SUCCEEDED".equals(r.outcome()) || "DRINK".equals(r.intent()), () -> "drinking must resolve: " + r.perception());
        return jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    @Test
    void aSilverCupKeepsRawWaterFitToDrink() {
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

        // Bare skin: drinking the raw water you carry sits uneasy in the gut.
        int fromSkin = illnessFromDrinkingRawWater(chronicle, now);
        assertTrue(fromSkin > 0, "drinking untreated water must carry a real gut-illness risk");

        // Mine, smelt, and cast silver into a cup — through the real router.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);
        items.createCarriedItem(chronicle, "silver_ore", "Silver ore", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "silver_ore", "Silver ore", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        String[] smelt = items.runProcess(chronicle, chunk, "smelt the silver ore", now);
        assertEquals("SUCCEEDED", smelt[0], () -> "smelting silver ore must succeed: " + smelt[1]);
        assertTrue(items.hasAtLeast(chronicle, "silver_ingot", 1), "smelting must yield a silver ingot");
        String[] cast = items.runProcess(chronicle, chunk, "cast a silver cup", now);
        assertEquals("SUCCEEDED", cast[0], () -> "casting a silver cup must succeed: " + cast[1]);
        assertTrue(items.hasAtLeast(chronicle, "silver_cup", 1), "casting must yield a silver cup");

        // The same raw water, now drunk from the silver cup: the gut fares far better.
        int fromSilver = illnessFromDrinkingRawWater(chronicle, now);

        assertTrue(fromSilver < fromSkin,
                () -> "a silver cup must keep raw water fitter to drink: silver=" + fromSilver + " vs skin=" + fromSkin + " (#188)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
