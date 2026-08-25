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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quern-stone (EPIC #180 / #183 stone shaping; #45 food). Grain ground by hand on a rough stone comes out coarse;
 * a dressed quern grinds it fine, and a finer flour bakes a more nourishing bread (food quality carries into
 * nourishment, #271). The quern is the workstation of grinding: at one, the flour comes out finer.
 *
 * <p>Proven through the real router: grinding fine grain with no quern yields only coarse (SOUND) flour, but a quern
 * dressed from a stone slab lifts the same grinding to fine (FINE) flour. Skips gracefully without Docker.
 */
@SpringBootTest
class QuernStoneIntegrationTest {

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

    private int flourOfGrade(UUID chronicle, String grade) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='grain_flour' AND i.quality_grade=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'",
                Integer.class, grade, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aQuernGrindsFinerFlourThanTheBareStone() {
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
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED"); // grinding is STRIKING work

        // Fine grain to grind — so the flour's grade is limited by the grinding, not the grain (flour is never finer
        // than the grain it came from).
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "wild_grain", "Wild grain", now, "TEST_SEED");
        jdbc.update("UPDATE item_instance SET quality_grade='FINE' WHERE item_key='wild_grain' AND object_id IN " +
                "(SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE')", chronicle);

        // Ground by hand, with no quern: even fine grain comes out only coarse flour.
        String[] byHand = items.runProcess(chronicle, chunk, "grind the grain", now);
        assertEquals("SUCCEEDED", byHand[0], () -> "grinding by hand must succeed: " + byHand[1]);
        assertEquals(0, flourOfGrade(chronicle, "FINE"), "hand-grinding must not produce fine flour");
        assertTrue(flourOfGrade(chronicle, "SOUND") >= 1, "hand-grinding yields coarse (sound) flour");

        // Dress a quern from a stone slab — through the real router.
        items.createCarriedItem(chronicle, "stone_slab", "Stone slab", now, "TEST_SEED");
        String[] dress = items.runProcess(chronicle, chunk, "dress a quern stone", now);
        assertEquals("SUCCEEDED", dress[0], () -> "dressing a quern-stone must succeed through the router: " + dress[1]);
        assertTrue(items.hasAtLeast(chronicle, "quern_stone", 1), "dressing must yield a quern-stone");

        // The same fine grain, ground at the quern, comes out fine flour.
        String[] atQuern = items.runProcess(chronicle, chunk, "grind the grain", now);
        assertEquals("SUCCEEDED", atQuern[0], () -> "grinding at the quern must succeed: " + atQuern[1]);
        assertTrue(flourOfGrade(chronicle, "FINE") >= 1,
                "grinding at a quern must yield finer flour than the bare stone (#183/#271)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
