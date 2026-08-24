package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lead and the fishing sinker (EPIC #180 heavy industry / #188 gold, silver, lead). Lead is useless for an edge but
 * dense and low-melting — the sinker metal. This gives it its own chain (mine galena → smelt → cast sinkers) and a
 * distinctive use: a lead weight on a hand-line carries the baited hook down to the deeper fish and holds it in the
 * current, so a weighted line catches more than a bare one.
 *
 * <p>Proven: lead ore smelts and casts to sinkers through the real router, and over a fixed battery of casts a
 * weighted line lands more fish than an unweighted one. Skips gracefully without Docker.
 */
@SpringBootTest
class LeadSinkerIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Cast a line over a fixed battery and count the takes (fish success is deterministic in the action id). */
    private int catches(UUID chronicle, UUID chunk, Instant now, List<UUID> actionIds) {
        int taken = 0;
        for (UUID action : actionIds) {
            if ("SUCCEEDED".equals(wildlife.fish(chronicle, chunk, action, now, "fish with a line and hook").outcome())) taken++;
        }
        return taken;
    }

    @Test
    void aLeadSinkerWeightsALineToCatchMoreFish() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        // Wetland water, where the fish are.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have wetland water to fish");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        items.createCarriedItem(chronicle, "bone_fish_hook", "Bone fish hook", now, "TEST_SEED");
        java.util.Random rnd = new java.util.Random(6607);
        List<UUID> actionIds = new ArrayList<>();
        for (int i = 0; i < 140; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // A bare, unweighted line: a baseline of how often it takes.
        int bareLine = catches(chronicle, chunk, now, actionIds);
        assertTrue(bareLine > 0, "an unweighted line must land some fish (else the test proves nothing)");

        // Mine, smelt, and cast lead into sinkers — through the real router.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);
        items.createCarriedItem(chronicle, "lead_ore", "Lead ore", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "lead_ore", "Lead ore", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        String[] smelt = items.runProcess(chronicle, chunk, "smelt the lead ore", now);
        assertEquals("SUCCEEDED", smelt[0], () -> "smelting lead ore must succeed: " + smelt[1]);
        assertTrue(items.hasAtLeast(chronicle, "lead_ingot", 1), "smelting must yield a lead ingot");
        String[] cast = items.runProcess(chronicle, chunk, "cast a lead sinker", now);
        assertEquals("SUCCEEDED", cast[0], () -> "casting lead sinkers must succeed: " + cast[1]);
        assertTrue(items.hasAtLeast(chronicle, "lead_sinker", 1), "casting must yield lead sinkers");

        // The same line, now weighted with a lead sinker: it takes more of the same battery.
        int weightedLine = catches(chronicle, chunk, now, actionIds);

        assertTrue(weightedLine > bareLine,
                () -> "a lead-weighted line must land more fish than a bare one: weighted=" + weightedLine + " bare=" + bareLine + " (#188)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
