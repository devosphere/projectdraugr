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
 * Tool-wear-in-crafting regression (EPIC #215, story #220). Tool wear began with the axe on the fell path (#299);
 * this extends it to the shared crafting core: a bladed process (any CUTTING/STRIKING/AXE material process) now
 * wears the tool it turns on, and a broken tool is refused until it is mended. The knife that stitches, the
 * hammerstone that knaps, the axe that hews — all now dull with the work, the same way, on one code path.
 *
 * <p>Proven through a real CUTTING process (sewing a hide sack): a knife worked past the threshold wears through to
 * BROKEN; a broken knife refuses the work (and consumes none of the stock); a whetstone-honed knife stitches
 * again. Skips gracefully without Docker.
 */
@SpringBootTest
class ToolWearCraftingIntegrationTest {

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

    private String knifeCondition(UUID chronicle) {
        return jdbc.queryForObject("SELECT i.condition_state FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE w.current_owner_id=? AND i.item_key='stone_knife'", String.class, chronicle);
    }

    private void seedKnifeUses(UUID chronicle, int uses) {
        jdbc.update("UPDATE item_instance SET use_count=? WHERE object_id IN " +
                "(SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=? AND i.item_key='stone_knife')",
                uses, chronicle);
    }

    @Test
    void aBladedProcessWearsItsToolAndRefusesABrokenOne() {
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
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "tanned_leather", "Tanned leather", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "leather_cord", "Leather cord", now, "TEST_SEED");

        // Worked to the edge of its life, one more sew wears the blade through to BROKEN — but that sew still lands.
        seedKnifeUses(chronicle, 15);
        String[] worn = items.executeProcess(chronicle, chunk, "sew_hide_sack", "sew a hide sack", now);
        assertEquals("SUCCEEDED", worn[0], () -> "a still-serviceable knife must complete the work: " + worn[1]);
        assertEquals("BROKEN", knifeCondition(chronicle), "a blade worked past the threshold wears through to BROKEN (#220)");

        // A broken knife refuses the work — and consumes none of the stock (the tool gate runs before inputs).
        items.createCarriedItem(chronicle, "tanned_leather", "Tanned leather", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "leather_cord", "Leather cord", now, "TEST_SEED");
        String[] refused = items.executeProcess(chronicle, chunk, "sew_hide_sack", "sew a hide sack", now);
        assertEquals("FAILED", refused[0], "a broken tool must refuse the work until it is mended (#220)");
        assertTrue(refused[1].toLowerCase().contains("mend") || refused[1].toLowerCase().contains("biting"),
                () -> "the failure must point to mending: " + refused[1]);
        assertTrue(items.hasAtLeast(chronicle, "tanned_leather", 1), "a refused process must not consume the stock");

        // Honed against a whetstone, the edge comes back and the wear resets — the knife stitches again.
        items.createCarriedItem(chronicle, "whetstone", "Whetstone", now, "TEST_SEED");
        String[] mended = items.repairNamedItem(chronicle, chunk, "sharpen the stone knife", now);
        assertEquals("SUCCEEDED", mended[0], () -> "honing a broken knife must restore it: " + mended[1]);
        assertEquals("WORN", knifeCondition(chronicle), "a honed broken knife comes back to WORN, ready to work again");
        assertEquals("SUCCEEDED", items.executeProcess(chronicle, chunk, "sew_hide_sack", "sew a hide sack", now)[0],
                "a mended knife must complete the work again (#220)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
