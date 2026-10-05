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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The answer that was confidently wrong (#37, V401).
 *
 * <p>Four sentences answered in the same even voice as every true one, and each needed the <b>object</b> rather
 * than the verb to settle it:
 *
 * <pre>
 *   turn over the ground for insects  ->  TILL_GROUND   "Tillage wants open, workable ground…"
 *   sling my arm                      ->  EQUIP         and with something to hand it equipped a digging stick
 *   carve a yoke                      ->  MARK          "You set a hand to the bark, but with no blade…"
 *   make a hot drink                  ->  DRINK         answered by looking round for water to swallow
 * </pre>
 *
 * <p>The first is the sharper one than it looks: <b>that exact phrase is what the colony depletion test hands
 * {@code collectInsects} directly</b>, so the grub hunt was reachable in a test and unreachable in play, and
 * what a player got instead was a seedbed.
 *
 * <p>Each fix is the object. The tillage keeps its verb and its ground and yields when the sentence says
 * insects; the sling keeps the weapon and yields when the sentence names an arm; the yoke's own recipe learns
 * the word a person uses for it; and a sentence carrying "drink" is no longer a drink when it carries a making
 * verb too.
 *
 * <p><b>And the hot drink was the SUBJECT, not the category.</b> I recorded the category gate first and was
 * wrong: the category is already a hint rather than a gate — {@code resolveAndRecord} has fallen back to the
 * whole catalogue since #721 when the guessed category answers nothing. What refuses a sentence is the
 * SUBJECT: <i>"right work, right verb, wrong material"</i>, in the matcher&#39;s own words.
 * {@code brew_infusion}&#39;s subjects were {@code infusion} and {@code tea}, and "a hot drink" names neither —
 * it names a PROPERTY of the thing rather than the thing. Adding {@code brew}, {@code steep} and
 * {@code infuse} as PROCESS category terms changed nothing, measured, because the category was never what
 * stopped it. Two subject rows were.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheAnswerThatWasConfidentlyWrongIntegrationTest {

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
    void turningGroundForInsectsIsAGrubHuntAndNotTillage() {
        awaken();
        ChronicleActionService.ActionResult r = actions.resolve("turn over the ground for insects");
        assertNotEquals("TILL_GROUND", r.intent(),
            () -> "asked for insects and answered with a seedbed: " + r.intent() + " / " + r.perception());
        // This is the phrase ColonyDepletionAndPollinationIntegrationTest hands collectInsects directly, so the
        // mechanic was reachable in a test and not in play. Whatever it reaches, it must be about the insects.
        assertTrue(r.perception().toLowerCase(java.util.Locale.ROOT).contains("insect")
                || "COLLECT_INSECTS".equals(r.intent()),
            () -> "and the answer must be about what was asked for: " + r.intent() + " / " + r.perception());
    }

    @Test
    void aSlingOnAnArmIsATreatmentAndASlingAloneIsAWeapon() {
        awaken();
        assertEquals("TREAT_WOUND", actions.resolve("sling my arm").intent(),
            () -> "an arm in a sling is a treatment: " + actions.resolve("sling my arm").perception());
        // The object is the whole of the difference. "sling" with no body part stays the weapon's.
        assertNotEquals("TREAT_WOUND", actions.resolve("sling a stone").intent(),
            "a sling with a stone in it is not a treatment");
    }

    @Test
    void theYokeAnswersToTheWordAPersonUsesForIt() {
        awaken();
        ChronicleActionService.ActionResult r = actions.resolve("carve a yoke");
        assertEquals("PROCESS_MATERIAL", r.intent(),
            () -> "carving a yoke is making one, not blazing a trail: " + r.intent() + " / " + r.perception());
        // And the recipe it reaches is the yoke's own, which refuses for want of the right material.
        assertTrue(r.perception().toLowerCase(java.util.Locale.ROOT).contains("wooden")
                || r.perception().toLowerCase(java.util.Locale.ROOT).contains("yoke"),
            () -> "and it must be the yoke's recipe that answers: " + r.perception());
        // The marking intent keeps its own phrasings, which is why it was right to take the sentence before.
        assertEquals("MARK", actions.resolve("blaze a trail").intent(), "blazing a trail is still a marking");
    }

    @Test
    void aSentenceCarryingTheWordDrinkIsNotAlwaysADrink() {
        awaken();
        // Was answered by looking round for water to swallow. Now an honest miss, which this project counts as
        // better than a confident wrong answer — see the class note on why the keywords were not shipped.
        // Now reaches the infusion and refuses truthfully for want of heat, which is the real answer.
        ChronicleActionService.ActionResult hot = actions.resolve("make a hot drink");
        assertEquals("PROCESS_MATERIAL", hot.intent(),
            () -> "a request to MAKE one is a making, not a drink: " + hot.intent() + " / " + hot.perception());
        assertEquals("PROCESS_MATERIAL", actions.resolve("brew something hot").intent(),
            "and so is brewing something hot, which reached nothing at all before");
        // And drinking is still drinking.
        Map<String, String> held = new LinkedHashMap<>();
        held.put("take a drink", "DRINK");
        held.put("drink some water", "DRINK");
        held.put("have a drink", "DRINK");
        held.forEach((said, intent) -> assertEquals(intent, actions.resolve(said).intent(),
            () -> "\"" + said + "\" is still a drink: " + actions.resolve(said).intent()));
    }

    @Test
    void andTheRulesThatOwnedThoseVerbsKeepThem() {
        awaken();
        Map<String, String> held = new LinkedHashMap<>();
        held.put("till the ground", "TILL_GROUND");
        held.put("break the ground for a seedbed", "TILL_GROUND");
        held.put("plough the field", "TILL_GROUND");
        held.put("equip my cloak", "EQUIP");
        held.put("bind the wound", "TREAT_WOUND");
        held.forEach((said, intent) -> assertEquals(intent, actions.resolve(said).intent(),
            () -> "\"" + said + "\" must stay " + intent + ": " + actions.resolve(said).intent()));

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
