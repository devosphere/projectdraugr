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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tree-planting regression (EPIC #200 forestry — #204 replanting, the counter-play to felling/clear-cutting). A
 * cleared or thin wood could recover only near another wood (#309 recolonisation); a Chronicle had no way to
 * restore one deliberately. Now planting a carried tree seed — an acorn grows an oak, a pine nut a pine — on
 * ground that suits the species establishes a young stand (or thickens a thin one) with room to grow, and starts
 * its regrowth clock. So a clear-cut a Chronicle replants comes back over the years where it would stay bare.
 *
 * <p>Proven end to end: planting an acorn on cleared forest ground raises a sapling stand set to grow; planting
 * with no seed fails. Skips gracefully without Docker.
 */
@SpringBootTest
class FloraPlantingIntegrationTest {

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
    @Autowired com.devosphere.draugr.action.ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void plantingAnAcornRaisesAStandSetToGrow() {
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

        // Cleared forest ground — no oak stands here.
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key='oak'", chunk);

        // With no seed, there is nothing to plant.
        com.devosphere.draugr.action.ChronicleActionService.ActionResult noSeed = actions.resolve("I plant a tree.");
        assertEquals("FAILED", noSeed.outcome(), () -> "planting with no seed must fail: " + noSeed.perception());

        // An acorn in hand, planted on ground that suits an oak, raises a sapling stand with room to grow.
        items.createCarriedItem(chronicle, "acorn", "Acorn", now, "TEST_SEED");
        com.devosphere.draugr.action.ChronicleActionService.ActionResult planted = actions.resolve("I plant an acorn.");
        assertEquals("SUCCEEDED", planted.outcome(), () -> "planting an acorn on suitable ground must succeed: " + planted.perception());
        assertFalse(items.hasAtLeast(chronicle, "acorn", 1), "the planted acorn must be consumed");

        Integer quantity = jdbc.queryForObject("SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key='oak'", Integer.class, chunk);
        Integer capacity = jdbc.queryForObject("SELECT capacity FROM chunk_flora WHERE chunk_id=? AND flora_key='oak'", Integer.class, chunk);
        assertEquals(1, quantity, "planting must raise a single sapling (#200/#204)");
        assertTrue(capacity != null && capacity >= 3, "the planted stand must have room to grow into a wood, so regrowth carries it up (#200/#204)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
