package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterEach;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * One sky, and catching what it gives (#37).
 *
 * <p><b>The world told the player two different things about one sky, in the same minute, on the same ground.</b>
 * Measured live on a rainy day:
 *
 * <pre>
 *   read the sky  -&gt; "… Snow is coming down."                        (via BiomeClimate — the local fall)
 *   look around   -&gt; "… A rain falls, ticking against leaf and stone." (straight off world_weather — the front)
 * </pre>
 *
 * <p>{@code BiomeClimate} exists for precisely this: one global front, and "the same front … falls as snow where
 * the lowland gets rain". Every other reader went through it — the body it charges, the sky reading, the
 * examination, the visual context. The one sentence most players use to ask what the weather is doing,
 * {@code look around}, did not, and neither did the weather shown in the HUD frame.
 *
 * <p><b>And standing in it, you could not catch it.</b> {@code collect water} in the rain answered <i>"There is no
 * water here to fill from"</i> — false, and the world knew better in the same breath, since DRINK already said
 * "rain wets your face and runs off your hands". Of the phrasings a person reaches for, {@code collect rainwater},
 * {@code catch the rain}, {@code catch rainwater}, {@code gather rainwater} and {@code set out a vessel for the
 * rain} all landed on the generic miss, one of them because RAINWATER IS ONE WORD and the rule looked for
 * "collect water".
 *
 * <p><b>What this test holds, and why in this shape.</b> It asserts the AGREEMENT of the two sky sentences rather
 * than either one's text — an agreement cannot be satisfied by a stale constant, and it holds in every weather,
 * whereas a fixed string would only hold in one. It then reads the sky the way a player does, from the game's own
 * words, and requires the water answer to match THAT sky: fill in rain, name the melting in snow, and say plainly
 * that nothing is falling when nothing is. Every branch is a real assertion, so this test never skips its point.
 *
 * <p>It deliberately does NOT move {@code simulation_clock} to force rain. The suite shares one clock and one
 * world; days of simulated time land on every other test's Chronicle, and thirteen of them killed mine outright
 * while this was being measured. The rain path's arithmetic is proved against a throwaway database instead.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class OneSkyAndCatchingWhatItGivesIntegrationTest {

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

    private final List<UUID> given = new ArrayList<>();

    @AfterEach
    void takeBackWhatThisTestMade() {
        // One world, ~350 tests. A vessel this test handed over is another test's vessel, and water it caught is
        // another test's water. A consumed item cannot be deleted — spending it writes an object_transition whose
        // foreign key refuses the delete — so what still carries a reference is archived instead.
        for (UUID id : given) {
            boolean hasHistory = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM object_transition WHERE object_id=?)", Boolean.class, id));
            String state = jdbc.query("SELECT lifecycle_state FROM world_object WHERE id=?",
                rs -> rs.next() ? rs.getString(1) : null, id);
            if (state == null) continue;
            if (!hasHistory) {
                jdbc.update("DELETE FROM item_instance WHERE object_id=?", id);
                jdbc.update("DELETE FROM world_object WHERE id=?", id);
            } else if ("ACTIVE".equals(state)) {
                jdbc.update("UPDATE world_object SET lifecycle_state='ARCHIVED' WHERE id=?", id);
            }
        }
        given.clear();
    }

    private UUID aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(summary, "a living Chronicle is needed");
        return summary.id();
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    private String intentOf(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    /** The weather in one word, taken from the game's own sky reading — the sky as a PLAYER is told it. */
    private String theSkyAsTold() {
        String sky = said("read the sky");
        if (sky.contains("rain is falling")) return "RAIN";
        if (sky.contains("snow is coming down")) return "SNOW";
        if (sky.contains("closed over")) return "OVERCAST";
        if (sky.contains("sky is open")) return "CLEAR";
        return fail("the sky reading named no weather at all: " + sky);
    }

    private UUID giveAVessel(UUID me) {
        UUID id = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id, created_at) "
            + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d WHERE d.item_key='waterskin'",
            id, me);
        Assumptions.assumeTrue(made != null && made == 1, "this catalogue has no waterskin");
        jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) VALUES (?,'waterskin','SOUND')", id);
        given.add(id);
        return id;
    }

    private int waterInHand(UUID me) {
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id "
            + "WHERE w.current_owner_id=? AND i.item_key IN ('raw_water','filtered_water','clean_water') "
            + "AND w.lifecycle_state='ACTIVE'", Integer.class, me);
        assertNotNull(n);
        return n;
    }

    @Test
    void theSkyLookedAtIsTheSkyTheWorldUses() {
        // THE DEFECT, HELD AS AN AGREEMENT. `look around` read world_weather raw while everything else read the
        // fall as felt here, so the two could name different weather on the same ground in the same minute.
        // Asserting agreement rather than text means this holds whatever the sky happens to be doing.
        aLivingChronicle();
        String told = theSkyAsTold();
        String survey = said("look around");

        String mustSay = switch (told) {
            case "RAIN" -> "rain";
            case "SNOW" -> "snow";
            case "OVERCAST" -> "overcast";
            default -> "sky is clear";
        };
        assertTrue(survey.contains(mustSay),
            () -> "the survey and the sky reading must describe ONE sky: the reading said " + told
                  + ", so the survey had to mention '" + mustSay + "' — it said: " + survey);

        // And it must not name the OTHER kind of fall, which is how the contradiction showed itself.
        String mustNotSay = "SNOW".equals(told) ? "rain falls" : "RAIN".equals(told) ? "snow drifts" : null;
        if (mustNotSay != null) {
            assertTrue(!survey.contains(mustNotSay),
                () -> "the sky reading said " + told + " and the survey still described the other fall: " + survey);
        }
    }

    /**
     * The sky grouped the way the PROSE can express it. The sky reading says "Rain is falling" for a storm as
     * well as for rain, so holding the frame to exact equality would fail on any stormy day for no reason — the
     * two would be describing one sky and disagreeing only about a word the prose never offered.
     */
    private static String wetOrDry(String kind) {
        return switch (kind == null ? "" : kind) {
            case "RAIN", "STORM" -> "RAIN";
            case "SNOW" -> "SNOW";
            case "OVERCAST" -> "OVERCAST";
            default -> "CLEAR";
        };
    }

    @Test
    void theFrameShowsTheSkyThatTheProseDescribes() {
        // The HUD had the same split: the panel showed the front while the paragraph described the local fall.
        aLivingChronicle();
        String told = theSkyAsTold();
        var r = actions.resolve("look around", UUID.randomUUID());
        assertNotNull(r);
        assertNotNull(r.frame(), "the frame carries the weather the client shows");
        assertNotNull(r.frame().weather(), "the frame must report a sky");
        String shown = wetOrDry(r.frame().weather().kind());
        assertTrue(told.equals(shown),
            () -> "the HUD sky and the spoken sky must be the same sky: reading said " + told
                  + ", frame said " + r.frame().weather().kind());
    }

    @Test
    void theWordsForCatchingRainReachTheWaterChain() {
        // All five of these landed on the generic miss. "collect rainwater" is the one worth naming: the rule
        // looked for "collect water", and rainwater is one word.
        aLivingChronicle();
        for (String phrase : List.of("collect rainwater", "catch the rain", "catch rainwater",
                                     "gather rainwater", "set out a vessel for the rain")) {
            String got = intentOf(phrase);
            assertTrue("COLLECT_WATER".equals(got),
                () -> "asking to catch the rain must reach the water chain, not the generic miss: '"
                      + phrase + "' -> " + got);
        }
    }

    @Test
    void buildingTheCatchmentIsStillBuildingIt() {
        // THE SHADOW THIS SLICE HAD TO DODGE, and the reason the rule is a pattern with exclusions rather than a
        // pair of contains(). A Java intent runs ahead of the assembly matcher, and the rainwater_catchment
        // assembly owns the BARE phrase "rainwater catchment" — while "catchment" itself contains "catch".
        aLivingChronicle();
        for (String phrase : List.of("build a rainwater catchment", "dig a rainwater catchment",
                                     "work on the rainwater catchment", "build a rain screen")) {
            String got = intentOf(phrase);
            assertTrue(!"COLLECT_WATER".equals(got),
                () -> "the catalogue owns this phrase and must keep it: '" + phrase + "' -> COLLECT_WATER");
        }
    }

    @Test
    void gatheringSomethingElseBeforeTheRainIsStillGathering() {
        // A bare "gather" beside a bare "rain" reads "gather firewood before the rain" as filling a waterskin.
        // That sentence is said often in this game and belongs to the wood.
        aLivingChronicle();
        String got = intentOf("gather firewood before the rain");
        assertTrue(!"COLLECT_WATER".equals(got),
            () -> "the rain must be the OBJECT of the verb, not merely present in the sentence: -> " + got);
    }

    @Test
    void theWaterAnswerAgreesWithTheSkyWhateverItIs() {
        // Every weather is a real assertion here, so this test cannot quietly pass by finding a sky it has
        // nothing to say about. The old answer — "no stream, spring, or standing water within reach" — was given
        // in all four, including while rain was running off the player's hands.
        UUID me = aLivingChronicle();
        boolean groundHasWater = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_chunk wc WHERE wc.id=(SELECT current_location_id FROM world_object WHERE id=?) "
            + "AND wc.biome IN ('WETLAND','RIVER_BANK'))", Boolean.class, me));
        Assumptions.assumeFalse(groundHasWater, "this is about ground with no water of its own");

        giveAVessel(me);
        String told = theSkyAsTold();
        int before = waterInHand(me);
        String answer = said("collect rainwater");
        int after = waterInHand(me);

        switch (told) {
            case "RAIN" -> assertTrue(after > before,
                () -> "it is raining and a vessel is in hand; the rain must go into it: " + before + " -> " + after
                      + " (" + answer + ")");
            case "SNOW" -> assertTrue(answer.contains("melted"),
                () -> "snow is water that will not pour — say what it takes: " + answer);
            default -> assertTrue(answer.contains("nothing falling out of the sky"),
                () -> "with a dry sky, say that the sky is giving nothing rather than only naming absent streams: "
                      + answer);
        }
    }
}
