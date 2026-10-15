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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saying what your own body is doing (#37).
 *
 * <p>Sixteen of twenty such statements reached nothing, while four worked:
 *
 * <pre>
 *   I am tired / I am hungry / I am thirsty / I feel ill  -&gt; answered
 *   I am bleeding / I am hurt / I am injured / I am cut   -&gt; nothing at all
 *   my arm hurts / my leg hurts / it hurts / I am in pain -&gt; nothing at all
 *   my hands are cold / my feet are cold                  -&gt; nothing at all
 *   I am wet / I am soaked                                -&gt; nothing at all
 *   I am exhausted / I am sick / I am dizzy / I am weak    -&gt; nothing at all
 * </pre>
 *
 * <p>The body rule was a list of literal phrases, and its own comments already called that out twice as "the
 * literal-phrasing trap". In a survival game a player reporting their own body is close to the most natural
 * sentence there is, and the Chronicle has ten Body HUD fields and a tracked injury behind it.
 *
 * <p><b>Each family goes where the game already sends its nearest synonym</b>, so nothing here invents a routing
 * decision: {@code stop the bleeding} is TREAT_WOUND, {@code I am freezing} is WARM_BODY, {@code dry my clothes}
 * is DRY_BODY. Each of those answers a bare statement honestly when there is nothing to be done — "you go over
 * yourself and find nothing open and nothing bleeding" — so a statement is reported on when it is only a
 * statement and acted on when there is something to act on.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class SayingWhatYourBodyIsDoingIntegrationTest {

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

    @Test
    void sayingItGoesWhereAskingForItAlreadyGoes() {
        aLivingChronicle();
        Map<String, String> expected = Map.ofEntries(
            Map.entry("I am bleeding", "TREAT_WOUND"),
            Map.entry("I am hurt", "TREAT_WOUND"),
            Map.entry("I am injured", "TREAT_WOUND"),
            Map.entry("I am cut", "TREAT_WOUND"),
            Map.entry("my arm hurts", "TREAT_WOUND"),
            Map.entry("my leg hurts", "TREAT_WOUND"),
            Map.entry("it hurts", "TREAT_WOUND"),
            Map.entry("I am in pain", "TREAT_WOUND"),
            Map.entry("I am sore", "TREAT_WOUND"),
            Map.entry("my hands are cold", "WARM_BODY"),
            Map.entry("my feet are cold", "WARM_BODY"),
            Map.entry("my fingers are numb", "WARM_BODY"),
            Map.entry("I am wet", "DRY_BODY"),
            Map.entry("I am soaked", "DRY_BODY"),
            Map.entry("I am exhausted", "SENSE_BODY"),
            Map.entry("I am dizzy", "SENSE_BODY"),
            Map.entry("I am weak", "SENSE_BODY"),
            Map.entry("I am sick", "SENSE_BODY"));
        expected.forEach((said, want) -> {
            String got = intentOf(said);
            assertTrue(want.equals(got), () -> "'" + said + "' must reach " + want + ", not " + got);
        });
    }

    @Test
    void theOnesThatAlreadyWorkedStillWork() {
        // The list this replaces knew these four. Nothing is traded for the sixteen.
        aLivingChronicle();
        for (String said : List.of("I am tired", "I am hungry", "I am thirsty", "I feel ill")) {
            assertTrue("SENSE_BODY".equals(intentOf(said)),
                () -> "'" + said + "' must keep its answer: -> " + intentOf(said));
        }
    }

    @Test
    void sayingSomethingOfANIMALIsNotSayingItOfYourself() {
        // THE WRONG ANSWER THIS SLICE NEARLY CREATED. The first cut allowed any "my <noun>", and took "my goat
        // is sick" as a reading of the PLAYER's body — a keeper told about their own chill and hunger when they
        // asked about an animal. That is the same class of confident falsehood the slice exists to remove.
        //
        // The cure is a CLOSED set of what a person can call part of themselves (or the cloth on it). Excluding
        // animals instead would be a list of species that misses whichever one was forgotten; a person's parts
        // do not grow.
        aLivingChronicle();
        for (String said : List.of("my goat is sick", "the goat is sick")) {
            String got = intentOf(said);
            assertFalse("SENSE_BODY".equals(got) || "TREAT_WOUND".equals(got),
                () -> "'" + said + "' is about a beast, not about me: -> " + got);
        }
        // And a tool is not a limb either.
        assertFalse("TREAT_WOUND".equals(intentOf("my knife is blunt")), "a blunt knife is not a wound");
    }

    @Test
    void workThatMentionsACutIsStillWork() {
        // THE END-ANCHOR, which is the whole guard. "I am cut" is a wound; "I am cutting the hide" is work, and
        // a rule that merely looked for "cut" after "I am" would have taken both.
        aLivingChronicle();
        for (String said : List.of("I am cutting the hide", "I am cutting down a tree", "I am building a shelter",
                                   "I am making cordage", "I am setting a snare", "I am drying the meat")) {
            String got = intentOf(said);
            assertFalse("TREAT_WOUND".equals(got) || "DRY_BODY".equals(got) || "WARM_BODY".equals(got),
                () -> "'" + said + "' is work, not a condition: -> " + got);
        }
    }

    @Test
    void theActsThemselvesAreUntouched() {
        aLivingChronicle();
        assertTrue("TREAT_WOUND".equals(intentOf("stop the bleeding")), "the act keeps its own words");
        assertTrue("TREAT_WOUND".equals(intentOf("bind my wound")), "and so does dressing one");
        assertTrue("WARM_BODY".equals(intentOf("warm my hands")), "and warming");
        assertTrue("DRY_BODY".equals(intentOf("dry my clothes")), "and drying");
    }
}
