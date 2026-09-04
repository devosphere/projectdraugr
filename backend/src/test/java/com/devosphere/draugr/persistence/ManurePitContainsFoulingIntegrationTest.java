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
 * Story #106 - sanitation for kept stock. Tamed draft beasts stood at a camp fouled nothing, however long they were
 * kept there. Now the ground grows foul where stock are kept — refuse already draws wildlife, costs the body, and
 * lets pests dock stored food — and a manure pit contains it. Skips without Docker.
 */
@SpringBootTest
class ManurePitContainsFoulingIntegrationTest {

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

    private int refuseAt(UUID chunk) {
        Integer n = jdbc.queryForObject("SELECT COALESCE((SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?),0)", Integer.class, chunk);
        return n == null ? 0 : n;
    }

    @Test
    void keptStockFoulTheGroundUnlessAManurePitContainsIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        UUID site = UUID.randomUUID();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Pasture',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Pasture',50)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'ox','HERBIVORE','DIURNAL',1,3,'FORAGING',?)", pop, site, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',90,10,?)", UUID.randomUUID(), chronicle, pop, ts);

        // Stock kept on open ground foul it.
        int before = refuseAt(chunk);
        items.foulGroundWithLivestock(now);
        int fouled = refuseAt(chunk);
        assertTrue(fouled > before, () -> "kept stock must foul the ground (was " + before + ", now " + fouled + ")");

        // Dig a manure pit through the real action boundary.
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_FIXTURE");
        var dug = actions.resolve("dig a manure pit");
        assertEquals("SUCCEEDED", dug.outcome(), () -> "digging a manure pit must succeed (not be stolen by BUILD_LATRINE): " + dug.perception());
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE cp.project_kind='MANURE_PIT' AND cp.state='COMPLETED' AND w.current_location_id=?)", Boolean.class, chunk),
            "a completed manure pit must stand on this ground");

        // With the muck gathered into the pit, the ground stops growing foul.
        int held = refuseAt(chunk);
        items.foulGroundWithLivestock(now);
        assertEquals(held, refuseAt(chunk), "a manure pit must contain the muck, so the ground no longer grows foul");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
