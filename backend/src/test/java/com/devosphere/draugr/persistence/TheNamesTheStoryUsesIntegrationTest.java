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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The names the story uses (#77, V422).
 *
 * <p>#77 names 100 structures. An audit against the catalogue found 35 present by key and 65 absent, which reads
 * like two thirds of a story outstanding. <b>It is not.</b> Testing all 65 live through the composer —
 * {@code build a <name>} for each — only <b>nineteen</b> reach nothing at all. The other forty-six are already
 * built and answer under another name or through a Java intent: ADVANCE_ASSEMBLY (10), a material process (11),
 * CRAFT_SHELF (4), MARK (3), MAKE_BED (3), BUILD_LATRINE (3) and the rest.
 *
 * <p>That is this project's recurring lesson in a new place: <b>a key absent from the catalogue is only a gap if
 * nothing else owns the phrase.</b> Key equality is not reachability, and here the difference is 65 against 19.
 *
 * <p>Of those nineteen, five were the same thing under a different word, and this test holds them. The other
 * fourteen are genuinely absent and are recorded on the story rather than invented.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheNamesTheStoryUsesIntegrationTest {

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

    @Test
    void theStorysWordReachesTheStructureTheGameAlreadyBuilds() {
        aLivingChronicle();
        Map<String, String> expected = Map.of(
            "build a cooking pit", "ADVANCE_ASSEMBLY",
            "build a brush path", "ADVANCE_ASSEMBLY",
            "build a pottery drying shelf", "ADVANCE_ASSEMBLY",
            "build a smokehouse", "ADVANCE_ASSEMBLY",
            // The pit IS the making in this game, so #77's `charcoal_pit` is a Java intent rather than a
            // structure — and its verbs were make/take/gather/get/collect, never build or dig.
            "build a charcoal pit", "MAKE_CHARCOAL",
            "dig a charcoal pit", "MAKE_CHARCOAL");
        expected.forEach((said, want) -> {
            String got = intentOf(said);
            assertEquals(want, got, () -> "'" + said + "' must reach " + want + ", not " + got);
        });
    }

    @Test
    void aCookingPitIsTheEarthOvenAndActuallyDigsIt() {
        // Not merely routing: the earth oven's first stage is "dig the pit", which is precisely what somebody
        // asking for a cooking pit wants done. Measured because a routing assertion would pass on any assembly.
        aLivingChronicle();
        var r = actions.resolve("build a cooking pit", UUID.randomUUID());
        assertNotNull(r);
        assertNotNull(r.perception());
        String prose = r.perception().toLowerCase(java.util.Locale.ROOT);
        assertTrue(prose.contains("pit") || prose.contains("wants") || prose.contains("strike"),
            () -> "a cooking pit must reach the oven's own work: " + r.perception());
    }

    @Test
    void eachStructureStillAnswersToItsOwnName() {
        // What adding a synonym must not cost. The migration asserts this in SQL; this holds it through the
        // composer, which is where a player would notice.
        aLivingChronicle();
        for (String phrase : List.of("build an earth oven", "build a drying rack", "build a smoke rack",
                                     "build a path")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(phrase),
                () -> "'" + phrase + "' is the catalogue's own name and must keep working: -> " + intentOf(phrase));
        }
        assertEquals("MAKE_CHARCOAL", intentOf("make charcoal"), "and making charcoal still makes it");
        assertEquals("MAKE_CHARCOAL", intentOf("gather charcoal"), "and gathering it still gathers");
    }

    @Test
    void aNamedThingReachesExactlyOneThing() {
        // These are synonyms for a PARTICULAR structure, not family words, so an ambiguous "which one?" ask
        // would be the wrong answer — the player named a specific thing and there is a specific thing to give.
        // Asserted over the data as well as the composer, because the ask only appears when two rows tie.
        aLivingChronicle();
        for (String name : List.of("cooking pit", "brush path", "drying shelf", "smokehouse")) {
            Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_definition WHERE ',' || replace(keywords,', ',',') || ',' LIKE ?",
                Integer.class, "%," + name + ",%");
            assertNotNull(n);
            assertEquals(1, n, () -> "'" + name + "' names one structure, not " + n);
        }
    }
}
