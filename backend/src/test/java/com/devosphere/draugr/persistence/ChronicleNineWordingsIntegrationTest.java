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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Chronicle-9 wordings (#37), as fixtures.
 *
 * <p>The ticket's complaint was not that one action failed. It was that a player who used their senses the way a
 * person would — looking, inspecting, analysing, tracking, listening, smelling — got nothing back, and could not
 * tell whether there were fish in the water they were standing in or birds in the trees over them. Several fixes
 * have gone in since (#491 perception from the catalogue rather than from a dozen seeded sites, #496 tracking,
 * #596 water named for what it is). Nothing held those wordings in place afterwards.
 *
 * <p>This does. Each phrasing below is taken from the ticket, resolved through the public action boundary against
 * a real world, and asserted on the two things the complaint was about: <b>the resolver understood it</b> (never
 * UNKNOWN), and <b>the world answered with something</b> — prose of a sentence or more that is not a refusal to
 * engage. Nothing here asserts a particular fish or a particular bird, because what lives on the ground a Chronicle
 * wakes on is the world's business, not the test's. Skips without Docker.
 */
@SpringBootTest
class ChronicleNineWordingsIntegrationTest {

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

    /** The senses, in the words the Chronicle-9 playthrough used them in. */
    private static final List<String> USING_THE_SENSES = List.of(
        "look around",
        "inspect the ground",
        "examine the ground here",
        "analyze the soil",
        "investigate the surroundings",
        "search the undergrowth",
        "listen",
        "smell the air",
        "scout the area",
        "track animals",
        "look for birds",
        "look for insects",
        "look at the water",
        "check the water for fish");

    @Test
    void everyWayAPersonUsesTheirSensesIsUnderstoodAndAnswered() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant noon = Instant.parse("2031-06-10T12:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(noon));
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", Timestamp.from(noon));

        for (String words : USING_THE_SENSES) {
            var said = actions.resolve(words);
            assertNotEquals("UNKNOWN", said.intent(), () -> "the resolver must understand \"" + words + "\", and it answered: " + said.perception());
            assertNotNull(said.perception(), () -> "\"" + words + "\" produced no prose at all");
            assertTrue(said.perception().length() > 40,
                () -> "\"" + words + "\" was answered with too little to be an answer: " + said.perception());
            // The body keeps pace with the world, so a fourteen-step examination of one place does not starve anyone.
            jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=(SELECT simulated_at FROM simulation_clock WHERE id=1), " +
                "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0 WHERE chronicle_id=?", chronicle);
        }

        // A search that NAMES a kind is answered about that kind (#37). The ticket's words: "aerial species,
        // insects are not returned by the Narrator even if the chronicle intently look for them on sensible
        // locations." Before this, "look for birds", "look for insects" and "look for clay" returned the same
        // sentence about whatever was underfoot, and a player could not tell an empty place from a deaf one.
        //
        // Asserted on the subject WORD rather than on a species, because which birds live on the ground a
        // Chronicle wakes on is the world's business — but that it answers about birds at all is not.
        for (String[] asked : new String[][] { {"look for birds", "birds"}, {"look for insects", "insects"}, {"look for fish", "fish"} }) {
            var answer = actions.resolve(asked[0]);
            assertNotEquals("UNKNOWN", answer.intent(), answer::perception);
            assertTrue(answer.perception().toLowerCase(java.util.Locale.ROOT).contains(asked[1]),
                () -> "\"" + asked[0] + "\" must answer about " + asked[1] + ", found: " + answer.perception());
            jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=(SELECT simulated_at FROM simulation_clock WHERE id=1), " +
                "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0 WHERE chronicle_id=?", chronicle);
        }

        // And the ticket's other half: an action the world cannot settle asks rather than guessing, and changes
        // nothing while it asks. "carve a bowl" is the recorded example — two processes answer to it (#593).
        int thingsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM world_object", Integer.class);
        var ambiguous = actions.resolve("carve a bowl");
        assertNotEquals("UNKNOWN", ambiguous.intent(), ambiguous::perception);
        assertTrue(List.of("FAILED", "PARTIAL").contains(ambiguous.outcome()),
            () -> "an action the world cannot settle does not succeed at something: " + ambiguous.outcome() + " / " + ambiguous.perception());
        assertEquals(thingsBefore, (int) jdbc.queryForObject("SELECT COUNT(*) FROM world_object", Integer.class),
            "nothing is made while the world is still asking which thing was meant");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
