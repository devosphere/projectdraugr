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
 * Dressed stone and dry ashlar (EPIC #180 / #183 masonry). Rough coursing stone beds into a wall on mortar; the finer
 * craft is ashlar — each block dressed square so the stones fit face to face and need no mortar at all. It is more
 * work (every block dressed by hand) but wants no lime, the mason's alternative to the mortared rubble course.
 *
 * <p>Proven through the real router: coursing stones dress into squared ashlar blocks, and four blocks lay into a dry
 * ashlar course with no mortar present at all. Skips gracefully without Docker.
 */
@SpringBootTest
class AshlarMasonryIntegrationTest {

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

    private int count(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void coursingStoneDressesToAshlarAndLaysDryWithoutMortar() {
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
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED"); // a striking tool for the chisel work

        // Dress four coursing stones into squared ashlar blocks — through the real router.
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "construction_stone", "Medium construction stone", now, "TEST_SEED");
        for (int i = 0; i < 4; i++) {
            String[] dress = items.runProcess(chronicle, chunk, "dress an ashlar block", now);
            assertEquals("SUCCEEDED", dress[0], () -> "dressing an ashlar block must succeed through the router: " + dress[1]);
        }
        assertEquals(4, count(chronicle, "ashlar_block"), "dressing four coursing stones must yield four ashlar blocks");

        // Lay them into a dry ashlar course — with NO mortar of any kind present, proving the fit alone holds it.
        assertEquals(0, count(chronicle, "mortar_mix"), "no ash mortar present");
        assertEquals(0, count(chronicle, "lime_mortar"), "no lime mortar present");
        String[] lay = items.runProcess(chronicle, chunk, "lay an ashlar course", now);
        assertEquals("SUCCEEDED", lay[0], () -> "a dry ashlar course must lay with no mortar at all: " + lay[1]);
        assertTrue(items.hasAtLeast(chronicle, "ashlar_course", 1), "laying must yield a dry ashlar course");
        assertEquals(0, count(chronicle, "ashlar_block"), "the four blocks must be consumed into the course");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
