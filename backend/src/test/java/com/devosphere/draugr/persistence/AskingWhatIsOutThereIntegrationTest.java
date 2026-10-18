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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asking what is out there (#37).
 *
 * <p><b>The capability was already excellent and reachable by one phrasing only.</b> {@code look for game} goes
 * to SEARCH and answers <em>"A horse grazes the open ground. A wood pigeon grazes the open ground"</em> — it
 * names the wildlife actually standing on this chunk. Meanwhile:
 *
 * <pre>
 *   is there game nearby    -&gt; nothing at all   |  look for game      -&gt; works
 *   what animals are around -&gt; nothing at all   |  listen for animals -&gt; works
 *   where are the deer      -&gt; nothing at all   |  look for tracks    -&gt; works
 *   what lives here         -&gt; nothing at all
 *   go hunting              -&gt; nothing at all   |  hunt a deer        -&gt; works
 * </pre>
 *
 * <p>{@code go hunting} is the one worth dwelling on. Naming a quarry works and the bare intent reached nothing
 * — and had CONFRONT_WILDLIFE taken the sentence it would have answered about RED DEER specifically, because
 * that is what a bare "deer" resolves to. An invented quarry is a worse answer than the true one, which is what
 * is actually on this ground; and a player handed that list can then name it.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AskingWhatIsOutThereIntegrationTest {

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
    void askingWhatGameIsAboutReachesTheGroundReading() {
        aLivingChronicle();
        for (String question : List.of("is there game nearby", "any game about", "what animals are around",
                                       "which animals are here", "where are the deer", "what lives here",
                                       "is there anything to hunt")) {
            String got = intentOf(question);
            assertTrue("SEARCH".equals(got),
                () -> "a question about what is out there must reach the ground reading: '" + question + "' -> " + got);
        }
    }

    @Test
    void huntingWithoutNamingTheQuarryIsAnsweredWithWhatIsThere() {
        // Rather than with an invented quarry. CONFRONT_WILDLIFE resolves a bare "deer" to RED DEER, so a vague
        // hunt would have been answered "you work this ground over for red deer and find none of it here" —
        // specific, confident, and about an animal the player never named.
        aLivingChronicle();
        for (String phrase : List.of("go hunting", "do some hunting", "find something to hunt")) {
            String got = intentOf(phrase);
            assertTrue("SEARCH".equals(got),
                () -> "a vague hunt is answered with what is actually here: '" + phrase + "' -> " + got);
        }
    }

    @Test
    void theGroundReadingNamesTheWildlifeOnIt() {
        // What makes the routing worth doing: the answer is not a stub. It names the flora and the animals
        // standing on this chunk, which is exactly what somebody asking after game needs in order to hunt.
        aLivingChronicle();
        var r = actions.resolve("is there game nearby", UUID.randomUUID());
        assertNotNull(r);
        assertNotNull(r.perception());
        assertTrue(r.perception().toLowerCase(java.util.Locale.ROOT).contains("ground"),
            () -> "the ground reading must speak of the ground: " + r.perception());
    }

    @Test
    void namingTheQuarryStillHunts() {
        // The hazard of claiming the vague forms: if the rule reached too far, nothing could be hunted at all.
        aLivingChronicle();
        for (String phrase : List.of("hunt a deer", "stalk the deer", "shoot the deer")) {
            String got = intentOf(phrase);
            assertTrue("CONFRONT_WILDLIFE".equals(got),
                () -> "naming the quarry still hunts it: '" + phrase + "' -> " + got);
        }
    }

    @Test
    void theOtherSearchesAreUntouched() {
        aLivingChronicle();
        for (String phrase : List.of("search the ground", "look for firewood", "look under the log")) {
            assertTrue("SEARCH".equals(intentOf(phrase)),
                () -> "'" + phrase + "' keeps its answer: -> " + intentOf(phrase));
        }
        // Tracking and scouting keep theirs too, which the replay also holds.
        assertTrue("TRACK".equals(intentOf("look for tracks")), "tracking is still tracking");
        assertTrue("SCOUT".equals(intentOf("scout the area")), "scouting is still scouting");
    }

    @Test
    void askingWhatIsOnACarcassIsStillALook() {
        // "off" was in the list and "from" was not, which is the same preposition said the other way.
        aLivingChronicle();
        assertTrue("EXAMINE".equals(intentOf("what can I get from this carcass")),
            () -> "asking what a carcass will give is a look: -> " + intentOf("what can I get from this carcass"));
        assertTrue("EXAMINE".equals(intentOf("what can I get off this carcass")),
            "and the wording that already worked still does");
        // And it is a LOOK, not a taking — the distinction this rule was added for.
        assertFalse("HARVEST_CARCASS".equals(intentOf("what can I get from this carcass")),
            "asking is not butchering");
    }

    @Test
    void askingAboutTheCountryIsStillNavigation() {
        aLivingChronicle();
        assertFalse("SEARCH".equals(intentOf("where is the nearest water")),
            () -> "the country is not the ground underfoot: -> " + intentOf("where is the nearest water"));
    }
}
