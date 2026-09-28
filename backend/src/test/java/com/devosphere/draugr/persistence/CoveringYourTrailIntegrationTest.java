package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covering your trail (#37) — the opposite of reading one.
 *
 * <p>Act eleven:
 *
 * <pre>
 *   cover my tracks  ->  TRACK / SUCCEEDED
 *   "You find a line of prints pressed into the softer ground."
 * </pre>
 *
 * <p>A hunter covering their trail is trying to leave <b>less</b> sign, and the world read sign instead — success
 * at the very thing the asker was trying to prevent. TRACK owns the word "tracks" outright, so every sentence
 * containing it became an act of reading them.
 *
 * <p>And there was a mechanic waiting for it. A fresh kill carried on the body adds 10 to the passive-encounter
 * chance — blood and scent on the wind, the reason carrying a carcass through predator ground makes you the bait.
 * Cooking, storing or caching it removes the draw, and a camp store removes it too: all of which need a camp, and
 * the risk is on the walk back. Brushing out the trail is what a person does out in the country, and it takes
 * most of the draw off for a few hours — never all of it, because blood carries and a predator with the scent
 * does not need the footprints.
 *
 * <p>Counted the way {@code BrandDeterrentIntegrationTest} counts a fire-brand: one fixed, seeded set of
 * encounters run for both cohorts, the pack reset to HUNTING before each roll. Skips without Docker.
 */
@SpringBootTest
class CoveringYourTrailIntegrationTest {

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
    @Autowired ChronicleActionService actions;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** The suite shares one clock, and everything here resolves actions that move it. Pinned and put back. */
    private Timestamp clockWas;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
    }

    @AfterEach
    void unpinTheClock() {
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
    }

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        // awaken() hands back the SAME Chronicle within a class, so the other method in here had already covered
        // its trail by the time this one asserted nobody had. The precondition belongs where the Chronicle is
        // got, not in whichever test happens to run first.
        jdbc.update("UPDATE chronicle SET trail_hidden_at=NULL WHERE id=?", summary.id());
        return summary.id();
    }

    @Test
    void coveringATrailIsNotReadingOne() {
        UUID chronicle = awaken();
        Instant now = ticks.current().simulatedAt();

        assertFalse(wildlife.trailRecentlyHidden(chronicle, now), "nobody has covered anything yet");

        ChronicleActionService.ActionResult covered = actions.resolve("cover my tracks");
        assertEquals("HIDE_TRAIL", covered.intent(),
            () -> "covering a trail is its own act, not an act of reading one: " + covered.perception());
        assertEquals("SUCCEEDED", covered.outcome(), () -> covered.perception());
        assertFalse(covered.perception().contains("You find"),
            () -> "and it must not report FINDING prints, which is what it used to do: " + covered.perception());
        assertTrue(wildlife.trailRecentlyHidden(chronicle, now), "the trail is now covered");

        // Reading a trail is untouched — the rule needed a hiding verb, and these have none.
        assertEquals("TRACK", actions.resolve("follow the tracks").intent(), "following a trail still reads it");
        assertEquals("TRACK", actions.resolve("look for tracks").intent(), "looking for sign still reads it");
        assertEquals("TRACK", actions.resolve("read the ground").intent(), "reading the ground still reads it");

        // And it wears off: a trail brushed out this morning is no use by this evening.
        assertFalse(wildlife.trailRecentlyHidden(chronicle, now.plus(java.time.Duration.ofHours(6))),
            "a covered trail does not stay covered for ever");
    }

    /** Count how many of a fixed set of encounters land, resetting the pack to HUNTING before each so every roll
     *  faces the same threat. The same id set is used for both cohorts. */
    private int ambushes(UUID chronicle, UUID chunk, UUID pack, Instant now, java.util.List<UUID> actionIds) {
        int hits = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE wildlife_population SET behavior_state='HUNTING' WHERE id=?", pack);
            if (wildlife.passiveEncounter(chronicle, chunk, action, now, "LOW") != null) hits++;
        }
        return hits;
    }

    @Test
    void aCoveredTrailDrawsLessOntoACarriedKill() {
        UUID chronicle = awaken();
        UUID chunk = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dusk prowler territory',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Dusk prowler territory',400)", site, worldId, chunk);
        UUID pack = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", pack, site, ts);

        // A raw kill on the back — the thing that draws them, and the reason to cover a trail at all.
        UUID kill = items.createCarriedItem(chronicle, "raw_game_meat", "Raw game meat", now, "TEST_SEED");
        jdbc.update("INSERT INTO food_preservation_state (object_id, preparation_kind, safe_until) VALUES (?,'RAW',?) " +
            "ON CONFLICT (object_id) DO UPDATE SET preparation_kind='RAW'", kill, Timestamp.from(now.plus(java.time.Duration.ofHours(18))));

        java.util.Random rnd = new java.util.Random(42);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        jdbc.update("UPDATE chronicle SET trail_hidden_at=NULL WHERE id=?", chronicle);
        int trailOpen = ambushes(chronicle, chunk, pack, now, actionIds);
        assertTrue(trailOpen > 0, "a HUNTING pack must reach a Chronicle carrying blood sometimes, or this proves nothing");

        jdbc.update("UPDATE chronicle SET trail_hidden_at=? WHERE id=?", ts, chronicle);
        int trailHidden = ambushes(chronicle, chunk, pack, now, actionIds);

        assertTrue(trailHidden < trailOpen,
            () -> "brushing out the trail must take some of the draw off a carried kill (hidden=" + trailHidden
                + ", open=" + trailOpen + ")");
        assertTrue(trailHidden > 0,
            () -> "but never all of it — blood carries, and a predator with the scent does not need the prints "
                + "(hidden=" + trailHidden + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
