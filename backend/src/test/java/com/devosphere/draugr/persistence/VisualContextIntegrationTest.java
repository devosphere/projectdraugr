package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.VisualContextService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Chronicle can see, and nothing they could not (#224/#232).
 *
 * <p>The dangerous failure for a visual-context payload is not that it is wrong — it is that it is <b>too
 * right</b>. The Overseer's marker plan knows where every lair, seam and roost in the world is, and a payload
 * built from it would let a player learn by watching their own screen that a monster lair sits two chunks east.
 * That is knowledge the Chronicle has no way to have, and no amount of careful presentation puts it back.
 *
 * <p>So the load-bearing assertion here is a negative one: a site that exists on other ground must not appear in
 * the payload for this ground. The rest — fingerprint stability, features appearing when built and going when
 * destroyed — is what makes the payload useful once it is safe.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class VisualContextIntegrationTest {

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
    @Autowired VisualContextService visual;
    @Autowired SimulationTickService ticks;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID chronicleOn(UUID chunk) {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        return summary.id();
    }

    private UUID plainGround() {
        return jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    }

    /** The payload must describe the ground underfoot at all. */
    @Test
    void itDescribesTheGroundTheChronicleStandsOn() {
        world();
        UUID ground = plainGround();
        assertNotNull(ground, "the world must have open ground with nothing on it");
        chronicleOn(ground);

        var seen = visual.active(ticks.current().simulatedAt());
        assertNotNull(seen, "a living Chronicle standing somewhere must be able to see it");
        assertEquals(VisualContextService.VERSION, seen.version(), "the payload must declare its contract version");
        assertEquals("GRASSLAND", seen.biome(), "it must report the ground actually underfoot");
        assertNotNull(seen.timeOfDay());
        assertNotNull(seen.season());
        assertNotNull(seen.weather());
        assertTrue(seen.fingerprint() != null && !seen.fingerprint().isBlank(), "every reading is fingerprinted");
    }

    /**
     * The load-bearing rule. A site on other ground must not appear here — the Overseer knows where every lair in
     * the world is, and this payload must not be a way to read the Overseer's map.
     */
    @Test
    void itNeverReportsSomewhereElsesSites() {
        world();
        UUID ground = plainGround();
        assertNotNull(ground);
        chronicleOn(ground);

        // A lair exists in this world, on ground the Chronicle is not standing on.
        String remoteSite = jdbc.query(
            "SELECT es.site_kind FROM ecology_site es WHERE es.chunk_id <> ? AND es.site_category='MONSTER' LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, ground);
        Assumptions.assumeTrue(remoteSite != null, "this world seeded no monster lair to be discreet about");

        var seen = visual.active(ticks.current().simulatedAt());
        assertNotNull(seen);
        boolean leaked = seen.features().stream().anyMatch(f -> remoteSite.equals(f.name()));
        assertTrue(!leaked,
            () -> "the payload named '" + remoteSite + "', which stands on other ground — a player could learn "
                + "from their own screen where a lair is, which the Chronicle has no way to know: " + seen.features());

        assertTrue(seen.features().isEmpty(),
            () -> "bare ground must report no features at all, or the payload is reading beyond the chunk: "
                + seen.features());
    }

    /** Unchanged world, unchanged fingerprint — that is what makes it worth carrying. */
    @Test
    void anUnchangedPlaceKeepsItsFingerprint() {
        world();
        UUID ground = plainGround();
        assertNotNull(ground);
        chronicleOn(ground);
        Instant at = ticks.current().simulatedAt();

        assertEquals(visual.active(at).fingerprint(), visual.active(at).fingerprint(),
            "the same place in the same state must read the same across calls");
    }

    /** And a thing built here changes both what is seen and the fingerprint. */
    @Test
    void raisingAndLosingAStructureChangesWhatIsSeen() {
        world();
        UUID ground = plainGround();
        assertNotNull(ground);
        chronicleOn(ground);
        Instant at = ticks.current().simulatedAt();

        String before = visual.active(at).fingerprint();

        UUID built = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Lean-to',?)",
            built, ground);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) " +
            "VALUES (?,'LEAN_TO','COMPLETED',100,?)", built, Timestamp.from(at));

        var withShelter = visual.active(at);
        assertTrue(withShelter.features().stream().anyMatch(f -> f.kind().equals("BUILT:LEAN_TO")),
            () -> "a shelter standing here must be visible from here: " + withShelter.features());
        assertNotEquals(before, withShelter.fingerprint(), "raising a shelter changes what the place looks like");

        // Destroyed is gone: the fingerprint must return, because the place is as it was.
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_cause='TEST', " +
            "current_location_id=NULL, destroyed_location_id=? WHERE id=?", Timestamp.from(at), ground, built);
        var after = visual.active(at);
        assertTrue(after.features().stream().noneMatch(f -> f.kind().startsWith("BUILT:")),
            () -> "a destroyed structure must stop being scenery: " + after.features());
        assertEquals(before, after.fingerprint(),
            "with the shelter gone the place is as it was, so it must read as it did");
    }

    /** Inside the rock the sky is irrelevant — a cave is dark at noon unless something is burning. */
    @Test
    void aCaveIsDarkWhateverTheSkyIsDoing() {
        world();
        UUID cave = jdbc.query("SELECT id FROM world_chunk WHERE biome='CAVE_INTERIOR' LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        Assumptions.assumeTrue(cave != null, "this world carved no cave interior");
        chronicleOn(cave);

        // Midday, explicitly — the hour must not be what saves it.
        Instant noon = Instant.parse("2026-06-15T12:00:00Z");
        var seen = visual.active(noon);
        assertNotNull(seen);
        assertEquals("DAY", seen.timeOfDay(), "the sky outside is at midday");
        assertTrue(!seen.lit(), "and inside the rock it is dark anyway — that is what a cave is");
    }
}
