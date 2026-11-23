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
 * Bronze short sword (#966, EPIC #964). Four acts, not one: a clay mould is made and fired, the ingot is cast in it,
 * the casting's edges are forged cold into a blade, and the blade is hilted. Every step is driven through the real
 * process router by the words a player would use, and the shared "bronze sword" keyword is shown to take the NEXT
 * step of the road for a Chronicle standing at each step.
 *
 * <p>The Chronicle is given its OWN materials rather than asking the shared world for them, so the test owns what it
 * measures. Skips gracefully without Docker.
 */
@SpringBootTest
class BronzeShortSwordIntegrationTest {

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
    void aBronzeSwordIsMouldedCastEdgedAndHilted() {
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
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");

        // 1. The mould: two lumps of clay, cut and fired.
        items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_SEED");
        String[] mould = items.runProcess(chronicle, chunk, "make a sword mould", now);
        assertEquals("SUCCEEDED", mould[0], () -> "making the mould must succeed: " + mould[1]);
        assertTrue(items.hasAtLeast(chronicle, "sword_mould", 1), "the clay must become a sword mould");

        // 2. The pour. Said with the shared family words: holding an ingot, a mould and charcoal, "forge a bronze
        // sword" can only mean the cast -- the edging and hilting have nothing in reach to work on.
        items.createCarriedItem(chronicle, "bronze_ingot", "Bronze ingot", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        String[] cast = items.runProcess(chronicle, chunk, "forge a bronze sword", now);
        assertEquals("SUCCEEDED", cast[0], () -> "the pour must succeed: " + cast[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_sword_casting", 1), "the pour must yield a casting");
        assertFalse(items.hasAtLeast(chronicle, "sword_mould", 1), "the mould is broken open to free the casting");
        assertFalse(items.hasAtLeast(chronicle, "bronze_ingot", 1), "the ingot went into the mould");

        // 3. The same words again now take the next step: the casting is in hand, so its edges are forged.
        String[] edges = items.runProcess(chronicle, chunk, "forge a bronze sword", now);
        assertEquals("SUCCEEDED", edges[0], () -> "forging the edges must succeed: " + edges[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_sword_blade", 1), "the casting must become a blade");

        // 4. The hilt: a bone and sinew, the thriftiest of each group.
        items.createCarriedItem(chronicle, "animal_bone", "Animal bone", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "animal_sinew", "Animal sinew", now, "TEST_SEED");
        String[] hilt = items.runProcess(chronicle, chunk, "hilt the sword", now);
        assertEquals("SUCCEEDED", hilt[0], () -> "hilting must succeed: " + hilt[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_short_sword", 1), "the hilted blade is a bronze short sword");

        // And it is a sword in a fight: a bronze edge, drawing on the discipline the wooden sword began.
        assertEquals("BRONZE", jdbc.queryForObject("SELECT edge_tier FROM weapon_profile WHERE item_key='bronze_short_sword'", String.class));
        assertEquals("SWORD_FIGHTING", jdbc.queryForObject("SELECT combat_skill_key FROM weapon_profile WHERE item_key='bronze_short_sword'", String.class));

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * V479: a worn bronze sword is not a dead end. One sword (760 g) cannot honestly fill a 900 g ingot, so it takes
     * two; the hilts and the dross are the loss.
     */
    @Test
    void twoBronzeSwordsMeltDownIntoOneIngot() {
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

        // One sword is not enough metal for an ingot: the melt is refused rather than inventing bronze.
        items.createCarriedItem(chronicle, "bronze_short_sword", "Bronze short sword", now, "TEST_SEED");
        String[] tooLittle = items.runProcess(chronicle, chunk, "melt down the bronze sword", now);
        assertFalse("SUCCEEDED".equals(tooLittle[0]), () -> "one sword must not make a whole ingot: " + tooLittle[1]);
        assertFalse(items.hasAtLeast(chronicle, "bronze_ingot", 1), "no ingot from a single sword");
        assertTrue(items.hasAtLeast(chronicle, "bronze_short_sword", 1), "the refused melt must leave the sword alone");

        // Two swords fill the crucible.
        items.createCarriedItem(chronicle, "bronze_short_sword", "Bronze short sword", now, "TEST_SEED");
        String[] melt = items.runProcess(chronicle, chunk, "melt down the bronze swords", now);
        assertEquals("SUCCEEDED", melt[0], () -> "two swords must melt down: " + melt[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_ingot", 1), "the swords' metal must be recovered as an ingot");
        assertFalse(items.hasAtLeast(chronicle, "bronze_short_sword", 1), "both swords went into the melt");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
