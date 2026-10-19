package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.people.ContactService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asking whether anyone is there (#37, #109).
 *
 * <p><b>The people ACTS were wired and the people QUESTIONS were not.</b> {@code greet them} and
 * {@code trade with them} both reached the people system and were honestly refused — "there are none within
 * reach of this ground — no isle on it or on any beside it, and nobody within hail" — while every question
 * about presence reached the generic miss:
 *
 * <pre>
 *   are there people nearby -&gt; the generic miss   |  greet them     -&gt; the people refusal
 *   who lives here          -&gt; the generic miss   |  trade with them -&gt; the people refusal
 *   is anyone about         -&gt; the generic miss
 *   am I alone              -&gt; the generic miss
 *   are they friendly       -&gt; the generic miss   |  do they trust me -&gt; CHECK_STANDING
 *   what do they want       -&gt; the generic miss
 *   approach the people     -&gt; the generic miss   |  approach them   -&gt; the contact act
 *   call out                -&gt; the generic miss   |  call out to them -&gt; the contact act
 * </pre>
 *
 * <p><b>Why the presence questions are not simply added to {@link ContactService#recognise}.</b> Making a
 * question into a contact act would be asking-is-not-doing again — a player would be announcing themselves to an
 * isle for wondering whether one was there. So they are answered honestly BOTH ways instead: with an isle in
 * reach the survey already carries the glimpse ("Firelight moves between reed houses on the raised ground around
 * you, and low voices carry over the water"), and with none, the people refusal is already the exact answer.
 *
 * <p>Both branches are exercised here, the second by standing the Chronicle on a community's own ground —
 * otherwise the isle-present half would be code nothing had ever run.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AskingWhetherAnyoneIsThereIntegrationTest {

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
    @Autowired ContactService contact;
    @Autowired JdbcTemplate jdbc;

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

    /** Ground with no community within reach, so the "nobody within hail" half can be measured. */
    private void standWhereNobodyIs(UUID me) {
        UUID empty = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE NOT EXISTS ("
            + "  SELECT 1 FROM native_community n JOIN world_chunk home ON home.id=n.home_chunk_id "
            + "   WHERE n.lifecycle <> 'DISPERSED' AND abs(home.grid_x-c.grid_x)<=1 AND abs(home.grid_y-c.grid_y)<=1) "
            + "LIMIT 1", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        Assumptions.assumeTrue(empty != null, "this world is all within hail of somebody");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", empty, me);
    }

    /** A community's own ground, so the isle-present half can be measured rather than assumed. */
    private boolean standOnAnIsle(UUID me) {
        UUID home = jdbc.query(
            "SELECT n.home_chunk_id FROM native_community n JOIN cognition_profile cp ON cp.species_key=n.species_key "
            + "WHERE n.lifecycle <> 'DISPERSED' AND cp.cognition_class IN ('PEOPLE','SOVEREIGN') LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (home == null) return false;
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", home, me);
        return contact.communityInReach(home) != null;
    }

    @Test
    void withNobodyWithinHailTheAnswerSaysSo() {
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        for (String question : List.of("are there people nearby", "who lives here", "is anyone about", "am I alone")) {
            String prose = said(question);
            assertTrue(prose.contains("no isle") || prose.contains("within hail") || prose.contains("no people"),
                () -> "'" + question + "' must be answered about PEOPLE, not with the generic miss: " + prose);
            // The generic miss is what it used to get, and its wording is the thing to keep out.
            assertFalse(prose.contains("hands find no purchase") || prose.contains("nothing within reach takes the shape"),
                () -> "'" + question + "' fell through to the generic miss: " + prose);
        }
    }

    @Test
    void theirDispositionIsTheStandingQuestion() {
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        for (String question : List.of("are they friendly", "what do they want", "can I trust them")) {
            String prose = said(question);
            assertTrue(prose.contains("no people") || prose.contains("no isle") || prose.contains("view of you"),
                () -> "'" + question + "' is a question about standing: " + prose);
        }
    }

    @Test
    void approachingAndCallingOutAreActsAgain() {
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        // With nobody there, both are recognised acts refused honestly — which is itself the proof that they are
        // recognised at all. "approach them" and "call out to them" were known; the plainer forms were not.
        for (String phrase : List.of("approach the people", "call out", "go over to them")) {
            String prose = said(phrase);
            assertTrue(prose.contains("no isle") || prose.contains("within hail"),
                () -> "'" + phrase + "' must reach the people system: " + prose);
        }
    }

    @Test
    void withAnIsleInReachThePresenceQuestionShowsIt() {
        // THE BRANCH THAT WOULD OTHERWISE NEVER RUN. Standing on a community's own ground, the survey carries the
        // glimpse, which is the direct answer to "who lives here".
        UUID me = aLivingChronicle();
        Assumptions.assumeTrue(standOnAnIsle(me), "this world generated no people to stand among");
        String prose = said("who lives here");
        assertFalse(prose.contains("no isle") || prose.contains("within hail"),
            () -> "standing among people, the answer must not say there are none: " + prose);
        assertTrue(prose.contains("reed") || prose.contains("voices") || prose.contains("houses")
                   || prose.contains("firelight") || prose.contains("isle"),
            () -> "and must say something of the people whose ground this is: " + prose);
    }

    @Test
    void theActsThatAlreadyWorkedAreUntouched() {
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        for (String phrase : List.of("greet them", "trade with them", "how do they regard me")) {
            String prose = said(phrase);
            assertFalse(prose.contains("hands find no purchase"),
                () -> "'" + phrase + "' kept its own answer before this and must keep it: " + prose);
        }
    }
}
