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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The siblings that forgot their bench (#77/#220, V342).
 *
 * <p>A station is declared per process, so every member of a family done at one bench has to name it. Wool was woven
 * on the loom and linen on nothing; a single arrow was fletched in the jig and a bundle freehand. This proves the
 * linen case end to end — FINE thread and a careful hand give SOUND cloth off the bare ground and FINE cloth with a
 * loom to hand — and asserts the other four by their data. Deterministic, not statistical. Skips without Docker.
 */
@SpringBootTest
class SiblingsTakeTheirBenchIntegrationTest {

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
    void linenWovenOnALoomComesOutTruerAndLinenWovenWithoutOneStillComesOut() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        assertEquals("SOUND", weave(chronicle, chunk, now),
            "without a loom a careful hand gives sound cloth — a station eases, it never gates, and the linen is still woven");

        items.createCarriedItem(chronicle, "loom", "Loom", now, "TEST_FIXTURE");
        assertEquals("FINE", weave(chronicle, chunk, now),
            "on the loom, the same thread and the same hand come out one grade truer — as wool always did");

        // The other four, by their data: each names the bench its siblings already name.
        List<String> benched = jdbc.queryForList(
            "SELECT process_key FROM material_process WHERE (process_key, station_kind) IN " +
            "(('sew_winter_stock_blanket','sewing_table'), ('fletch_arrows','arrow_fletching_jig'), " +
            " ('grind_salt','quern_stone'), ('grind_pigment','quern_stone')) ORDER BY 1", String.class);
        assertEquals(4, benched.size(), () -> "the blanket, the arrow bundle, the salt and the ochre must name their bench: " + benched);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Weave linen from four FINE threads with a careful hand, and answer the grade of the cloth. */
    private String weave(UUID chronicle, UUID chunk, Instant now) {
        for (int i = 0; i < 4; i++) {
            UUID thread = items.createCarriedItem(chronicle, "linen_thread", "Linen thread", now, "TEST_FIXTURE");
            jdbc.update("UPDATE item_instance SET quality_grade='FINE' WHERE object_id=?", thread);
        }
        String[] done = items.runProcess(chronicle, chunk, "carefully weave linen", now);
        assertEquals("SUCCEEDED", done[0], () -> "the linen must be woven: " + done[1]);
        return jdbc.queryForObject(
            "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            " WHERE i.item_key='linen_cloth' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            " ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }
}
