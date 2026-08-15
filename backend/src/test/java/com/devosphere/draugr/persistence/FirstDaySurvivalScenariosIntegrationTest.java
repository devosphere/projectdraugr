package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First-day multi-biome survival scenario regressions (M1 #129, EPIC #123). Boots the real Spring
 * context against a PostgreSQL container and drives each scenario through the authoritative action
 * pipeline ({@link ChronicleActionService#resolve}), asserting that a careful Chronicle survives a
 * realistic first day from an approved start while a hostile boundary stays avoidable — and that the
 * world remains persistence- and Auditor-consistent throughout. Where dr0132 pins the survival CHAINS
 * as reachable data, this proves the SCENARIOS play out end to end.
 *
 * The container starts after a Docker-availability assumption, so the class skips gracefully on a
 * Docker-less machine and never fails a local {@code mvn test}; it runs for real in CI.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirstDaySurvivalScenariosIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the survival-scenario integration test");
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
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /**
     * #129 scenario 1 — Forest rain start beside a hostile predator boundary.
     * A careful Chronicle observes its surroundings, builds the first-day survival kit (fire, a woven
     * carrier), marks an escape route, and — crucially — is never FORCED to fight the seeded predator
     * pack: it survives the day, the pack is left untouched, and the world stays Auditor-consistent.
     */
    @Test
    @Order(1)
    void carefulForestArrivalSurvivesFirstDayBesideAHostileBoundary() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant now = ticks.current().simulatedAt();

        // Place the arrival in a temperate-forest chunk and seed a hostile predator pack there, so
        // "observe the signs, then avoid the fight" is a genuine choice rather than a forced wall.
        UUID forest = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(forest, "the approved world must contain a temperate-forest chunk");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", forest, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, forest);
        Timestamp ts = Timestamp.from(now);
        UUID lair = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dire wolf pack ground',?)", lair, forest);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Dire wolf pack ground',400)", lair, worldId, forest);
        UUID pack = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'FORAGING',?)", pack, lair, ts);

        // Observe — a deliberate look must return a perception frame that locates the Chronicle in the
        // forest and carries a readable body snapshot, and must not itself be fatal.
        ChronicleActionService.ActionResult look = actions.resolve(
                "I stop and look carefully around me, reading the ground and the treeline for anything nearby.");
        assertNotNull(look.frame(), "a resolved action must return a perception frame");
        assertEquals("TEMPERATE_FOREST", look.frame().location().biome(), "the frame must locate the arrival in the forest");
        assertNotNull(look.frame().physiology(), "the frame must carry the body HUD snapshot");
        assertFalse(look.died(), "observing must never be fatal");

        // The careful first-day survival battery. Materials the forest floor would provide are seeded so
        // the build/craft paths have their inputs; each action must resolve through the full tick without
        // a persistence error (outcomes vary with capability/RNG, exactly as in the full-tick regression).
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        String[] firstDay = {
                "I gather dry branches from beneath the trees.",
                "I gather plant fiber from the growth around me.",
                "I build a stone fire pit.",
                "I light a fire.",
                "I add a branch to the fire.",
                "I weave a basket from the plant fiber.",
                "I look for clean water nearby.",
                "I mark my escape route back toward open ground.",
                "I rest for 30 minutes."
        };
        for (String act : firstDay) {
            assertDoesNotThrow(() -> actions.resolve(act), "a first-day survival action must resolve without a persistence error: " + act);
        }

        // The decisive #129 promise: a careful first day is survivable. The Chronicle is still living and
        // is the same life that awoke.
        assertNotNull(chronicles.active(), "a careful Chronicle must survive its first day beside the boundary (#129)");
        assertEquals(chronicle, chronicles.active().id(), "the survivor must be the Chronicle that awoke");

        // Avoidance, not extermination: the Chronicle never confronted the pack, so the pack must still be
        // there — proving the hostile boundary is something to route around, never an auto-resolved fight.
        // The count may drift by natural wildlife simulation, but the pack is never wiped out by inaction.
        Integer packCount = jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pack);
        assertTrue(packCount != null && packCount >= 1, "avoiding the boundary must leave the predator pack alive, not exterminated (#129)");

        // Persistence + audit: the whole scenario leaves the world Auditor-consistent.
        PersistentStateAuditor.AuditReport report = auditor.inspect();
        assertTrue(report.consistent(), () -> "the world must stay consistent after the first-day survival scenario: " + report.violations());
    }
}
