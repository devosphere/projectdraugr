package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.construction.ConstructionService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roof smoke-vent (EPIC #215, story #219 — hazard-emission mitigation; extends the #198 enclosed-fire smoke model).
 * Woodsmoke from an unvented fire in an enclosed shelter fouls the air and pushes a Chronicle toward illness. Until
 * now the only relief was possessing a smoke_hood item; a hut had no way to breathe. A hole cut through the roof
 * vents the smoke structurally — the built vent earns its terminal effect.
 *
 * <p>Proven deterministically: in an enclosed shelter with an unvented hearth, illness rises from a benign baseline;
 * cutting a smoke-vent (the real ConstructionService path) clears the air so the same span raises no illness. Skips
 * gracefully without Docker.
 */
@SpringBootTest
class SmokeVentIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired ConstructionService construction;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** From a benign baseline (illness 0, clean, dry, unhurt), advance {@code hours} and return the illness left —
     *  so the only illness pressure that can move is the enclosed-fire smoke (mirrors the refuse-sanitation test). */
    private int illnessAfter(UUID chronicle, Instant base, int hours) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0, hygiene_level=80, wetness_level=0, injury_severity=0, " +
                "core_temperature_c=37, hours_without_food=40, hours_without_water=5, energy_level=80, last_metabolic_update=? WHERE chronicle_id=?",
                Timestamp.from(base), chronicle);
        physiology.advanceTo(base.plus(Duration.ofHours(hours)));
        return jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    private void placeConstruction(UUID chunk, String kind, String name, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)", id, name, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,?,'COMPLETED',100,?,100)", id, kind, Timestamp.from(at));
        if ("STONE_FIRE_PIT".equals(kind))
            jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,200,?)", id, Timestamp.from(at));
    }

    @Test
    void anUnventedEnclosedHearthBreedsIllnessAndASmokeVentClearsIt() {
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
        Instant base = ticks.current().simulatedAt();

        // An enclosed hut with an unvented hearth burning inside it.
        placeConstruction(chunk, "WATTLE_AND_DAUB_HUT", "Wattle-and-daub hut", base);
        placeConstruction(chunk, "STONE_FIRE_PIT", "Stone fire pit", base);

        // Part 1 — the smoke fouls the air: illness rises from a benign baseline.
        int smoky = illnessAfter(chronicle, base, 24);
        assertTrue(smoky > 0, () -> "an unvented fire in an enclosed shelter must foul the air toward illness (#198/#219), got " + smoky);

        // Part 2 — cut a smoke-vent by the real build path (needs a blade + clay), and the air clears.
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", base, "TEST_SETUP");
        items.createCarriedItem(chronicle, "clay_lump", "Clay lump", base, "TEST_SETUP");
        String[] built = construction.buildSmokeVent(chronicle, chunk, base);
        assertEquals("SUCCEEDED", built[0], () -> "cutting a smoke-vent in a standing shelter must succeed: " + built[1]);

        int vented = illnessAfter(chronicle, base, 24);
        assertEquals(0, vented, () -> "a built smoke-vent must clear the smoke so the same span breeds no illness (smoky=" + smoky + ", vented=" + vented + ")");

        // And a smoke-vent cut where nothing stands to hold it is refused.
        UUID bare = jdbc.queryForObject("SELECT id FROM world_chunk WHERE id<>? ORDER BY grid_y, grid_x LIMIT 1", UUID.class, chunk);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", bare, chronicle);
        items.createCarriedItem(chronicle, "clay_lump", "Clay lump", base, "TEST_SETUP");
        String[] noShelter = construction.buildSmokeVent(chronicle, bare, base);
        assertEquals("FAILED", noShelter[0], () -> "a smoke-vent needs a roof to cut through: " + noShelter[1]);

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
