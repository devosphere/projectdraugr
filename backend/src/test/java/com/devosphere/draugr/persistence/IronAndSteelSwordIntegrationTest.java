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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iron and steel swords (#967, EPIC #964), V478. Iron does not pour, so the iron sword is a bloom hammered into a
 * billet, the billet forged into a blade, and the blade hilted. The steel sword branches from the bare iron blade:
 * carburised in charcoal, then hardened and tempered, which wants water as well as fire, then hilted.
 *
 * <p>Every step goes through the real process router in a player's words. The shared family words ("iron sword",
 * "hilt the sword") are shown to take whichever step is next for what the Chronicle holds. Bare iron
 * is shown NOT to harden, because there is no carbon in it to harden. The Chronicle is given its OWN materials and
 * stands on ground with water, so the test owns what it measures. Skips gracefully without Docker.
 */
@SpringBootTest
class IronAndSteelSwordIntegrationTest {

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
    void ironIsForgedFromTheBloomAndSteelIsHardenedFromTheIron() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        // Wet ground: the hardening plunge wants water at hand, and wetland is water by the world's own rule.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome IN ('WETLAND','RIVER_BANK') ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,960,?)", pit, ts);
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "iron_bloom", "Iron bloom", now, "TEST_SEED");
        for (int i = 0; i < 9; i++) items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");

        // 1. The bloom is hammered into a billet.
        String[] billet = items.runProcess(chronicle, chunk, "forge the bloom into a bar", now);
        assertEquals("SUCCEEDED", billet[0], () -> "consolidating the bloom must succeed: " + billet[1]);
        assertTrue(items.hasAtLeast(chronicle, "iron_billet", 1), "the bloom must become a billet");
        assertFalse(items.hasAtLeast(chronicle, "iron_bloom", 1), "the bloom went into the billet");

        // 2. "forge an iron sword" fits the blade and the hilting equally; only the blade has its billet in reach.
        String[] blade = items.runProcess(chronicle, chunk, "forge an iron sword", now);
        assertEquals("SUCCEEDED", blade[0], () -> "forging the blade must succeed: " + blade[1]);
        assertTrue(items.hasAtLeast(chronicle, "iron_sword_blade", 1), "the billet must become a sword blade");

        // 3. Bare iron does not harden: there is no carbon in it. Nothing is spent by the refusal.
        String[] bare = items.runProcess(chronicle, chunk, "harden the sword blade", now);
        assertEquals("FAILED", bare[0], () -> "bloomery iron must not harden: " + bare[1]);
        assertTrue(items.hasAtLeast(chronicle, "iron_sword_blade", 1), "the refused hardening must not consume the blade");

        // 4. Steel is made by the smith's own verbs. Carburising and hardening are PROCESS work, and "make a steel
        // sword" classifies CRAFT, so those words reach only the hilting (V478 records the measurement).
        String[] carb = items.runProcess(chronicle, chunk, "carburise the sword blade", now);
        assertEquals("SUCCEEDED", carb[0], () -> "carburising must succeed: " + carb[1]);
        assertTrue(items.hasAtLeast(chronicle, "steel_sword_blank", 1), "carburising yields a carburised blade");
        assertFalse(items.hasAtLeast(chronicle, "iron_sword_blade", 1), "the iron blade became the carburised one");

        // 5. Hardened and tempered, on wet ground with the fire going.
        String[] hard = items.runProcess(chronicle, chunk, "harden and temper the blade", now);
        assertEquals("SUCCEEDED", hard[0], () -> "hardening must succeed: " + hard[1]);
        assertTrue(items.hasAtLeast(chronicle, "steel_sword_blade", 1), "hardening yields a steel sword blade");

        // 6. And hilt it.
        items.createCarriedItem(chronicle, "animal_bone", "Animal bone", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "animal_sinew", "Animal sinew", now, "TEST_SEED");
        String[] steel = items.runProcess(chronicle, chunk, "hilt the steel sword", now);
        assertEquals("SUCCEEDED", steel[0], () -> "hilting the steel sword must succeed: " + steel[1]);
        assertTrue(items.hasAtLeast(chronicle, "steel_sword", 1), "the hilted blade is a steel sword");

        // 7. The plain "hilt the sword" fits the bronze, iron and steel hiltings alike; an iron blade in hand settles it.
        items.createCarriedItem(chronicle, "iron_sword_blade", "Iron sword blade", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "animal_bone", "Animal bone", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "animal_sinew", "Animal sinew", now, "TEST_SEED");
        String[] iron = items.runProcess(chronicle, chunk, "hilt the sword", now);
        assertEquals("SUCCEEDED", iron[0], () -> "hilting the iron sword must succeed: " + iron[1]);
        assertTrue(items.hasAtLeast(chronicle, "iron_sword", 1), "the hilted iron blade is an iron sword");

        assertEquals("IRON", jdbc.queryForObject("SELECT edge_tier FROM weapon_profile WHERE item_key='iron_sword'", String.class));
        assertEquals("STEEL", jdbc.queryForObject("SELECT edge_tier FROM weapon_profile WHERE item_key='steel_sword'", String.class));
        assertEquals("SWORD_FIGHTING", jdbc.queryForObject("SELECT combat_skill_key FROM weapon_profile WHERE item_key='steel_sword'", String.class));

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
