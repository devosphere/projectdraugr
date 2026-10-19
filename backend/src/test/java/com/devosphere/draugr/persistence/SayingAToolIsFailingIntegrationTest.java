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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saying a tool is failing (#37).
 *
 * <p>The third face of this family: the body has one, the camp has one, and the gear in your hands had none.
 * {@code mend my boots} and {@code sharpen my knife} both reach REPAIR_ITEM, while the way a person actually
 * reports the trouble reached nothing:
 *
 * <pre>
 *   my knife is broken        -&gt; nothing   |  mend my boots    -&gt; REPAIR_ITEM
 *   my boots are worn through -&gt; nothing   |  sharpen my knife -&gt; REPAIR_ITEM
 *   the handle has come loose -&gt; nothing
 *   my axe is blunt           -&gt; nothing
 * </pre>
 *
 * <p>And two questions were answered about the wrong thing entirely:
 *
 * <pre>
 *   is my knife sharp           -&gt; nothing, though "how sharp" and "still sharp" were both known
 *   how long will my boots last -&gt; MEASURE, "You pace it out and reckon by eye"
 * </pre>
 *
 * <p>That last is a <b>distance reckoning</b> in answer to a question about wear, while {@code condition_state}
 * and {@code use_count} sit on every item and are exactly what the gear reading reports.
 *
 * <p><b>What this test is really for is the precedence.</b> Three rules now share the word "broken" and the
 * possessive frame, and they must not take each other's sentences: a broken FENCE is a structure, an empty
 * WATERSKIN wants filling rather than mending, and wet BOOTS are the body's business. Each is asserted here
 * because a change to any one of the three frames could silently swap them.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class SayingAToolIsFailingIntegrationTest {

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
    void sayingItIsFailingReachesTheMending() {
        aLivingChronicle();
        for (String said : List.of("my knife is broken", "my boots are worn through", "the handle has come loose",
                                   "my axe is blunt", "the rope is frayed", "my cloak is torn",
                                   "the haft is cracked", "my basket is coming apart")) {
            String got = intentOf(said);
            assertEquals("REPAIR_ITEM", got, () -> "'" + said + "' is a thing to mend: -> " + got);
        }
    }

    @Test
    void askingHowMuchIsLeftInItIsNotADistance() {
        // `how long will my boots last` reached MEASURE — "You pace it out and reckon by eye" — which is a
        // reckoning of DISTANCE offered in answer to a question about wear.
        aLivingChronicle();
        for (String question : List.of("how long will my boots last", "how much life is left in my axe",
                                       "how much wear is left", "is my knife sharp")) {
            String got = intentOf(question);
            assertEquals("TAKE_STOCK_OF_GEAR", got,
                () -> "'" + question + "' is answered from condition_state, not by pacing it out: -> " + got);
            assertFalse("MEASURE".equals(got), () -> "'" + question + "' is not a distance: -> " + got);
        }
    }

    @Test
    void thePrecedenceBetweenTheThreeFramesHolds() {
        // THE REASON THIS TEST MATTERS MOST. Three rules now share the word "broken" and the possessive frame.
        aLivingChronicle();
        Map<String, String> precedence = Map.ofEntries(
            // A broken fence is a STRUCTURE, not an item in the pack.
            Map.entry("the fence is broken", "REPAIR_STRUCTURE"),
            Map.entry("the gate is broken", "REPAIR_STRUCTURE"),
            Map.entry("the roof is leaking", "REPAIR_STRUCTURE"),
            // An empty vessel wants FILLING, not mending.
            Map.entry("my waterskin is empty", "COLLECT_WATER"),
            // And a broken knife is the item.
            Map.entry("my knife is broken", "REPAIR_ITEM"));
        precedence.forEach((said, want) -> {
            String got = intentOf(said);
            assertEquals(want, got, () -> "'" + said + "' must reach " + want + ", not " + got);
        });
        // Wet boots are the body's business, not the cobbler's — a different frame again, and it must keep them.
        String wet = intentOf("my boots are wet");
        assertFalse("REPAIR_ITEM".equals(wet), () -> "wet boots are not broken boots: -> " + wet);
    }

    @Test
    void theMendingItselfIsUntouched() {
        aLivingChronicle();
        for (String phrase : List.of("mend my boots", "sharpen my knife", "repair the stone hammer",
                                     "abrade the flint knife", "sharpen the stone axe")) {
            assertEquals("REPAIR_ITEM", intentOf(phrase),
                () -> "'" + phrase + "' keeps its own work: -> " + intentOf(phrase));
        }
    }

    @Test
    void theOtherReadingsAreUntouched() {
        aLivingChronicle();
        assertEquals("TAKE_STOCK_OF_GEAR", intentOf("how is my knife"), "the gear reading keeps its words");
        assertEquals("TAKE_STOCK_OF_GEAR", intentOf("what am I carrying"), "and so does the stocktake");
        // And a genuine distance question is still a distance question.
        assertFalse("TAKE_STOCK_OF_GEAR".equals(intentOf("how far to the river")),
            () -> "the country is not the pack: -> " + intentOf("how far to the river"));
        assertEquals("MEASURE", intentOf("how deep is it"), "and measuring is still measuring");
    }
}
