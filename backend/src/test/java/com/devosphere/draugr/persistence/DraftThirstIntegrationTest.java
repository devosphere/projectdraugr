package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
 * Kept animals must need water. wildlife_bond tracked fatigue, conditioning and hunger but nothing for thirst, so an
 * animal never needed a drink and every watering structure in the catalogue was inert by construction. Proves thirst
 * rises on dry ground, that a built trough answers it, and that a thirsty beast hauls less. Skips without Docker.
 */
@SpringBootTest
class DraftThirstIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void keptStockGrowThirstyOnDryGroundUntilATroughStands() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Dry ground: no wet biome, no freshwater site, nothing built.
        UUID chunk = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome NOT IN ('WETLAND','RIVER_BANK','OCEAN') ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have dry ground for this fixture");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND (site_kind ILIKE '%spring%' OR site_kind ILIKE '%stream%' OR site_kind ILIKE '%river%' OR site_kind ILIKE '%freshwater%')", chunk);
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);

        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',20)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'ox','HERBIVORE','DIURNAL',1,3,'FORAGING',?)", pop, site, ts);
        UUID bond = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',95,10,?)", bond, chronicle, pop, ts);

        // Away from water, the animal dries out.
        items.advanceDraftThirst(now);
        int thirsty = jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bond);
        assertTrue(thirsty > 0, () -> "stock kept away from water must grow thirsty (got " + thirsty + ")");

        // A thirsty beast is worth less at the traces than a watered one.
        jdbc.update("UPDATE wildlife_bond SET draft_thirst=90 WHERE id=?", bond);
        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST_FIXTURE");
        int parched = items.sustainedMassCapacity(chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_thirst=0 WHERE id=?", bond);
        int watered = items.sustainedMassCapacity(chronicle);
        assertTrue(watered > parched,
            () -> "a thirsty beast must haul less than a watered one (" + parched + " vs " + watered + ")");

        // Build a trough, and the ground can keep stock after all.
        jdbc.update("UPDATE wildlife_bond SET draft_thirst=80 WHERE id=?", bond);
        items.createCarriedItem(chronicle, "timber_log", "Timber log", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", now, "TEST_FIXTURE");
        var built = actions.resolve("build a watering trough");
        assertEquals("SUCCEEDED", built.outcome(), () -> "building a watering trough must succeed: " + built.perception());

        items.advanceDraftThirst(now);
        int afterTrough = jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bond);
        assertTrue(afterTrough < 80, () -> "a built trough must water the stock (was 80, now " + afterTrough + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
