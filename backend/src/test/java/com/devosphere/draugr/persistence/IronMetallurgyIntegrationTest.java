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
 * Iron metallurgy (EPIC #180 heavy industry / #186 iron ore & smelting). The capstone of the metal ladder: common
 * iron ore smelted into a spongy bloom, hammered free of slag and forged into an iron axe that out-cuts and
 * out-fights bronze.
 *
 * <p>Proven through the real process router: iron ore smelts to a bloom, the bloom forges to an axe; the axe fells
 * a tree, and over a fixed battery out-kills a bronze axe. Skips gracefully without Docker.
 */
@SpringBootTest
class IronMetallurgyIntegrationTest {

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

    private int kills(UUID chronicle, UUID chunk, UUID quarry, Instant now, List<UUID> actionIds) {
        int killed = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE chronicle_physiology SET energy_level=90, injury_severity=0, pain_level=0, illness_severity=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE wildlife_population SET population_count=1000, behavior_state='FORAGING' WHERE id=?", quarry);
            if ("SUCCEEDED".equals(wildlife.confront(chronicle, chunk, action, now).outcome())) killed++;
        }
        return killed;
    }

    private void equipInHand(UUID chronicle, String itemKey, Instant now) {
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicle);
        UUID item = items.createCarriedItem(chronicle, itemKey, itemKey, now, "TEST_SEED");
        jdbc.update("INSERT INTO equipment_attachment(item_id, chronicle_id, body_position, layer) VALUES (?,?,'HAND_RIGHT','CARRIED')", item, chronicle);
    }

    @Test
    void ironOreSmeltsToABloomForgesToAnAxeThatOutFightsBronze() {
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
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");

        // Smelt the iron ore into a bloom.
        items.createCarriedItem(chronicle, "iron_ore", "Iron ore", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "iron_ore", "Iron ore", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        String[] smelt = items.runProcess(chronicle, chunk, "smelt the iron ore", now);
        assertEquals("SUCCEEDED", smelt[0], () -> "smelting iron ore must succeed: " + smelt[1]);
        assertTrue(items.hasAtLeast(chronicle, "iron_bloom", 1), "smelting must yield an iron bloom");

        // Forge the bloom into an axe.
        String[] forge = items.runProcess(chronicle, chunk, "forge an iron axe", now);
        assertEquals("SUCCEEDED", forge[0], () -> "forging an iron axe must succeed: " + forge[1]);
        assertTrue(items.hasAtLeast(chronicle, "iron_axe", 1), "forging must yield an iron axe");

        // It fells a tree.
        assertEquals("SUCCEEDED", items.fellTree(chronicle, chunk, now)[0], "an iron axe must fell a tree");

        // Its terminal edge: over a fixed battery it out-kills a bronze axe.
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Deer range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Deer range',400)", site, worldId, chunk);
        UUID quarry = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'red_deer','HERBIVORE','DIURNAL',1000,2000,'FORAGING',?)", quarry, site, ts);

        java.util.Random rnd = new java.util.Random(3301);
        List<UUID> actionIds = new ArrayList<>();
        for (int i = 0; i < 120; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        equipInHand(chronicle, "bronze_axe", now);
        int bronzeAxe = kills(chronicle, chunk, quarry, now, actionIds);
        equipInHand(chronicle, "iron_axe", now);
        int ironAxe = kills(chronicle, chunk, quarry, now, actionIds);

        assertTrue(ironAxe > bronzeAxe,
                () -> "an iron axe must out-fight a bronze one: iron=" + ironAxe + " bronze=" + bronzeAxe + " (#180/#186)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
