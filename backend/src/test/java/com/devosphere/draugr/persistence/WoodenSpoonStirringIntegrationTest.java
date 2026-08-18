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
 * Wooden-spoon usefulness regression (dead-craft audit #257, unblocked by #271; EPIC #123). A wooden spoon was
 * craftable but read by nothing — a pot cooked the same with or without one. executeProcess now lets a spoon to
 * hand lift a pot-cook one grade (stew/porridge/greens/mushrooms): kept moving, the pot cooks evenly and nothing
 * scorches on the bottom. The same minor bounded assist a workstation gives, still capped against the ingredients
 * by worst() — and terminal now that food grade scales nourishment (#271). Proven deterministically: from the
 * same FINE grain and water, a plain boil yields SOUND porridge, but a spoon to hand yields FINE porridge.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class WoodenSpoonStirringIntegrationTest {

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

    /** The grade of the most-recently-cooked porridge the Chronicle carries. */
    private String newestPorridgeGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='grain_porridge' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void aWoodenSpoonLiftsTheWorkmanshipOfABoiledPorridge() {
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

        // An active fire pit at the location to boil over (cook_porridge requires fire).
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);

        // FINE grain and water so the workmanship — not the stock — is what caps the porridge.
        items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "clean_water", "Boiled water", now, "TEST_SEED", QualityGrade.FINE);

        // A plain boil, no spoon: the workmanship is only SOUND, so the FINE stock is capped to SOUND porridge.
        String[] plain = items.executeProcess(chronicle, chunk, "cook_porridge", "boil grain porridge", now);
        assertEquals("SUCCEEDED", plain[0], () -> "boiling porridge must succeed: " + plain[1]);
        assertEquals("SOUND", newestPorridgeGrade(chronicle), "a plain boil of FINE stock yields SOUND porridge");

        // The same stock and the same words, but a wooden spoon to hand: the even stirring lifts it to FINE.
        items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "clean_water", "Boiled water", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "wooden_spoon", "Wooden spoon", now, "TEST_SEED");
        String[] withSpoon = items.executeProcess(chronicle, chunk, "cook_porridge", "boil grain porridge", now);
        assertEquals("SUCCEEDED", withSpoon[0], () -> "boiling with a spoon must succeed: " + withSpoon[1]);
        assertEquals("FINE", newestPorridgeGrade(chronicle), "a wooden spoon lifts the same FINE stock to FINE porridge (#257/#271)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
