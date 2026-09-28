package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four answers that were confidently wrong, found by playing act nine (#37).
 *
 * <p>Each of these succeeded. That is what makes them worth a regression: a missing answer announces itself, and an
 * answer delivered in the same even voice as every true one does not.
 *
 * <ul>
 *   <li><b>A place called "place."</b> {@code name this place} contains no name; the fallback pattern backtracked
 *       and captured the word "place", so the world said "You fix a name to this place: place." and wrote it into
 *       {@code chronicle_named_location} and the Chronicle's current zone.</li>
 *   <li><b>A tree that is not there.</b> {@code climb a tree to look around} on open grassland running flat to
 *       every horizon reported what was seen from up a tree.</li>
 *   <li><b>Firewood from beneath trees</b> on the same treeless ground — the defect #30 named: narration must
 *       witness the world.</li>
 *   <li><b>The tally the world was keeping.</b> {@code count the days since I woke} reached the item counter and
 *       answered "nothing by that name is here to count", SUCCEEDED, about a different question — while the
 *       Chronicle's own awakening event and the simulation clock held the answer all along.</li>
 * </ul>
 *
 * Skips without Docker.
 */
@SpringBootTest
class ActNineAnswersIntegrationTest {

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

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    private void standOn(UUID chronicle, String biome) {
        jdbc.update("UPDATE world_chunk SET biome=? WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", biome, chronicle);
    }

    @Test
    void askingForAPlaceToBeNamedIsNotItselfAName() {
        UUID chronicle = awaken();

        ChronicleActionService.ActionResult empty = actions.resolve("name this place");
        assertEquals("FAILED", empty.outcome(), () -> "there is no name in those words: " + empty.perception());
        assertFalse(empty.perception().contains(": place"),
            () -> "and the answer must not be that the place is called place: " + empty.perception());
        assertEquals(0, count("SELECT COUNT(*) FROM chronicle_named_location WHERE chronicle_id=? AND lower(name)='place'", chronicle),
            "nothing named 'place' is written down");

        // The regex must keep doing its job: the demonstrative is still skipped when a real name follows it.
        ChronicleActionService.ActionResult named = actions.resolve("name this place the Long Meadow");
        assertEquals("SUCCEEDED", named.outcome(), () -> "a real name must still take: " + named.perception());
        assertTrue(named.perception().contains("Long Meadow"), () -> "and be the name given: " + named.perception());
        assertEquals(1, count("SELECT COUNT(*) FROM chronicle_named_location WHERE chronicle_id=? AND name='Long Meadow'", chronicle),
            "the named place is persisted under the name the Chronicle gave it");
    }

    @Test
    void thereIsNoClimbingATreeOnGroundThatHasNone() {
        UUID chronicle = awaken();

        standOn(chronicle, "GRASSLAND");
        ChronicleActionService.ActionResult onGrass = actions.resolve("climb a tree to look around");
        assertEquals("FAILED", onGrass.outcome(), () -> "open grass has nothing to climb: " + onGrass.perception());
        assertTrue(onGrass.perception().contains("nothing here that will take your weight"),
            () -> "and the refusal witnesses the ground it is refusing on: " + onGrass.perception());

        // In a wood it is an ordinary thing to do, and the survey is what they see from up there.
        standOn(chronicle, "TEMPERATE_FOREST");
        ChronicleActionService.ActionResult inWood = actions.resolve("climb a tree to look around");
        assertEquals("SUCCEEDED", inWood.outcome(), () -> "a wood has trees in it: " + inWood.perception());
        assertTrue(inWood.perception().contains("haul yourself up"),
            () -> "and the act itself is narrated, not skipped: " + inWood.perception());

        // A plain look about is not a climb, and must keep working on any ground at all.
        standOn(chronicle, "GRASSLAND");
        assertEquals("SUCCEEDED", actions.resolve("look around").outcome(), "looking about needs nothing to climb");
    }

    @Test
    void firewoodComesFromWhatIsActuallyStandingHere() {
        UUID chronicle = awaken();

        standOn(chronicle, "TEMPERATE_FOREST");
        ChronicleActionService.ActionResult inWood = actions.resolve("gather firewood");
        assertEquals("SUCCEEDED", inWood.outcome(), () -> "a wood yields firewood: " + inWood.perception());
        assertTrue(inWood.perception().contains("beneath the trees"),
            () -> "and in a wood that is exactly where it comes from: " + inWood.perception());

        standOn(chronicle, "GRASSLAND");
        ChronicleActionService.ActionResult onGrass = actions.resolve("gather firewood");
        assertEquals("SUCCEEDED", onGrass.outcome(), () -> "open ground still yields some dry wood: " + onGrass.perception());
        assertFalse(onGrass.perception().contains("beneath the trees"),
            () -> "but not from beneath trees that are not there: " + onGrass.perception());
        assertTrue(onGrass.perception().contains("scrub"),
            () -> "it comes from what is actually there: " + onGrass.perception());
    }

    @Test
    void theWorldKnowsHowLongYouHaveBeenHereAndWillSayIt() {
        UUID chronicle = awaken();

        // Both halves are pinned in time, and the clock is shared by the whole suite, so it is put back.
        //
        // Counting what you carry is FINE work and the world refuses it in the dark — "It is too dark to see the
        // fine of it" — which is correct, and which failed this test in CI at one in the morning while passing
        // here at noon. Pinned to midday for that question.
        //
        // The days question is not sight work, but it needs a known elapsed time, and the six days are measured
        // from the Chronicle's own CHRONICLE_AWAKENED record: an immutable personal archive that a trigger will
        // not let anything rewrite (which is the right rule, and the reason the answer reads the archive rather
        // than a mutable column). So the CLOCK is moved to a fixed distance from that record instead.
        java.sql.Timestamp wasAt = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", java.sql.Timestamp.class);
        String wasWeather = jdbc.queryForObject("SELECT weather_kind FROM world_weather LIMIT 1", String.class);
        try {
            jdbc.update("UPDATE simulation_clock SET simulated_at = date_trunc('day', simulated_at) + interval '12 hours' WHERE id=1");
            jdbc.update("UPDATE world_weather SET weather_kind='CLEAR', intensity=0");

            // The item counter is untouched: a real counting question still counts things.
            ChronicleActionService.ActionResult stones = actions.resolve("how many stones do I have");
            assertEquals("SUCCEEDED", stones.outcome(), () -> "counting goods still works: " + stones.perception());
            assertFalse(stones.perception().contains("came to yourself"),
                () -> "and is not answered with the calendar: " + stones.perception());

            // Six days and three hours after this Chronicle woke, whenever that was.
            jdbc.update("UPDATE simulation_clock SET simulated_at = " +
                "(SELECT MIN(ce.occurred_at) FROM chronicle_event ce WHERE ce.chronicle_id=? AND ce.event_type='CHRONICLE_AWAKENED') " +
                "+ interval '6 days 3 hours' WHERE id=1", chronicle);

            ChronicleActionService.ActionResult days = actions.resolve("count the days since I woke");
            assertEquals("SUCCEEDED", days.outcome(), () -> "the question has an answer: " + days.perception());
            assertTrue(days.perception().contains("6 days"),
                () -> "and the answer is the count the world has been keeping: " + days.perception());
            assertFalse(days.perception().contains("nothing by that name"),
                () -> "not a report about carried items, which is what it used to say: " + days.perception());

            // And in the dark. MEASURE is sight work — weighing, counting, sounding a depth all want light — but
            // reckoning up how long you have been somewhere wants only the count you carry in your head. CI found
            // this by running at two in the morning and being told "it is too dark to see the fine of it", which
            // is nonsense: you do not need a candle to know it has been about a week.
            jdbc.update("UPDATE simulation_clock SET simulated_at = date_trunc('day', simulated_at) + interval '2 hours' WHERE id=1");
            ChronicleActionService.ActionResult atNight = actions.resolve("how long have I been here");
            assertEquals("SUCCEEDED", atNight.outcome(), () -> "the dark does not stop you counting days: " + atNight.perception());
            assertTrue(atNight.perception().contains("came to yourself"),
                () -> "and the answer is the same answer: " + atNight.perception());
        } finally {
            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", wasAt);
            jdbc.update("UPDATE world_weather SET weather_kind=?", wasWeather);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    private int count(String sql, UUID chronicle) {
        Integer n = jdbc.queryForObject(sql, Integer.class, chronicle);
        return n == null ? 0 : n;
    }
}
