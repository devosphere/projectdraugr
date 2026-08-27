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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crop depredation (EPIC #162 / story #166 crop stress; ties to the #127 perimeter fence). A ripe stand standing open
 * on ground grazing animals reach is eaten and trampled unless it is kept; a wattle or brush fence keeps it whole, so
 * the reward for fencing the field (or reaping before the animals find it) is a fuller harvest.
 *
 * <p>Proven through the world tick: a ripe stand on unfenced ground with grazers near is marked grazed and reaps
 * less; the same stand behind a fence is untouched and reaps full. Skips gracefully without Docker.
 */
@SpringBootTest
class CropGrazingIntegrationTest {

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

    private int heads(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='wild_grain_head' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private boolean grazed(UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT grazed FROM crop_stand WHERE chunk_id=? AND harvested=false", Boolean.class, chunk));
    }

    private void putHerbivores(UUID chunk, UUID world, Instant now) {
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',100)", site, world, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'red_deer','HERBIVORE','DIURNAL',4,8,'FORAGING',?)", UUID.randomUUID(), site, Timestamp.from(now));
    }

    private void ripeCrop(UUID chunk, Instant now) {
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested) VALUES (?,?,?,?,30,false)",
            UUID.randomUUID(), chunk, "wild_grain", Timestamp.from(now.minus(Duration.ofDays(35))));
    }

    private void fence(UUID chunk, Instant now) {
        UUID f = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Wattle fence',?)", f, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'WATTLE_FENCE','COMPLETED',100,?,100)", f, Timestamp.from(now));
    }

    @Test
    void aRipeUnfencedCropIsGrazedDownWhileAFencedOneIsKept() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        List<UUID> chunks = jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        UUID open = chunks.get(0), fenced = chunks.get(1);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, open);
        Instant now = ticks.current().simulatedAt();

        // Grazers range both fields; one has a ripe stand open, the other a ripe stand behind a wattle fence.
        putHerbivores(open, world, now);
        putHerbivores(fenced, world, now);
        fence(fenced, now);
        ripeCrop(open, now);
        ripeCrop(fenced, now);

        // The world turns an hour — the depredation pass runs.
        ticks.advanceBy(Duration.ofHours(1));

        assertEquals(true, grazed(open), "a ripe open stand with grazers near must be grazed");
        assertEquals(false, grazed(fenced), "a fenced stand must be kept whole");

        assertEquals("SUCCEEDED", items.harvestCrop(chronicle, open, now)[0], "reaping the grazed stand must still succeed");
        int openYield = heads(chronicle);
        assertEquals("SUCCEEDED", items.harvestCrop(chronicle, fenced, now)[0], "reaping the fenced stand must succeed");
        int fencedYield = heads(chronicle) - openYield;

        assertTrue(openYield < fencedYield, () -> "the grazed stand must reap less than the fenced one (open=" + openYield + ", fenced=" + fencedYield + ") (#166)");
        assertEquals(4, fencedYield, "the fenced stand reaps the full untilled yield");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
