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

        // Bare ground reports nothing STANDING on it. What grows on it is another matter and is reported (#224):
        // a brake of blackberry is a fact about this chunk that anyone standing here can see, and it is the thing
        // that most distinguishes one piece of open country from another. The rule being guarded is unchanged —
        // nothing from beyond the chunk — so the check is narrowed to the features that could come from elsewhere,
        // rather than loosened.
        assertTrue(seen.features().stream().noneMatch(f -> f.kind() != null && !f.kind().startsWith("FLORA:")),
            () -> "bare ground must report nothing built or sited, or the payload is reading beyond the chunk: "
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
        assertTrue(seen.surroundings().isEmpty(), "and nothing beyond the rock can be seen from inside it");
    }

    /**
     * #232's visible-nearby tier: by day a standing person sees what kind of country lies next door — and only that.
     * The neighbours' sites stay unreported, and in the dark nothing beyond this ground is seen at all.
     */
    @Test
    void byDayTheNextGroundIsSeenButNotWhatStandsOnIt() {
        world();
        UUID ground = plainGround();
        assertNotNull(ground);
        chronicleOn(ground);

        java.util.List<String> neighbours = jdbc.queryForList(
            "SELECT DISTINCT n.biome FROM world_chunk c JOIN world_chunk n ON n.world_id=c.world_id " +
            "AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1 WHERE c.id=? ORDER BY 1", String.class, ground);

        var noon = visual.active(Instant.parse("2026-06-15T12:00:00Z"));
        assertTrue(noon.lit(), "open ground at noon is lit");
        assertEquals(neighbours, noon.surroundings(), "by day the kinds of the four neighbouring grounds are seen");
        assertTrue(noon.features().stream().noneMatch(f -> f.kind() != null && !f.kind().startsWith("FLORA:")),
            () -> "and nothing standing on them is reported here — what grows on THIS ground is this ground's own "
                + "(#224), but no neighbour's site or structure may appear: " + noon.features());

        var midnight = visual.active(Instant.parse("2026-06-15T00:30:00Z"));
        if (!midnight.lit())
            assertTrue(midnight.surroundings().isEmpty(), "in the dark nothing beyond this ground can be seen");
        assertNotEquals(noon.fingerprint(), midnight.fingerprint(), "what is seen changed, so the fingerprint moves");
    }

    /**
     * What grows here is part of what this place looks like (#224).
     *
     * <p>A brake of blackberry, a bed of nettle, a stand of reed is the thing that most distinguishes one piece of
     * open country from another to look at, and the examination has named such stands within reach since it was
     * written. The visual context reported sites and finished builds and nothing else, so the scene for a meadow
     * thick with bramble was the same scene as for bare grass — and thirty-eight backdrops were gated on exactly
     * this, waiting for a place to be able to carry what grows on it.
     *
     * <p>A stand worked down to nothing stops counting: that is bare ground again, not scenery.
     */
    @Test
    void whatGrowsHereIsPartOfWhatThePlaceLooksLike() {
        world();

        UUID ground = plainGround();
        assertNotNull(ground);
        chronicleOn(ground);
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=?", ground);

        var bare = visual.active(ticks.current().simulatedAt());
        String bareKey = com.devosphere.draugr.world.BackdropResolver.resolve(bare).key();
        assertTrue(!bareKey.startsWith("flora."), () -> "ground with nothing growing must not resolve to a stand: " + bareKey);

        // A stand of something the catalogue says grows on this ground.
        String key = jdbc.queryForObject(
            "SELECT fd.flora_key FROM flora_definition fd JOIN world_chunk c ON c.id=? " +
            " WHERE fd.organism_type <> 'TREE' AND fd.biome_affinity ILIKE '%' || c.biome || '%' ORDER BY fd.flora_key LIMIT 1",
            String.class, ground);
        Assumptions.assumeTrue(key != null, "nothing in the catalogue grows on this kind of ground");
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity, established_at) VALUES (?,?,5,5,now())", ground, key);

        try {
            var withStand = visual.active(ticks.current().simulatedAt());
            assertTrue(withStand.features().stream().anyMatch(f -> ("FLORA:" + key).equals(f.kind())),
                () -> "the place must report what is growing on it: " + withStand.features());
            assertEquals("flora." + key.replace('_', '-'), com.devosphere.draugr.world.BackdropResolver.resolve(withStand).key(),
                "the fullest stand decides the scene, ranked over the bare ground it grows on");
            assertNotEquals(bare.fingerprint(), withStand.fingerprint(),
                "what is seen changed, so the fingerprint must move with it");

            // Worked down to nothing: bare ground again.
            jdbc.update("UPDATE chunk_flora SET quantity=0 WHERE chunk_id=? AND flora_key=?", ground, key);
            var cleared = visual.active(ticks.current().simulatedAt());
            assertTrue(cleared.features().stream().noneMatch(f -> ("FLORA:" + key).equals(f.kind())),
                () -> "a stand worked down to nothing is bare ground, not scenery: " + cleared.features());
            assertEquals(bareKey, com.devosphere.draugr.world.BackdropResolver.resolve(cleared).key(),
                "and the scene goes back to exactly what it was before anything grew there");
        } finally {
            jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key=?", ground, key);
        }
    }
}
