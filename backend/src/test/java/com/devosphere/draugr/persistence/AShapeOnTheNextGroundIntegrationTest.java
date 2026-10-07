package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.VisualContextService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shape on the next ground (#224).
 *
 * <p>The last line of this story's required contract is a visible "ruin, or structure with direction/distance
 * band", and it was the one line left unbuilt: the tier above reported the lie of the land and stopped, with a
 * comment saying plainly that no neighbour's structures are ever read.
 *
 * <p><b>Why reporting one is safe, which is the only interesting question here.</b> The danger in this payload has
 * always been that it is built from the Overseer's plan, which knows where everything is — so a careless field
 * hands the player knowledge their Chronicle cannot have. A silhouette is the exception, and only in one precise
 * form: you cannot stand one chunk from a watchtower in daylight and fail to see that something is built there,
 * but which tower it is, what it was for and what is left inside it are invisible from that distance and are
 * exactly what makes it worth walking to. So a kind and a bearing cross; a name never does.
 *
 * <p>What this holds, with fixtures of its own rather than whatever genesis happened to place nearby:
 *
 * <ul>
 *   <li>a ruin raised on the ground to the south is reported as RUIN, south, ADJACENT;</li>
 *   <li>its <b>name appears nowhere in the payload</b> — asserted against the whole serialised response, not just
 *       the field it would be expected in, because a leak that mattered would be one nobody expected;</li>
 *   <li>a ruin two chunks away is <b>not</b> reported, so this is the one ring and not a marker lookup;</li>
 *   <li>the dark takes the skyline away, as it takes the lie of the land;</li>
 *   <li>a neighbour that is cave interior reports nothing, because there is no seeing into rock;</li>
 *   <li>and the fingerprint moves when a shape appears, which is what makes a backdrop notice.</li>
 * </ul>
 *
 * <p>Every fixture is removed again in {@code @AfterEach}: this suite shares one world across hundreds of tests,
 * and a ruin left standing on borrowed ground is another test's unexplained failure.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AShapeOnTheNextGroundIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    /** Sites this test raised, torn down afterwards so the shared world is handed back as it was found. */
    private final List<UUID> raised = new java.util.ArrayList<>();

    private static final Instant NOON = Instant.parse("2026-06-15T12:00:00Z");
    private static final Instant MIDNIGHT = Instant.parse("2026-06-15T00:30:00Z");
    private static final String SECRET_NAME = "Thornwick Signal Tower";

    @AfterEach
    void tearDownFixtures() {
        for (UUID site : raised) {
            jdbc.update("DELETE FROM ecology_site WHERE id=?", site);
            jdbc.update("DELETE FROM world_object WHERE id=?", site);
        }
        raised.clear();
    }

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /**
     * Open ground whose four neighbours are all plain — no ruin, no finished building, no village — so that a shape
     * reported afterwards is one this test raised and not one genesis happened to place. Without this the positive
     * assertion could pass on somebody else's watchtower and the negative one could never pass at all.
     */
    private UUID groundWithAnEmptySkyline() {
        return jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome <> 'CAVE_INTERIOR' AND c.biome <> 'OCEAN' " +
            "  AND EXISTS (SELECT 1 FROM world_chunk s WHERE s.world_id=c.world_id " +
            "               AND s.grid_x=c.grid_x AND s.grid_y=c.grid_y+1) " +
            "  AND NOT EXISTS (" +
            "     SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id " +
            "       AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1 AND (" +
            "         EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=n.id AND es.site_category='RUIN')" +
            "      OR EXISTS (SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "                  WHERE w.current_location_id=n.id AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE')" +
            "      OR EXISTS (SELECT 1 FROM native_settlement_site s2 JOIN world_object w2 ON w2.id=s2.object_id " +
            "                  WHERE w2.current_location_id=n.id AND w2.lifecycle_state='ACTIVE' AND s2.site_kind='VILLAGE')))" +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    }

    /** The chunk one step in a given grid offset from another, or null if the map ends there. */
    private UUID neighbour(UUID chunk, int dx, int dy) {
        return jdbc.query(
            "SELECT n.id FROM world_chunk c JOIN world_chunk n ON n.world_id=c.world_id " +
            "AND n.grid_x=c.grid_x+? AND n.grid_y=c.grid_y+? WHERE c.id=?",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null, dx, dy, chunk);
    }

    /**
     * Raise a named ruin on a chunk, exactly as genesis places one: an {@code ecology_site} of category RUIN.
     *
     * <p>The abundance is 1000 because that is what genesis writes for a ruin, and because the column's own CHECK
     * demands 1..1000 — a fixture that writes 0 is rejected. The first version of this test wrote 0, reasoning
     * that a ruin yields nothing, and it passed every pre-push check I had: the queries were all PREPAREd against
     * a real schema, which proves a statement parses and its columns exist and proves <b>nothing</b> about whether
     * a row will be accepted. An INSERT has to actually run — inside a transaction that is rolled back — before
     * it has been checked at all.
     */
    private void raiseRuin(UUID chunk, String name) {
        UUID site = UUID.randomUUID();
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id,lifecycle_state) " +
            "VALUES (?,'ECOLOGY_SITE',?,?,'ACTIVE')", site, name, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) " +
            "VALUES (?,?,?,'RUIN',?,1000)", site, world, chunk, name);
        raised.add(site);
    }

    private UUID standOn(UUID chunk) {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        return summary.id();
    }

    @Test
    void aRuinOnTheNextGroundIsSeenByKindAndBearingAndNeverByName() {
        world();
        UUID here = groundWithAnEmptySkyline();
        Assumptions.assumeTrue(here != null, "this world has no open ground with an empty skyline to borrow");
        standOn(here);

        var before = visual.active(NOON);
        Assumptions.assumeTrue(before.lit(), "the ground chosen must be lit at noon for anything to be seen");
        assertTrue(before.landmarks().isEmpty(),
            () -> "the fixture requires an empty skyline to start with: " + before.landmarks());

        // North is grid_y - 1, so grid_y + 1 is the ground to the SOUTH.
        UUID southward = neighbour(here, 0, 1);
        assertNotNull(southward, "the chosen ground was required to have a neighbour to the south");
        raiseRuin(southward, SECRET_NAME);

        var seen = visual.active(NOON);
        assertEquals(1, seen.landmarks().size(), () -> "one ruin was raised, so one shape is seen: " + seen.landmarks());
        var shape = seen.landmarks().get(0);
        assertEquals("RUIN", shape.kind(), "a ruin reads as a ruin");
        assertEquals("south", shape.direction(), "and it is to the south, because north is grid_y - 1");
        assertEquals("ADJACENT", shape.distance(), "one step away is the only band this tier claims");

        // THE LOAD-BEARING NEGATIVE. Not "the kind field holds no name" — the whole payload, rendered, must not
        // contain it anywhere. A leak that mattered would be in a field nobody thought to check.
        assertFalse(seen.toString().contains(SECRET_NAME),
            () -> "the ruin's identity is the discovery and must not cross: " + seen);
        assertFalse(seen.features().toString().contains(SECRET_NAME),
            "a neighbour's ruin is not a feature of this ground");

        assertNotEquals(before.fingerprint(), seen.fingerprint(),
            "a shape appeared on the skyline, so the backdrop must be able to notice");
    }

    @Test
    void aRuinTwoChunksAwayIsNotSeenAtAll() {
        world();
        UUID here = groundWithAnEmptySkyline();
        Assumptions.assumeTrue(here != null, "this world has no open ground with an empty skyline to borrow");
        standOn(here);
        Assumptions.assumeTrue(visual.active(NOON).lit(), "the ground chosen must be lit at noon");

        UUID oneStep = neighbour(here, 0, 1);
        UUID twoSteps = oneStep == null ? null : neighbour(oneStep, 0, 1);
        Assumptions.assumeTrue(twoSteps != null, "the map must extend two chunks south of the chosen ground");
        raiseRuin(twoSteps, SECRET_NAME);

        var seen = visual.active(NOON);
        // This is what separates a visibility tier from a marker lookup. If the one-ring bound were dropped, or if
        // this were ever rebuilt as "nearest ruin on the map", this is the assertion that would catch it.
        assertTrue(seen.landmarks().isEmpty(),
            () -> "two chunks off is not adjacent, and only the one ring may be reported: " + seen.landmarks());
        assertFalse(seen.toString().contains(SECRET_NAME), () -> "nor may its name appear: " + seen);
    }

    @Test
    void theDarkTakesTheSkylineAway() {
        world();
        UUID here = groundWithAnEmptySkyline();
        Assumptions.assumeTrue(here != null, "this world has no open ground with an empty skyline to borrow");
        standOn(here);
        UUID southward = neighbour(here, 0, 1);
        Assumptions.assumeTrue(southward != null, "the chosen ground needs a neighbour to the south");
        raiseRuin(southward, SECRET_NAME);

        Assumptions.assumeTrue(visual.active(NOON).landmarks().size() == 1, "the shape must be visible by day first");

        var night = visual.active(MIDNIGHT);
        Assumptions.assumeTrue(!night.lit(), "midnight on this ground must actually be dark — no fire burning here");
        assertTrue(night.landmarks().isEmpty(),
            () -> "a silhouette needs light; in the dark there is no skyline: " + night.landmarks());
    }

    /**
     * The ticket's "occluded-site" case. There is no seeing into rock, however bright the day is where the viewer
     * stands, and the occlusion is a property of the ground looked AT rather than of the looker.
     *
     * <p>This has to be measured as a COUNT before and after, which is worth saying because the obvious assertion
     * is worthless: checking that the ruin's name is absent would pass even with occlusion removed, since a
     * landmark never carries a name in the first place. Only the number of reported shapes can tell the difference.
     */
    @Test
    void thereIsNoSeeingIntoRock() {
        world();
        UUID cave = jdbc.query("SELECT id FROM world_chunk WHERE biome='CAVE_INTERIOR' LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        Assumptions.assumeTrue(cave != null, "this world has no cave interior to be unable to see into");
        UUID outside = jdbc.query(
            "SELECT n.id FROM world_chunk c JOIN world_chunk n ON n.world_id=c.world_id " +
            "AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1 WHERE c.id=? AND n.biome <> 'CAVE_INTERIOR' " +
            "AND n.biome <> 'OCEAN' LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null, cave);
        Assumptions.assumeTrue(outside != null, "the cave must have open ground beside it to stand on");

        standOn(outside);
        var before = visual.active(NOON);
        Assumptions.assumeTrue(before.lit(), "the ground beside the cave must be lit at noon");
        int shapesBefore = before.landmarks().size();

        raiseRuin(cave, "Hollow Vault");

        var after = visual.active(NOON);
        assertEquals(shapesBefore, after.landmarks().size(),
            () -> "a ruin inside rock changes nothing about the skyline outside it: " + after.landmarks());
        assertEquals(before.fingerprint(), after.fingerprint(),
            "and nothing visible changed, so the fingerprint must not move either");
    }

    @Test
    void theContractDeclaresItsNewVersion() {
        world();
        UUID here = groundWithAnEmptySkyline();
        Assumptions.assumeTrue(here != null, "this world has no open ground with an empty skyline to borrow");
        standOn(here);
        var seen = visual.active(NOON);
        assertEquals(VisualContextService.VERSION, seen.version(), "the payload must declare its contract version");
        assertEquals(3, VisualContextService.VERSION, "the skyline tier is version 3");
        assertNotNull(seen.landmarks(), "landmarks is empty when nothing is seen, never null");
    }
}
