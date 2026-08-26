package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke drift — an emission is a hazard footprint that reaches the connected ground, not a point event (EPIC #215,
 * story #219). A smoky working (a charcoal burn) disturbs the ground it stands on AND drifts a lesser disturbance
 * onto the orthogonally-adjacent chunks, so the wildlife of the ring around it grow wary too — bounded to the
 * immediate neighbours (the plume thins with distance), and never reaching ground two chunks off.
 *
 * <p>Proven deterministically: the source takes the full amount; each orthogonal neighbour takes half, logged as
 * its own SMOKE_DRIFT provenance; a chunk two steps away is untouched. Skips gracefully without Docker.
 */
@SpringBootTest
class SmokeDriftIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int level(UUID chunk) {
        Integer v = jdbc.query("SELECT disturbance_level FROM chunk_disturbance WHERE chunk_id=?", rs -> rs.next() ? rs.getInt(1) : null, chunk);
        return v == null ? 0 : v;
    }

    @Test
    void aSmokySourceDisturbsItsGroundAndDriftsOntoTheAdjacentRingButNoFurther() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");

        // A source chunk that actually has neighbours (an interior tile), and its world.
        UUID source = jdbc.queryForObject(
            "SELECT here.id FROM world_chunk here WHERE EXISTS (SELECT 1 FROM world_chunk n " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1) LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, source);

        // Clean slate for the measurement across this world's chunks.
        jdbc.update("DELETE FROM chunk_disturbance WHERE chunk_id IN (SELECT id FROM world_chunk WHERE world_id=?)", world);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        wildlife.recordEmissionDrift(source, "SMOKE", 20, now);

        // The ground it stands on takes the full amount.
        assertEquals(20, level(source), "the smoky working must disturb the ground it stands on in full");

        // Every orthogonal neighbour takes half the disturbance, kept as its own SMOKE_DRIFT provenance.
        List<UUID> neighbours = jdbc.queryForList(
            "SELECT n.id FROM world_chunk n JOIN world_chunk here ON here.id=? " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1", UUID.class, source);
        assertFalse(neighbours.isEmpty(), "the interior source must have adjacent ground for the plume to reach");
        for (UUID n : neighbours) {
            assertEquals(10, level(n), () -> "the drifting plume must disturb the adjacent ground by half");
            Integer driftEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunk_disturbance_event WHERE chunk_id=? AND source_kind='SMOKE_DRIFT' AND occurred_at=?",
                Integer.class, n, ts);
            assertTrue(driftEvents != null && driftEvents >= 1, "each neighbour's drift must be kept as SMOKE_DRIFT history (#208/#219)");
        }

        // A chunk two steps away is beyond the plume — untouched.
        UUID far = jdbc.query(
            "SELECT n.id FROM world_chunk n JOIN world_chunk here ON here.id=? " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)>=2 LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, source);
        if (far != null) assertEquals(0, level(far), "a hazard reaches only the connected neighbours, never ground two chunks off");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
