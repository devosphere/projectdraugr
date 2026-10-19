package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asking about the night you had (#37).
 *
 * <p><b>The most expensive member of this project's asking-is-not-doing family.</b> {@code did I sleep well} and
 * {@code how did I sleep} both reached SLEEP and SLEPT:
 *
 * <pre>
 *   did I sleep well -&gt; "You settle onto the bare ground and drift through 8 HOURS of
 *                        broken, shallow sleep, waking stiff and only half rested."
 * </pre>
 *
 * <p>A question about the night just gone, answered by consuming eight hours of simulated time and the body's
 * state with it. Every other member of the family either failed harmlessly or merely answered wrongly; this one
 * charged the player a night for asking.
 *
 * <p>Two more from the same sweep:
 *
 * <pre>
 *   make camp / set up camp / settle in for the night -&gt; nothing   |  bed down for the night -&gt; sleeps
 *   look at my map                                    -&gt; OBSERVE, which described the trees overhead
 * </pre>
 *
 * <p>There is no single camp-making work in this game — a camp is a fire, a shelter, a bed and a store, each
 * built separately — so rather than guess which was meant, making camp walks the ground and names what is and is
 * not there, which is precisely the list somebody making camp needs. And {@code read my map} already worked, so
 * looking at one only ever needed the other verb.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AskingAboutTheNightIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    private void aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertNotNull(chronicles.active() != null ? chronicles.active() : chronicles.awaken(),
            "a living Chronicle is needed");
    }

    private String intentOf(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    private Instant simulatedNow() {
        return jdbc.queryForObject("SELECT simulated_at FROM simulation_clock LIMIT 1", java.sql.Timestamp.class).toInstant();
    }

    @Test
    void askingAboutTheNightCostsNoTime() {
        // THE ASSERTION THIS TEST EXISTS FOR, and the only one that catches the real harm: a routing check would
        // pass on any read-only intent, but what made this defect expensive was the EIGHT HOURS. The clock is
        // the thing to hold.
        aLivingChronicle();
        Instant before = simulatedNow();
        actions.resolve("did I sleep well", UUID.randomUUID());
        Instant after = simulatedNow();
        long minutes = java.time.Duration.between(before, after).toMinutes();
        assertTrue(minutes < 60,
            () -> "asking after the night must not spend one: the clock moved " + minutes + " minutes");
    }

    @Test
    void askingAndSayingBothReachTheBodyReading() {
        aLivingChronicle();
        for (String said : List.of("did I sleep well", "how did I sleep", "I slept badly", "I slept well",
                                   "did I rest", "wake up")) {
            String got = intentOf(said);
            assertTrue("SENSE_BODY".equals(got),
                () -> "'" + said + "' is a question about the body, not a night: -> " + got);
            assertFalse("SLEEP".equals(got), () -> "'" + said + "' must not sleep: -> " + got);
        }
    }

    @Test
    void sleepingItselfStillSleeps() {
        // The hazard of putting a guard in front of the bare word "sleep": if it reached too far, a Chronicle
        // could not sleep at all — which in this game is fatal.
        //
        // AND THIS TEST KILLED ITS OWN SUBJECT PROVING IT. Each of these phrases does not merely route — it
        // SLEEPS, eight hours a time. Five of them is forty hours with nothing drunk, and CI failed with "No
        // living Chronicle exists" partway through the loop: the test asserting that sleeping still works
        // demonstrated it by sleeping the Chronicle to death. Which is, in fairness, the behaviour it was
        // written to protect.
        //
        // Ensuring a living Chronicle before each phrase was the first repair, and it was not enough: the five
        // nights still passed on the SHARED simulation clock, and a sibling test in this very class then failed
        // with "No living Chronicle exists" before asserting anything of its own. Spending forty hours of the
        // world to prove a routing claim was the mistake, not the dying.
        //
        // So the five phrasings are asserted in IntentClassificationRegressionTest, where classify() is reached
        // reflectively and costs nothing at all. What is worth paying for ONCE is the other half of the claim:
        // that the act still performs. One night, and the clock bears it.
        aLivingChronicle();
        assertTrue("SLEEP".equals(intentOf("sleep")), "sleeping is still sleeping");
        aLivingChronicle();
        assertTrue("REST".equals(intentOf("rest a while")), "and resting is still resting");
    }

    @Test
    void makingCampNamesWhatTheGroundStillWants() {
        aLivingChronicle();
        for (String phrase : List.of("make camp", "set up camp", "settle in for the night", "what is in my camp")) {
            String got = intentOf(phrase);
            assertEquals("TAKE_STOCK_OF_CAMP", got,
                () -> "making camp is answered with what the ground has: '" + phrase + "' -> " + got);
        }
        var r = actions.resolve("make camp", UUID.randomUUID());
        assertNotNull(r);
        assertNotNull(r.perception());
        // The answer must be about the GROUND and what stands on it — that is what makes reporting the right
        // response to a request with no single act behind it.
        assertTrue(r.perception().toLowerCase(java.util.Locale.ROOT).contains("ground"),
            () -> "and it must speak of the ground: " + r.perception());
    }

    @Test
    void lookingAtAThingYouWroteIsReadingIt() {
        // `look at my map` reached OBSERVE and described the trees overhead. A player who asks to look at their
        // own map and is told about the leaf litter has been answered confidently about the wrong thing.
        aLivingChronicle();
        for (String phrase : List.of("look at my map", "check my journal", "study my notes",
                                     "what have I written", "what did I write")) {
            String got = intentOf(phrase);
            assertEquals("READ", got, () -> "'" + phrase + "' is a reading: -> " + got);
        }
        assertEquals("READ", intentOf("read my journal"), "and the wording that already worked still does");
    }

    @Test
    void theCampStocktakeItselfIsUntouched() {
        aLivingChronicle();
        for (String phrase : List.of("take stock of the camp", "what have I built here", "what stands here")) {
            assertEquals("TAKE_STOCK_OF_CAMP", intentOf(phrase),
                () -> "'" + phrase + "' keeps its answer: -> " + intentOf(phrase));
        }
    }
}
