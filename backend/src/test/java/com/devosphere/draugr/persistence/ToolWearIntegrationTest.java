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
 * Tool-wear regression (EPIC #215, story #220 — the item side of rust/rot/weathering). condition_state existed and
 * could be mended and examined, but nothing ever wore a tool through use: an axe felled forever and never dulled.
 * Now an axe wears with the felling — at thresholds its condition steps down (SOUND→WORN→BROKEN) — and a broken
 * axe will not bite until it is mended against a whetstone, which resets the wear. A worn tool is no longer a
 * display-only label; it is a real maintenance need.
 *
 * <p>Proven end to end on the fell path: an axe worked long enough wears to WORN, then BROKEN; a broken axe
 * refuses to fell; a mended axe fells again. Skips gracefully without Docker.
 */
@SpringBootTest
class ToolWearIntegrationTest {

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

    private String axeCondition(UUID chronicle) {
        return jdbc.queryForObject("SELECT i.condition_state FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE w.current_owner_id=? AND i.item_key='stone_axe'", String.class, chronicle);
    }

    /** Seed the axe's wear counter so a single fell crosses a threshold, without felling a whole forest first. */
    private void setAxeUses(UUID chronicle, int uses) {
        jdbc.update("UPDATE item_instance SET use_count=? WHERE object_id IN " +
                "(SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=? AND i.item_key='stone_axe')",
                uses, chronicle);
    }

    @Test
    void anAxeWearsWithFellingBreaksAndFellsAgainOnceMended() {
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
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", now, "TEST_SEED");
        assertEquals("SOUND", axeCondition(chronicle), "a fresh axe starts sound");

        // The edge holds for a while, then shows wear, then finally gives way — but every fell up to that point
        // still lands (a worn axe still bites). The wear counter is seeded so one fell crosses each threshold; the
        // stepping itself is fellTree's real logic. Fewer real fells keeps the test off a whole forest of trees.
        setAxeUses(chronicle, 7);
        assertEquals("SUCCEEDED", items.fellTree(chronicle, chunk, now)[0], "a sound axe fells and takes the wear");
        assertEquals("WORN", axeCondition(chronicle), "worked past the first threshold, the edge shows wear (#220)");

        setAxeUses(chronicle, 15);
        assertEquals("SUCCEEDED", items.fellTree(chronicle, chunk, now)[0], "a worn axe still bites for this fell");
        assertEquals("BROKEN", axeCondition(chronicle), "worked past the last threshold, the axe wears through to BROKEN (#220)");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM object_transition ot JOIN item_instance i ON i.object_id=ot.object_id " +
                        "WHERE i.item_key='stone_axe' AND ot.transition_type='TOOL_WORN'", Integer.class) >= 2,
                "the wear steps (to WORN, to BROKEN) must be kept in history");

        // A broken axe refuses to fell — it must be mended first.
        String[] broken = items.fellTree(chronicle, chunk, now);
        assertEquals("FAILED", broken[0], "a broken axe must not fell until it is mended (#220)");
        assertTrue(broken[1].toLowerCase().contains("mend") || broken[1].toLowerCase().contains("whetstone"),
                () -> "the failure must point to mending: " + broken[1]);

        // Mend it against a whetstone — the edge comes back and the wear resets.
        items.createCarriedItem(chronicle, "whetstone", "Whetstone", now, "TEST_SEED");
        String[] mended = items.repairNamedItem(chronicle, chunk, "sharpen the stone axe", now);
        assertEquals("SUCCEEDED", mended[0], () -> "honing a broken axe must restore it: " + mended[1]);
        assertEquals("WORN", axeCondition(chronicle), "a honed broken axe comes back to WORN, ready to work again");

        // The mended axe fells once more.
        assertEquals("SUCCEEDED", items.fellTree(chronicle, chunk, now)[0], "a mended axe must fell again (#220)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
