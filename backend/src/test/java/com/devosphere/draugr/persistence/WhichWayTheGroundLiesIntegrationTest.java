package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which way the ground lies, and the acts whose object is the ground (#37, V405).
 *
 * <p>The movement axis swept at <b>26 of 42 sentences reaching nothing</b> — the axis a player uses more than
 * any other. The routing is held by {@code IntentClassificationRegressionTest}, which needs no world; this holds
 * the part that only a world can answer: that the bearing reported is the bearing the ground actually lies on,
 * and that walking it arrives where the report said it would.
 *
 * <p><b>That agreement is the whole point.</b> {@code whichWay} tells a player which way the marsh is and
 * {@code toward} decides which way wading goes, and if the two ever disagree the game would be lying in one of
 * the two sentences. They share a vocabulary and the convention in {@code Compass}; this proves they share an
 * answer.
 *
 * <p>Each test stands the Chronicle on ground it has CHOSEN for the property it needs, and puts it back
 * afterwards — the suite shares one world and one Chronicle, and a keeper left on the far side of a fen changes
 * what the next test sees.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WhichWayTheGroundLiesIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockWas;
    private UUID whoMoved;
    private UUID stoodAt;

    /** Noon in June: the whole answer is gated on daylight, and after dark it correctly reports that it cannot see. */
    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "blood_loss_ml=0, energy_level=100");
    }

    @AfterEach
    void putItBack() {
        if (whoMoved != null && stoodAt != null)
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", stoodAt, whoMoved);
        whoMoved = null; stoodAt = null;
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
    }

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /** Stand the Chronicle on the first chunk satisfying a predicate over its neighbours, remembering where it was. */
    private Map<String, Object> standOn(UUID chronicle, String having) {
        Map<String, Object> ground = jdbc.query(
            "SELECT c.id, c.biome FROM world_chunk c WHERE c.biome <> 'CAVE_INTERIOR' AND " + having + " LIMIT 1",
            rs -> rs.next() ? Map.of("id", rs.getObject(1, UUID.class), "biome", rs.getString(2)) : null);
        Assumptions.assumeTrue(ground != null, "this world has no ground where: " + having);
        whoMoved = chronicle;
        stoodAt = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground.get("id"), chronicle);
        return ground;
    }

    /** Ground with water on exactly one side: the case where a crossing has only one way it could mean. */
    private static final String ONE_SIDE_OF_WATER =
        "c.biome NOT IN ('OCEAN','WETLAND') AND (SELECT count(*) FROM world_chunk n WHERE n.world_id=c.world_id " +
        "  AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1 AND n.biome IN ('OCEAN','WETLAND')) = 1";

    @Test
    void theJudgementAndTheActAgreeAboutTheWater() {
        UUID me = awaken();
        standOn(me, ONE_SIDE_OF_WATER);

        // The judgement, which has always worked, and must still.
        ChronicleActionService.ActionResult judged = actions.resolve("can I get across here");
        assertEquals("JUDGE_CROSSING", judged.intent());
        assertFalse(judged.perception().toLowerCase(Locale.ROOT).contains("there is none"),
            () -> "there IS water on one side of this ground: " + judged.perception());

        // Which way the water lies, said out loud.
        ChronicleActionService.ActionResult asked = actions.resolve("which way is the water");
        assertEquals("WHICH_WAY", asked.intent(), () -> "asking the way must answer: " + asked.perception());
        String bearing = null;
        for (String point : com.devosphere.draugr.world.Compass.RING)
            if (asked.perception().toLowerCase(Locale.ROOT).contains(point)) { bearing = point; break; }
        assertNotNull(bearing, () -> "and must name a bearing: " + asked.perception());

        // ...and the act goes THAT way. If these two ever disagree the game is lying in one of them.
        UUID before = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        ChronicleActionService.ActionResult waded = actions.resolve("wade across");
        assertEquals("MOVE", waded.intent(), () -> "wading must be a move: " + waded.perception());
        String went = waded.perception().toLowerCase(Locale.ROOT);
        // It may refuse for the load — that is the crossing's own gate and is a true answer — but if it moved,
        // it moved the way the asking said.
        UUID after = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        if (!before.equals(after)) {
            String finalBearing = bearing;
            assertTrue(went.contains(finalBearing),
                () -> "the crossing went a different way than the asking reported (" + finalBearing + "): " + waded.perception());
            String arrived = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, after);
            assertTrue(List.of("OCEAN", "WETLAND").contains(arrived),
                () -> "and it must arrive in the water it crossed, not beside it: " + arrived);
        }
    }

    @Test
    void standingOnTheThingAskedForIsTheFirstThingSaid() {
        UUID me = awaken();
        standOn(me, "c.biome = 'WETLAND'");
        ChronicleActionService.ActionResult r = actions.resolve("which way is the marsh");
        assertEquals("WHICH_WAY", r.intent());
        assertTrue(r.perception().toLowerCase(Locale.ROOT).contains("standing on it"),
            () -> "a keeper in a marsh asking for the marsh is standing on it: " + r.perception());
    }

    @Test
    void afterDarkItSaysItCannotSeeRatherThanReadingTheMap() {
        UUID me = awaken();
        standOn(me, "c.biome = 'GRASSLAND'");
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T23:30:00Z' WHERE id=1");
        ChronicleActionService.ActionResult r = actions.resolve("which way is the water");
        assertEquals("WHICH_WAY", r.intent());
        assertTrue(r.perception().toLowerCase(Locale.ROOT).contains("dark"),
            () -> "in the dark the shape of the country cannot be made out, and it must say so: " + r.perception());
    }

    @Test
    void climbingGoesUpAndTheWorldDecidesWhichWay() {
        UUID me = awaken();
        // Ground with exactly one neighbour meaningfully higher — the same 40-unit step the aspect model uses.
        standOn(me, "(SELECT count(*) FROM world_chunk n WHERE n.world_id=c.world_id " +
                    "  AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1 AND n.elevation > c.elevation + 40) = 1");

        int wasAt = jdbc.queryForObject(
            "SELECT wc.elevation FROM world_object w JOIN world_chunk wc ON wc.id=w.current_location_id WHERE w.id=?",
            Integer.class, me);
        ChronicleActionService.ActionResult r = actions.resolve("climb");
        assertEquals("MOVE", r.intent(), () -> "climbing must be a move: " + r.perception());
        int nowAt = jdbc.queryForObject(
            "SELECT wc.elevation FROM world_object w JOIN world_chunk wc ON wc.id=w.current_location_id WHERE w.id=?",
            Integer.class, me);
        // Either it climbed, or it refused for a reason of its own (a water crossing, a cave mouth, the load) —
        // but it must never come out LOWER than it went in, which is what "climb" would have meant backwards.
        assertTrue(nowAt >= wasAt, () -> "a climb ended lower than it began: " + wasAt + " -> " + nowAt
            + " / " + r.perception());

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void goingBackTheWayYouCameReversesTheStepTheRecordKept() {
        UUID me = awaken();
        standOn(me, "c.biome = 'GRASSLAND'");

        // Nothing to take back until a step has been taken ONTO this ground.
        //
        // The ground is CHOSEN for having no arrival recorded on it rather than the record being cleared:
        // object_transition is IMMUTABLE — a trigger refuses the delete, which is exactly right, since the
        // history of a thing is the one part of this world that must survive everything. The first cut of this
        // test tried to delete and CI answered "object_transition is immutable", which is the table doing its
        // job. standOn already moved the Chronicle by UPDATE, so no arrival was recorded for this chunk.
        boolean cameFromSomewhere = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM object_transition WHERE object_id=? AND transition_type='MOVED' " +
            "  AND payload->>'toLocationId' = (SELECT current_location_id::text FROM world_object WHERE id=?))",
            Boolean.class, me, me));
        if (!cameFromSomewhere) {
            ChronicleActionService.ActionResult none = actions.resolve("retrace my steps");
            assertEquals("MOVE", none.intent());
            assertTrue(none.perception().toLowerCase(Locale.ROOT).contains("no step behind you"),
                () -> "with no step behind it onto this ground, say so: " + none.perception());
        }

        // Take one, then take it back. The record is what decides the way, not a guess.
        UUID start = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        actions.resolve("go north");
        UUID moved = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        Assumptions.assumeTrue(!start.equals(moved), "the step north must have happened for there to be one to take back");

        ChronicleActionService.ActionResult back = actions.resolve("go back the way I came");
        assertEquals("MOVE", back.intent());
        UUID ended = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        assertEquals(start, ended,
            () -> "going back the way you came must arrive where you came from: " + back.perception());
    }
}
