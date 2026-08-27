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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Noise carries (EPIC #215 / story #208 noise disturbance; #219 footprint propagation). The crash of a felled tree
 * is loud — it does not stop at the ground it happened on, but reaches the wildlife of the neighbouring country too.
 * The habitat-disturbance model was a point source for such events; felling (and a fight) now drift a lesser
 * disturbance onto the adjacent chunks, the same way a plume of smoke does.
 *
 * <p>Proven through the real action pipeline: felling a tree marks its own ground with the loss and drifts a
 * CANOPY_LOSS_DRIFT onto the adjacent ground. Skips gracefully without Docker.
 */
@SpringBootTest
class NoiseDriftIntegrationTest {

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

    private int events(UUID chunk, String kind) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM chunk_disturbance_event WHERE chunk_id=? AND source_kind=?", Integer.class, chunk, kind);
        return n == null ? 0 : n;
    }

    @Test
    void fellingATreeMarksItsGroundAndDriftsTheNoiseOntoTheNeighbours() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        // An interior forest tile (so the crash has neighbours to reach), and its world.
        UUID chunk = jdbc.queryForObject(
            "SELECT here.id FROM world_chunk here WHERE here.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk n " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1) LIMIT 1", UUID.class);
        assertNotNull(chunk, "an interior forest tile is needed for the noise to carry");
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", now, "TEST_SEED");

        // Clean slate so the measurement is unambiguous across this world.
        jdbc.update("DELETE FROM chunk_disturbance_event WHERE chunk_id IN (SELECT id FROM world_chunk WHERE world_id=?) AND source_kind IN ('CANOPY_LOSS','CANOPY_LOSS_DRIFT')", world);

        // Fell the tree through the full action pipeline — the loss is recorded, and the noise drifts.
        actions.resolve("fell the oak tree");

        assertTrue(events(chunk, "CANOPY_LOSS") >= 1, "felling must mark its own ground with the canopy loss");
        List<UUID> neighbours = jdbc.queryForList(
            "SELECT n.id FROM world_chunk n JOIN world_chunk here ON here.id=? " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1", UUID.class, chunk);
        boolean anyDrift = neighbours.stream().anyMatch(nb -> events(nb, "CANOPY_LOSS_DRIFT") >= 1);
        assertTrue(anyDrift, "the crash of the felled tree must drift onto the neighbouring ground (#208)");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
