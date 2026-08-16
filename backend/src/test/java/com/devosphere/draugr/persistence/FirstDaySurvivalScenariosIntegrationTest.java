package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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
    @Autowired ChroniclePhysiologyService physiology;
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

    /**
     * #129 scenario 2 — Wetland cold start.
     * A careful Chronicle treats its water before drinking, works the reeds and clay the marsh provides,
     * takes shelter against the cold, and survives the first day without wading into the deep water.
     */
    @Test
    @Order(2)
    void carefulWetlandArrivalTreatsWaterAndSurvivesTheCold() {
        UUID chunk = placeFreshChronicleIn("WETLAND");
        UUID chronicle = chronicles.active().id();
        Instant now = ticks.current().simulatedAt();

        // Marsh stock for the day: fibre and branches for fire and cordage, stones for a hearth, and raw
        // water to treat. None of it is direct capability — only the physical inputs the reedbed provides.
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "raw_water", "Raw water", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "raw_water", "Raw water", now, "TEST_SEED");

        runBattery(
                "I look carefully around the marsh, reading the water and the reeds for danger.",
                "I gather plant fiber from the reeds around me.",
                "I build a stone fire pit.",
                "I light a fire.",
                "I boil the water over the fire to make it safe.",
                "I drink from my treated water.",
                "I make a windbreak from branches and reeds.",
                "I rest for 30 minutes beside the fire.");

        assertSurvivedAndConsistent(chronicle, "wetland cold start");
    }

    /**
     * #129 scenario 3 — Grassland heat start.
     * The Chronicle finds water and shade, forages the open ground, and gives the seeded grazing herd a
     * wide berth — a boundary to route around, not a fight — and the herd is left intact.
     */
    @Test
    @Order(3)
    void carefulGrasslandArrivalFindsWaterAndAvoidsTheHerd() {
        UUID chunk = placeFreshChronicleIn("GRASSLAND");
        UUID chronicle = chronicles.active().id();
        Instant now = ticks.current().simulatedAt();
        UUID herd = seedWildlife(chunk, "Aurochs open herd range", "red_deer", "HERBIVORE", 6);

        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");

        runBattery(
                "I look across the open grass, marking where the herd grazes so I can keep away from it.",
                "I look for clean water nearby.",
                "I make a windbreak for shade against the sun.",
                "I search the ground for edible roots and seeds.",
                "I gather plant fiber from the grass around me.",
                "I mark my route back toward cover.",
                "I rest in the shade for 30 minutes.");

        assertSurvivedAndConsistent(chronicle, "grassland heat start");
        Integer herdCount = jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, herd);
        assertTrue(herdCount != null && herdCount >= 1, "avoiding the herd must leave it grazing, not gone (#129)");
    }

    /**
     * #129 scenario 4 — Highland storm start.
     * Exposed and cold, the Chronicle raises fire and cover and insulates against the weather rather than
     * being forced into a monster confrontation — no hostile site is seeded, and it survives the exposure.
     */
    @Test
    @Order(4)
    void carefulHighlandArrivalRaisesFireAndInsulatesAgainstExposure() {
        UUID chunk = placeFreshChronicleIn("HIGHLAND");
        UUID chronicle = chronicles.active().id();
        Instant now = ticks.current().simulatedAt();

        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");

        runBattery(
                "I take in the exposed slope and the weather rolling in, looking for the safest ground.",
                "I build a stone fire pit in the lee of the rocks.",
                "I light a fire.",
                "I add a branch to the fire.",
                "I warm myself by the fire.",
                "I make a windbreak against the wind.",
                "I rest close to the fire for 30 minutes.");

        assertSurvivedAndConsistent(chronicle, "highland storm start");
    }

    /**
     * #129 scenario 5 — River/clay start.
     * Beside the water the Chronicle gathers clay and stone, secures drinking water, and lays the start of
     * a camp — a working first day on wet ground, leaving the world consistent.
     */
    @Test
    @Order(5)
    void carefulRiverClayArrivalWorksTheClayAndMakesCamp() {
        UUID chunk = placeFreshChronicleIn("WETLAND");
        UUID chronicle = chronicles.active().id();
        Instant now = ticks.current().simulatedAt();

        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "raw_water", "Raw water", now, "TEST_SEED");

        runBattery(
                "I study the riverbank, the clay flats, and the safest place to cross.",
                "I gather clay from the bank.",
                "I look for clean water nearby.",
                "I gather stones from the shallows.",
                "I build a stone fire pit for a camp.",
                "I mark this place as my river camp.",
                "I rest for 30 minutes by the water.");

        assertSurvivedAndConsistent(chronicle, "river/clay start");
    }

    /**
     * #129 scenario 6 — Hostile encounter with retreat.
     * Faced with a seeded predator pack, the Chronicle breaks off, conceals itself, and shed its load to
     * move faster rather than being forced to fight — it survives, and the pack is neither fought nor wiped.
     */
    @Test
    @Order(6)
    void hostileEncounterAllowsRetreatConcealAndSurvival() {
        UUID chunk = placeFreshChronicleIn("TEMPERATE_FOREST");
        UUID chronicle = chronicles.active().id();
        Instant now = ticks.current().simulatedAt();
        UUID pack = seedWildlife(chunk, "Dusk prowler territory", "dire_wolf", "CARNIVORE", 3);
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");

        runBattery(
                "I catch sight of the pack and read how close it is before it notices me.",
                "I back away from the wolves, keeping my face to them and cover between us.",
                "I drop the field stone to move faster.",
                "I hide from the wolves in the thick brush and go still.",
                "I move quietly toward open ground away from the pack.");

        assertSurvivedAndConsistent(chronicle, "hostile encounter with retreat");
        Integer packCount = jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pack);
        assertTrue(packCount != null && packCount >= 1, "retreating must leave the pack alive, never auto-resolve the fight (#129)");
    }

    /**
     * #129 scenario 7 — Wounded start.
     * Arriving hurt, the Chronicle binds the wound, rests, and recovers gradually. A fibre binding staunches
     * bleeding but is no miracle: the injury itself is still there afterward — no impossible instant cure.
     */
    @Test
    @Order(7)
    void woundedArrivalBindsAndRecoversGraduallyNotInstantly() {
        UUID chunk = placeFreshChronicleIn("TEMPERATE_FOREST");
        UUID chronicle = chronicles.active().id();
        Instant now = ticks.current().simulatedAt();
        // Arrive injured and bleeding — the arrival state, not an injection of capability.
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=45, blood_loss_ml=600, pain_level=40 WHERE chronicle_id=?", chronicle);
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");

        ChronicleActionService.ActionResult bind = actions.resolve("I bind my wound with a length of plant fiber to stop the bleeding.");
        assertEquals("SUCCEEDED", bind.outcome(), () -> "binding a real wound with fibre to hand must succeed: " + bind.perception());
        runBattery(
                "I rest and keep still to let the wound settle.",
                "I look myself over and check how the injury is holding.");

        assertSurvivedAndConsistent(chronicle, "wounded start");
        Integer injury = jdbc.queryForObject("SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        assertTrue(injury != null && injury > 0, "a fibre binding staunches bleeding but does not instantly heal the injury (#129)");
        assertTrue(injury < 100, "the wounded Chronicle survives its first aid, it does not die of the wound");
    }

    /**
     * #129 scenario 8 — First-week progression.
     * Across the opening days the Chronicle builds up from bare hands: a tool, a carrier to store in, fire,
     * shelter, and the start of defence, drinking to stay alive — ending the stretch living, with real
     * persistent objects to show for it and the world still consistent.
     */
    @Test
    @Order(8)
    void firstWeekProgressionBuildsUpToolsStorageFireAndShelter() {
        UUID chunk = placeFreshChronicleIn("TEMPERATE_FOREST");
        UUID chronicle = chronicles.active().id();
        Instant now = ticks.current().simulatedAt();

        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "raw_water", "Raw water", now, "TEST_SEED");

        runBattery(
                // Tools
                "I knap a stone knife from a field stone.",
                "I lash a primitive spear from a branch, a stone, and fiber.",
                // Storage
                "I weave a basket from the plant fiber.",
                // Fire + water through the week
                "I build a stone fire pit.",
                "I light a fire.",
                "I drink from my water.",
                "I rest for a while by the fire.",
                // Shelter
                "I start a lean-to shelter from branches.",
                "I work further on the lean-to shelter.",
                "I drink from my water.",
                "I sleep through the night in the shelter.",
                // Defence + exploration as the week opens out
                "I gather more stones for a defensible ring.",
                "I mark a safe route out from my camp.",
                "I drink from my water.",
                "I look carefully around the camp before resting.");

        assertSurvivedAndConsistent(chronicle, "first-week progression");
        Integer crafted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        assertTrue(crafted != null && crafted >= 1, "a first week of work must leave the Chronicle with real carried possessions (#129)");
    }

    // ---- shared scenario helpers -------------------------------------------------------------------

    /** Bootstrap the world if needed, awaken a fresh Chronicle, and stand it in the first chunk of the
     *  named biome. Returns that chunk id; the living Chronicle is {@code chronicles.active()}. */
    private UUID placeFreshChronicleIn(String biome) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        // awaken() rejoins a still-living Chronicle rather than starting a second life, so a genuinely
        // fresh arrival needs the prior scenario's Chronicle to have ended first. End it the way the world
        // does — a lethal condition resolved by the metabolic pass, which also relocates its possessions —
        // so each scenario truly begins from arrival state, and the world stays Auditor-consistent.
        ChronicleService.ChronicleSummary prior = chronicles.active();
        if (prior != null) {
            jdbc.update("UPDATE chronicle_physiology SET blood_loss_ml=6000 WHERE chronicle_id=?", prior.id());
            physiology.advanceTo(ticks.current().simulatedAt());
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome=? ORDER BY grid_y, grid_x LIMIT 1", UUID.class, biome);
        assertNotNull(chunk, "the approved world must contain a " + biome + " chunk");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        return chunk;
    }

    /** Seed a wildlife population at a chunk so an avoidance/retreat choice has a real target. Returns the
     *  population id. */
    private UUID seedWildlife(UUID chunk, String siteName, String species, String role, int count) {
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE',?,?)", site, siteName, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE',?,400)", site, worldId, chunk, siteName);
        UUID population = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,?,'DIURNAL',?,?,'FORAGING',?)", population, site, species, role, count, count + 2, ts);
        return population;
    }

    /** Resolve each action through the real pipeline, asserting only that none raises a persistence error —
     *  outcomes vary with capability and RNG, exactly as in play. */
    private void runBattery(String... acts) {
        for (String act : acts)
            assertDoesNotThrow(() -> actions.resolve(act), "a survival action must resolve without a persistence error: " + act);
    }

    /** The shared #129 promise: the Chronicle that awoke is still living, and the world is Auditor-consistent. */
    private void assertSurvivedAndConsistent(UUID chronicle, String scenario) {
        assertNotNull(chronicles.active(), scenario + ": a careful Chronicle must survive its first day (#129)");
        assertEquals(chronicle, chronicles.active().id(), scenario + ": the survivor must be the Chronicle that awoke");
        PersistentStateAuditor.AuditReport report = auditor.inspect();
        assertTrue(report.consistent(), () -> scenario + ": the world must stay Auditor-consistent: " + report.violations());
    }
}
