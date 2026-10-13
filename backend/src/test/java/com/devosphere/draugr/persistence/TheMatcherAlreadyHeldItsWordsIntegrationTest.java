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
 * The matcher already held its words (#37, V416).
 *
 * <p><b>This test exists because I published the wrong reason twice.</b> V411 refused the family word "cape" on
 * the grounds that it sits inside ESCAPE "with no longer competing keyword to beat it on length", and V415
 * inherited that and extended it to "bow" (inside ELBOW) and "wrap" (inside WRAPPED).
 *
 * <p>The premise was wrong. {@code ActivityClassifier.normalise} space-pads both sides and
 * {@code containsTerm} asks whether the padded text contains the padded term, so {@code " cape "} is never found
 * inside {@code " escape "}. The process keyword matcher has held keywords to whole words all along; the
 * longest-keyword rule is for choosing between competing WHOLE-WORD matches, not for defending against
 * substrings.
 *
 * <p>I had conflated two matchers. {@code ChronicleActionService.classify} genuinely does use raw
 * {@code contains}, and every collision this project has suffered came from there — "stable" inside "table",
 * "fly agaric" inside the impossible-act list, "boar" inside floorboard, "weather" inside "eat". The keyword
 * matcher is a different mechanism with a different rule, and I carried the classifier's hazard across without
 * checking.
 *
 * <p><b>So this test drives the live matcher with the three words I wrongly feared</b>, which is the only honest
 * way to hold a claim about what the matcher does. It is the standing guard against the old argument being
 * reinstated from the migration comments that still carry it.
 *
 * <p>It also holds the thing the first cut of V416 got wrong: <b>a family word is two halves.</b> The matcher
 * wants a keyword AND a subject on the SAME process. {@code make a bow} and {@code make a cape} worked
 * immediately because those processes already carried a bare subject from their own naming; {@code make a wrap}
 * stayed dead, because the wrap processes carry only compound subjects — "bark wrap", "face wrap", "hand wrap".
 * A keyword count of ten looked healthy and reached nothing.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheMatcherAlreadyHeldItsWordsIntegrationTest {

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

    private String said(String text) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        if (chronicles.active() == null) assertNotNull(chronicles.awaken(), "a living Chronicle is needed");
        var r = actions.resolve(text, java.util.UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    private static boolean readsAsAMiss(String prose) {
        return prose.contains("the way to make what you intend does not come")
            || prose.contains("cannot find the step that would take it further")
            || prose.contains("the stuff of it is willing enough")
            || prose.contains("each one stops at the same place")
            || prose.contains("no method for what you meant")
            || prose.contains("beyond what your hands and knowledge");
    }

    @Test
    void theThreeFamilyWordsIWronglyRefusedNowReachTheirSiblings() {
        for (String plain : new String[]{"make a bow", "make a cape", "make a wrap"}) {
            String prose = said(plain);
            assertFalse(readsAsAMiss(prose), () -> "\"" + plain + "\" must reach the family: " + prose);
            assertTrue(prose.contains("fit more than one piece of work"),
                () -> "\"" + plain + "\" should name the siblings and ask: " + prose);
        }
    }

    @Test
    void escapingIsNotMakingARainCapeAndAnElbowIsNotABow() {
        // THE WHOLE POINT. These are the sentences V411 and V415 refused the family words to protect, and the
        // matcher never needed protecting: it space-pads both sides, so " cape " is not inside " escape ".
        String flee = said("escape the wolf");
        assertFalse(flee.contains("rain cape") || flee.contains("shoulder cape"),
            () -> "fleeing must not be answered with a cape: " + flee);

        String away = said("escape from here");
        assertFalse(away.contains("rain cape") || away.contains("shoulder cape"),
            () -> "nor must getting away: " + away);

        // "elbow" is one word and cannot contain the word "bow". (It still reaches nothing, which is a separate
        // gap and not this rule's to fill — asserted as "not a bow" rather than as a hit, so the test states
        // what is true.)
        String elbow = said("make an elbow guard");
        assertFalse(elbow.contains("self bow") || elbow.contains("recurve"),
            () -> "an elbow guard must not be answered with a bow: " + elbow);

        // And a bowl is still a bowl, which is the one case where length genuinely decides.
        String bowl = said("make a bowl");
        assertFalse(readsAsAMiss(bowl), () -> "a bowl is makeable and must stay so: " + bowl);
        assertTrue(bowl.contains("bowl"), () -> "and it must be a bowl, not a bow: " + bowl);

        // "wrapped" is one word too.
        String wrapped = said("the meat is wrapped");
        assertFalse(wrapped.contains("neck wrap") || wrapped.contains("face wrap"),
            () -> "a past participle must not be read as a request to make one: " + wrapped);
    }

    @Test
    void aFamilyWordIsTwoHalvesAndEitherAloneIsInert() {
        // What the first cut of V416 got wrong. The matcher wants a keyword AND a subject on the SAME process;
        // ten keywords with no bare subject anywhere reached nothing, and the keyword count looked healthy.
        for (String word : new String[]{"bow", "cape", "wrap"}) {
            Integer both = jdbc.queryForObject(
                "SELECT count(*) FROM material_process mp "
                + " WHERE EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k)=?) "
                + "   AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key=mp.process_key "
                + "                 AND ps.subject_term=?)", Integer.class, word, word);
            assertTrue(both != null && both > 0,
                () -> "\"" + word + "\" must be both a keyword and a subject on the same process, or it is "
                    + "inert however many keywords declare it");
        }
    }

    @Test
    void theFamilyWordsOfTheEarlierSlicesStillStand() {
        // V411's hats and V415's armour are data this migration does not touch, and whose ARGUMENT it corrects.
        // Held here so a later cycle cannot revert them on the strength of the old substring reasoning.
        for (String word : new String[]{"hat", "cap", "cuirass", "ingot", "panel"}) {
            Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k)=?",
                Integer.class, word);
            assertTrue(n != null && n > 0, () -> "the family word \"" + word + "\" has gone");
        }
        String hat = said("make a hat");
        assertFalse(readsAsAMiss(hat), () -> "\"make a hat\" must still reach the hat: " + hat);
    }
}
