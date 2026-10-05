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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sentence is about its own object (#37).
 *
 * <p>The residual of #739, named on the ticket and now closed. A process names itself with a <b>bare noun</b> so
 * that the plain family word reaches its family — "shield", "comb", "hoe", "roof", "wedge", "stave", "skin",
 * "tar", "glue", "felt" — and that is a thing this project worked to get. But the guard read a bare noun in the
 * vocabulary as the process <i>declaring that verb</i>, so every one of those words answered a sentence about
 * something else with a recipe for itself.
 *
 * <p>Swept live: 46 one-word process keywords that are also plain English verbs, against three objects no other
 * rule claims. <b>46 of 138 sentences were answered by a recipe to make something.</b>
 *
 * <pre>
 *   shield the fire from the wind  ->  "Build a war shield turns on a cutting edge…"
 *   comb my hair                   ->  "Carve a bone comb turns on a cutting edge…"
 *   skin the fire                  ->  "Skin a fish turns on a cutting edge…"
 *   tar the path                   ->  "This work needs heat, and none is within reach…"
 *   roof the doorway               ->  "Rive roof shakes turns on an axe…"
 * </pre>
 *
 * <p>The fix asks the data one more question. <b>The verb alone is not enough to make the sentence this
 * process's: the sentence must also name something the process is about.</b> That is what keeps "break the
 * stone" reaching the dressing that declares both words, and "skin the fish" reaching the fish — and what stops
 * "skin the fire", where the verb fits and nothing else does. And a sentence that names nothing in the process's
 * vocabulary matched on the verb and nothing else, whatever that verb was, which closes the thirty-odd words the
 * hand-written plain-verb list of #739 had never heard of.
 *
 * <p>Two sentences are deliberately left alone: one of a single word, which is a <b>name</b> and not a verb
 * phrase ("shield" must still reach the shield); and one carrying no subject at all ("ret it"), because there is
 * nothing in it to contradict the process with.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheSentenceIsAboutItsOwnObjectIntegrationTest {

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

    private Timestamp clockWas;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "blood_loss_ml=0, energy_level=100");
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
    void aSentenceThatNamesNothingTheProcessIsAboutIsNotARequestToMakeIt() {
        awaken();
        // Each of these was answered by a recipe for the thing whose NAME its first word happens to be. The
        // objects are ones no other rule claims, so what is measured is the process matcher and nothing else.
        for (String said : List.of(
                "shield the fire from the wind",   // the residual named on #37 after #739
                "shield the doorway",
                "comb my hair",
                "skin the fire",
                "skin the path",
                "tar the path",
                "roof the doorway",
                "hoe the path",
                "rake the doorway",
                "wedge the doorway",
                "wall the path",
                "glue the doorway",
                "felt the path",
                "thatch the fire",
                "stave the doorway")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertNotEquals("PROCESS_MATERIAL", r.intent(),
                () -> "\"" + said + "\" names nothing that process is about: " + r.intent() + " / " + r.perception());
        }
    }

    @Test
    void butTheSentencesThatDoNameItStillReachIt() {
        awaken();
        // The other direction, which is the whole reason the rule asks about the OBJECT rather than banning the
        // verb. Each of these uses the same word the sentences above use, against the thing it belongs to.
        for (String said : List.of(
                "break the stone",      // the dressing declares both words
                "break the flax",
                "skin the fish",
                "tar the timber",
                "tar the cordage",
                "felt the wool",
                "glue the backing",
                "scoop ash",
                "rake the ashes",
                "thatch bundle",
                "rive shakes",
                "course dry stone",
                "haft a hoe",
                "carve a comb",
                "shape a wedge",
                "sew a linen shift")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals("PROCESS_MATERIAL", r.intent(),
                () -> "\"" + said + "\" names what the process is about: " + r.intent() + " / " + r.perception());
        }

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void andABareFamilyWordIsAName() {
        awaken();
        // A sentence of ONE word is a name, not a verb phrase, and several of these names are also plain verbs.
        // Reaching the family from the bare family word is a thing #37 worked to get, and the first cut of this
        // fix took "shield" away again — the sweep caught it.
        for (String said : List.of("shield", "comb", "hoe", "roof", "wedge", "skin", "poultice", "a bowl")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals("PROCESS_MATERIAL", r.intent(),
                () -> "the bare word \"" + said + "\" must still reach its family: " + r.intent() + " / " + r.perception());
        }
    }

    @Test
    void andTheRefusalsOf739AreUnchanged() {
        awaken();
        // This rule sits alongside #739's, so #739's own measured cases are asserted here too: a sentence that
        // DOES name the thing but opens with a verb the process never heard of is still refused.
        for (String said : List.of(
                "wash the bowl",
                "break the ice on the trough",
                "clear the snow off the roof",
                "carry the bucket to the fire",
                "hang the lamp up")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertNotEquals("PROCESS_MATERIAL", r.intent(),
                () -> "\"" + said + "\" is not a request to make one: " + r.intent() + " / " + r.perception());
        }
        // And #739's accepted cases.
        for (String said : List.of(
                "carve a bowl", "make a poultice", "knap a flint blade",
                "dry the meat", "tan the hide", "split planks", "grind the grain")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals("PROCESS_MATERIAL", r.intent(),
                () -> "\"" + said + "\" asks for work and must still reach it: " + r.intent() + " / " + r.perception());
        }
    }
}
