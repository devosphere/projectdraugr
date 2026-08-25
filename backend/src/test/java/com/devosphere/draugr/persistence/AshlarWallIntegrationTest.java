package com.devosphere.draugr.persistence;

import com.devosphere.draugr.assembly.AssemblyService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dry stone wall from dressed ashlar (EPIC #180 / #183 masonry). Dressing ashlar and laying a dry course produced a
 * dry ashlar course that nothing then used — the finer masonry led nowhere. This raises the structure it was for: a
 * dry stone wall, built course on course from dressed ashlar, standing by the fit of its cutting alone.
 *
 * <p>Proven: a dry-stone-wall assembly advances through its stages consuming the dry ashlar courses, and stands
 * complete — closing the ashlar-course dead-end. Skips gracefully without Docker.
 */
@SpringBootTest
class AshlarWallIntegrationTest {

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
    @Autowired AssemblyService assembly;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int count(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey, chronicle);
        return n == null ? 0 : n;
    }

    private boolean wallComplete(UUID chronicle) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assembly_instance WHERE assembly_key='dry_stone_wall' AND chronicle_id=? AND state='COMPLETE'",
                Integer.class, chronicle);
        return n != null && n > 0;
    }

    @Test
    void aDryStoneWallIsRaisedFromDressedAshlarCourses() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED"); // the wall stages are STRIKING work

        // The dry ashlar courses to build with: one for the footing, three for the courses above.
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "ashlar_course", "Dry ashlar course", now, "TEST_SEED");
        assertEquals(4, count(chronicle, "ashlar_course"), "start with four dry ashlar courses");

        // Advance the wall stage by stage until it stands complete.
        boolean done = false;
        for (int i = 0; i < 5 && !done; i++) {
            String[] step = assembly.advance(chronicle, chunk, "build a dry stone wall", now);
            assertNotNull(step, "the text must name the dry-stone-wall assembly");
            assertTrue(!"FAILED".equals(step[0]), () -> "advancing the wall must not fail: " + step[1]);
            done = wallComplete(chronicle);
        }

        assertTrue(done, "the dry stone wall must stand complete after its stages are worked");
        assertEquals(0, count(chronicle, "ashlar_course"), "the wall must consume the dry ashlar courses — no dead-end");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
