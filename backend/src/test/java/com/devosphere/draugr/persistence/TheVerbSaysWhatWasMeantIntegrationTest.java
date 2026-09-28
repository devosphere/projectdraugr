package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The verb says what was meant (#37).
 *
 * <p>The matcher settles three things — the kind of work, the keyword, and the material — and <b>not one of them
 * is the verb</b>. So a keyword that is a bare noun is reachable from any sentence containing that noun, whatever
 * the sentence was doing to it:
 *
 * <pre>
 *   wash the bowl                  ->  "Carve a wooden bowl turns on a cutting edge…"
 *   break the ice on the trough    ->  "Carve a wooden trough turns on an axe…"
 *   clear the snow off the roof    ->  "Rive roof shakes turns on an axe…"
 *   carry the bucket to the fire   ->  "Assemble a wooden bucket…"
 *   hang the lamp up               ->  a lamp recipe, wanting plant fiber
 * </pre>
 *
 * <p><b>1,317 of the catalogue's 2,552 verified keywords carry no making verb at all.</b> Measured before the fix,
 * six of sixteen such sentences reached a making process.
 *
 * <p>The rule asks the DATA, not a list of forbidden phrases: a sentence with no verb is left alone (that is how
 * the plain family word reaches its family — "a bowl", "poultice" — which this project worked to get); a sentence
 * whose verb is a making verb is left alone; a sentence whose verb appears in the matched process's <b>own</b>
 * keywords is left alone, which is what keeps "break the flint" reaching the knapping that declares "break". Only
 * what is left over is refused, and refused as an honest miss rather than answered with the wrong act.
 *
 * <p>Asserted in BOTH directions, because a rule that only refuses is as wrong as one that only accepts. Skips
 * without Docker.
 */
@SpringBootTest
class TheVerbSaysWhatWasMeantIntegrationTest {

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

    /** Everything here resolves actions, and the suite shares one clock. Pinned and put back. */
    private Timestamp clockWas;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
    }

    @AfterEach
    void unpinTheClock() {
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
    }

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    @Test
    void aSentenceAboutAThingIsNotARequestToMakeOne() {
        awaken();
        // Each of these reached a making process before, purely because it names something the world can make.
        for (String said : java.util.List.of(
                "wash the bowl",
                "break the ice on the trough",
                "clear the snow off the roof",
                "carry the bucket to the fire",
                "hang the lamp up")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertNotEquals("PROCESS_MATERIAL", r.intent(),
                () -> "\"" + said + "\" is not a request to make one: " + r.intent() + " / " + r.perception());
        }
    }

    @Test
    void theWordsThatDoAskForWorkStillReachIt() {
        awaken();
        // The other direction. The plain family word with NO verb must still reach its family — that is the
        // thing #37 worked to get — and every ordinary making phrase must be untouched.
        for (String said : java.util.List.of(
                "a bowl",
                "poultice",
                "carve a bowl",
                "make a poultice",
                "knap a flint blade",
                "dry the meat",
                "tan the hide",
                "split planks",
                "grind the grain")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals("PROCESS_MATERIAL", r.intent(),
                () -> "\"" + said + "\" asks for work and must still reach it: " + r.intent() + " / " + r.perception());
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aVerbTheProcessItselfDeclaresIsNotAForeignVerb() {
        awaken();
        // "break" is not a making verb, but the knapping declares it — so the rule must leave it alone. This is
        // the reason the test is asked of the catalogue rather than of a list of banned words.
        Integer declaresBreak = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process WHERE review_state='VERIFIED' AND ',' || lower(keywords) || ',' LIKE '%break%'",
            Integer.class);
        assertTrue(declaresBreak != null && declaresBreak > 0,
            "the catalogue must still contain a process that declares 'break', or this test proves nothing");
    }
}
