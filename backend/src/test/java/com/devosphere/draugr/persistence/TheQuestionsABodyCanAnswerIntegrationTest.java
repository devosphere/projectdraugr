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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The questions a body can answer (#37).
 *
 * <p>Act fourteen swept the one domain the earlier acts touched and never swept: what a person says <b>about
 * themselves</b>. <b>28 of 41 reached nothing</b> — while {@code am I ill} answered, and answered well:
 *
 * <pre>
 *   You take stock of yourself. There is a hurt in you that has not closed, the cold has got past…
 * </pre>
 *
 * <p><b>So the body knew. It was the asking that failed.</b> Every question below asks after something
 * {@code chronicle_physiology} tracks by name — {@code injury_severity}, {@code blood_loss_ml},
 * {@code pain_level}, {@code illness_severity}, {@code core_temperature_c}, {@code wetness_level},
 * {@code sleep_debt_hours}, {@code energy_level} — and that {@code bodyReading} already says out loud, down to
 * <i>"the wet has got through your clothes"</i> and <i>"your legs are going out from under you"</i>. No new
 * capability, no migration: a routing widening and nothing else.
 *
 * <p>The treatment half needed a clause of its own. The existing rule knew bind, bandage, dress, clean, tend,
 * treat, see to and wash — against <i>wound, injury, bleeding, cut, gash</i>. So "splint my leg" and "cauterise
 * it" reached nothing over a mechanism that works perfectly from "bind the wound", because <b>nothing in them is
 * a word for a wound</b>: they name the PART. And it sits above EQUIP, which otherwise answered "splint my leg"
 * with <i>"you have nothing unequipped that can be worn or wielded"</i>.
 *
 * <p><b>28 → 7</b>, and each of the seven is genuinely unmodelled rather than unrouted — wound infection (the
 * word does not occur anywhere in the backend), broken bones, healing time, pain relief, sweating out a fever,
 * and whether a hurt is going to kill you. Those want a model, not a phrase, and are recorded on the ticket as
 * such. Two wrong answers also stay on the ticket, because both need the OBJECT rather than the verb:
 * {@code how deep is it} over a wound is answered by sounding for water, and {@code sling my arm} equips
 * whatever is to hand.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheQuestionsABodyCanAnswerIntegrationTest {

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

    private void eachReaches(Map<String, String> expected) {
        expected.forEach((said, intent) -> {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals(intent, r.intent(),
                () -> "\"" + said + "\" must reach " + intent + ", not " + r.intent() + " / " + r.perception());
        });
    }

    @Test
    void theBodyAnswersTheQuestionsItHasAlwaysKnownTheAnswersTo() {
        awaken();
        Map<String, String> asked = new LinkedHashMap<>();
        // The wound.
        asked.put("how bad is the cut", "SENSE_BODY");
        asked.put("am I bleeding still", "SENSE_BODY");
        asked.put("is the bleeding stopped", "SENSE_BODY");
        asked.put("is it swelling", "SENSE_BODY");
        asked.put("can I walk on it", "SENSE_BODY");
        // Illness and fever.
        asked.put("how sick am I", "SENSE_BODY");
        asked.put("do I have a fever", "SENSE_BODY");
        // The cold and the wet, both of which the reading already names.
        asked.put("am I getting cold", "SENSE_BODY");
        asked.put("are my feet wet", "SENSE_BODY");
        asked.put("is my nose going numb", "SENSE_BODY");
        // Being spent.
        asked.put("how tired am I", "SENSE_BODY");
        asked.put("can I keep going", "SENSE_BODY");
        eachReaches(asked);
    }

    @Test
    void andTheActsOverAMechanismThatAlreadyWorked() {
        awaken();
        Map<String, String> acts = new LinkedHashMap<>();
        // Treating a hurt where the sentence names the PART, not the hurt.
        acts.put("splint my leg", "TREAT_WOUND");
        acts.put("stitch the cut", "TREAT_WOUND");
        acts.put("cauterise it", "TREAT_WOUND");
        acts.put("change the dressing", "TREAT_WOUND");
        acts.put("press on it to stop the bleeding", "TREAT_WOUND");
        // Getting dry, and getting the feeling back.
        acts.put("wring out my clothes", "DRY_BODY");
        acts.put("change into dry clothes", "DRY_BODY");
        acts.put("rub the feeling back into my hands", "WARM_BODY");
        // And sitting down, which is the one act in the sweep that had an intent and no words reaching it.
        acts.put("sit down for a while", "REST");
        eachReaches(acts);

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void andNothingWithABetterClaimLosesIt() {
        awaken();
        Map<String, String> held = new LinkedHashMap<>();
        // Washing is washing; the wound rule must not take it.
        held.put("wash my hands", "WASH");
        // The hide and the fish are processes, and "clean"/"dress"/"stitch"/"press" are their words too.
        held.put("clean the fish", "PROCESS_MATERIAL");
        held.put("stitch a linen shift", "PROCESS_MATERIAL");
        held.put("sew a linen shift", "PROCESS_MATERIAL");
        held.put("press the oil", "PROCESS_MATERIAL");
        // Sounding water for its depth is a measure; the body questions must not take it.
        held.put("how deep is the water", "MEASURE");
        held.put("measure the depth of the stream", "MEASURE");
        // The plain words that already worked.
        held.put("how am I doing", "SENSE_BODY");
        held.put("am I ill", "SENSE_BODY");
        held.put("bind the wound", "TREAT_WOUND");
        held.put("clean the cut", "TREAT_WOUND");
        held.put("warm my hands", "WARM_BODY");
        held.put("dry off", "DRY_BODY");
        held.put("get dry", "DRY_BODY");
        held.put("rest", "REST");
        held.put("wait", "REST");
        // And dressing and wearing are still equipping.
        held.put("equip my cloak", "EQUIP");
        held.put("wear my boots", "EQUIP");
        held.put("put on my boots", "EQUIP");
        eachReaches(held);
    }

    @Test
    void andWhatIsUnmodelledIsNotAnsweredApproximately() {
        awaken();
        // Reporting the negative, which this project counts as a deliverable. These are not routing gaps: wound
        // INFECTION does not occur anywhere in the backend, nor do broken bones, healing time or dying of a
        // hurt. A body report that did not mention any of them would be a near-miss dressed as an answer, so
        // they are left as honest misses until there is a model behind them.
        for (String said : List.of("is it infected", "is the bone broken", "how long will this take to heal")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals("UNKNOWN", r.intent(),
                () -> "\"" + said + "\" has no model behind it and must stay an honest miss, not be answered "
                    + "approximately: " + r.intent() + " / " + r.perception());
        }
        Integer infection = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE column_name ILIKE '%infect%'", Integer.class);
        assertEquals(0, infection,
            "if an infection column is ever added, these questions become routing gaps and belong in the test above");
    }
}
