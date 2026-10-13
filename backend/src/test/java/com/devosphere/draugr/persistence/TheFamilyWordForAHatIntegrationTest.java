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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The family word for a hat (#37, V411).
 *
 * <p>Twenty-one head and hand garments exist and the plain words for them reached nothing — not even the
 * AMBIGUOUS "which one did you mean" ask. Every process declared only its specific name (`make_fur_cap` answers
 * to "fur cap") and none declared the word a cold person uses.
 *
 * <p>The fix is data rather than a classifier rule, because a Java intent runs before the material matcher:
 * putting "cap" in CRAFT_GARMENT would have shadowed `make_leather_cap` and the dozen like it. Declared as
 * process keywords, the family word reaches the siblings and the tie-breaker does the rest.
 *
 * <p><b>What this test is for is the two collisions the migration's own guards cannot see.</b> V411 asserts the
 * data invariants — every family word reaches a process, nothing declares a bare "cape", "thatch" is still
 * longer than "hat". It cannot assert what the MATCHER does with them, and that is the whole risk:
 *
 * <ul>
 *   <li><b>"cap" sits inside DEATH_CAP</b>, which is a real mushroom a player gathers;</li>
 *   <li><b>"hat" sits inside THATCH</b>, and thatching a roof is a real process.</li>
 * </ul>
 *
 * <p>Both are safe only because the matcher takes the LONGEST keyword and "death cap" and "thatch" each outrank
 * the family word. That is a property of the matcher, so it is asserted through the matcher.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheFamilyWordForAHatIntegrationTest {

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
        if (chronicles.active() == null) assertNotNull(chronicles.awaken(), "a living Chronicle is needed");
    }

    /** What the composer said, lowercased, for asserting on prose without caring about case. */
    private String said(String text) {
        aLivingChronicle();
        var r = actions.resolve(text, java.util.UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    /** The prose a sentence gets when nothing at all routed it — the thing a family word must never produce. */
    private static boolean readsAsAMiss(String prose) {
        return prose.contains("nothing here answers to the attempt")
            || prose.contains("whatever you meant by that")
            || prose.contains("it does not come to anything")
            || prose.contains("the way to make what you intend does not come")
            || prose.contains("cannot find the step that would take it further")
            || prose.contains("the stuff of it is willing enough");
    }

    @Test
    void theFamilyWordReachesTheGarmentsAndNotAMiss() {
        // ONE candidate, so it resolves outright and refuses on materials rather than asking.
        String hat = said("make a hat");
        assertFalse(readsAsAMiss(hat), () -> "\"make a hat\" must reach the one hat in the catalogue: " + hat);

        // SEVERAL candidates, so the right answer is the ask — naming them, which is what the player needs.
        for (String plural : new String[]{"make a cap", "make a hood", "make gloves", "make mittens"}) {
            String prose = said(plural);
            assertFalse(readsAsAMiss(prose), () -> "\"" + plural + "\" must reach the family: " + prose);
            assertTrue(prose.contains("fit more than one piece of work"),
                () -> "\"" + plural + "\" should ASK which one, since several answer to it: " + prose);
        }
    }

    @Test
    void theSpecificNameStillWinsOverTheFamilyWord() {
        // The longest keyword takes it, so naming the material still picks that one out rather than asking.
        String prose = said("sew a leather cap");
        assertFalse(prose.contains("fit more than one piece of work"),
            () -> "naming the material must settle it without an ask: " + prose);
        assertFalse(readsAsAMiss(prose), () -> "and it must still route: " + prose);
    }

    @Test
    void thatchingARoofIsNotMillineryAndADeathCapIsNotAGarment() {
        // THE WHOLE RISK OF THIS MIGRATION. "hat" is inside THATCH and "cap" is inside DEATH CAP, and both are
        // safe only because the matcher prefers the longer keyword. Asserted through the matcher, because the
        // migration's own guards can check the lengths and not the behaviour.
        String thatch = said("thatch the roof");
        assertFalse(thatch.contains("hat") && thatch.contains("fit more than one"),
            () -> "thatching must not reach the hat: " + thatch);
        assertFalse(thatch.contains("reed hat"), () -> "thatching a roof is not making a hat: " + thatch);

        // The mushroom is gathered where it grows, and must still be.
        Integer growsHere = jdbc.queryForObject(
            "SELECT count(*) FROM flora_definition WHERE flora_key='death_cap'", Integer.class);
        Assumptions.assumeTrue(growsHere != null && growsHere > 0, "this catalogue has no death cap to confuse");
        String cap = said("gather a death cap");
        assertFalse(cap.contains("fit more than one piece of work"),
            () -> "a death cap is a mushroom, not three kinds of hat: " + cap);
    }

    /**
     * A bare "cape" keyword IS safe, and this assertion used to say the opposite (#37, V416).
     *
     * <p>V411 refused the family word "cape" on the grounds that it sits inside ESCAPE "with no longer keyword to
     * outrank it", and this test guarded that decision. <b>The premise was wrong.</b>
     * {@code ActivityClassifier.normalise} space-pads both sides and {@code containsTerm} compares the padded
     * forms, so {@code " cape "} is never found inside {@code " escape "}. The process keyword matcher has held
     * keywords to whole words all along; the longest-keyword rule is for choosing between competing
     * <em>whole-word</em> matches, not for defending against substrings.
     *
     * <p>I had conflated it with {@code ChronicleActionService.classify}, which genuinely does use raw
     * {@code contains} and is where every collision in this project has come from.
     *
     * <p>So the assertion is inverted: the keyword must now BE there, and the sentence it was supposed to
     * protect is held through the <b>live matcher</b>, which is the only honest way to make a claim about what
     * the matcher does. The same correction applies to "bow" and "wrap" in
     * {@code TheFamilyWordsForArmourIntegrationTest}.
     */
    @Test
    void aBareCapeKeywordIsSafeBecauseTheMatcherHoldsWholeWords() {
        Integer bare = jdbc.queryForObject(
            "SELECT count(*) FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k)='cape'",
            Integer.class);
        assertTrue(bare != null && bare > 0,
            "V416 declares a bare \"cape\"; if it has gone, check whether it was reverted on the strength of the "
            + "old substring argument, which measurement disproved");

        // THE PROOF, through the matcher rather than from the code. If it were raw-contains, fleeing would be
        // answered with millinery.
        String flee = said("escape the wolf");
        assertFalse(flee.contains("rain cape") || flee.contains("shoulder cape"),
            () -> "fleeing must not be answered by making a cape: " + flee);
        String away = said("escape from here");
        assertFalse(away.contains("rain cape") || away.contains("shoulder cape"),
            () -> "nor must getting away: " + away);

        // ...and the family word still does its own job.
        String cape = said("make a cape");
        assertFalse(readsAsAMiss(cape), () -> "\"make a cape\" must reach the seven capes: " + cape);
    }
}
