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
 * Metal-axe durability (EPIC #180 heavy industry — the third payoff of metal). Beside its keener edge (combat) and
 * the finer work it turns out (grade), a harder metal holds that edge through far more work: the wear thresholds
 * scale with the metal, so stone(8/16) &lt; copper(12/24) &lt; bronze(16/32) &lt; iron(24/48) &lt; steel(32/64).
 *
 * <p>Proven: a steel axe is still SOUND after the felling that would have BROKEN a stone one, and wears through its
 * own far higher thresholds. Skips gracefully without Docker.
 */
@SpringBootTest
class MetalAxeDurabilityIntegrationTest {

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
    void aSteelAxeOutlastsWhatWouldBreakAStoneOne() {
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

        UUID axe = items.createCarriedItem(chronicle, "steel_axe", "Steel axe", now, "TEST_SEED");

        // Fifteen fellings deep — the point a knapped stone axe breaks (BROKEN at 16). The next fell would take a
        // stone axe past its life; a steel axe shrugs it off and stays SOUND.
        jdbc.update("UPDATE item_instance SET use_count=15, condition_state='SOUND' WHERE object_id=?", axe);
        assertEquals("SUCCEEDED", items.fellTree(chronicle, chunk, now)[0], "a sound steel axe fells");
        assertEquals("SOUND", condition(axe), "a steel axe is still SOUND where a stone axe would have BROKEN (#180)");

        // It does wear, but far later: WORN only at 32 uses.
        jdbc.update("UPDATE item_instance SET use_count=31, condition_state='SOUND' WHERE object_id=?", axe);
        assertEquals("SUCCEEDED", items.fellTree(chronicle, chunk, now)[0], "a steel axe fells at 31 uses");
        assertEquals("WORN", condition(axe), "a steel axe steps to WORN at 32 uses");

        // And BROKEN only at 64 — four times the life of stone.
        jdbc.update("UPDATE item_instance SET use_count=63, condition_state='WORN' WHERE object_id=?", axe);
        assertEquals("SUCCEEDED", items.fellTree(chronicle, chunk, now)[0], "a worn steel axe still fells at 63 uses");
        assertEquals("BROKEN", condition(axe), "a steel axe steps to BROKEN at 64 uses");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    private String condition(UUID axe) {
        return jdbc.queryForObject("SELECT condition_state FROM item_instance WHERE object_id=?", String.class, axe);
    }
}
