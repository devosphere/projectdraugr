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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plainest words for what the world does (#37).
 *
 * <p>Five acts of play, typed the way a person types, found the same thing over and over: the mechanic was
 * already built, wired and narrated, and the sentence a person would actually use to ask for it reached nothing.
 * Not one line of this fix adds a capability. Every one of these acts already existed and already worked from
 * some other phrasing:
 *
 * <pre>
 *   sit by the fire            -> nothing, while "warm my hands" warmed you
 *   get out of the wind        -> nothing, while "take shelter" sheltered you
 *   wash the blood off         -> nothing, while "wash my hands" washed them
 *   build up the fire          -> nothing, while "feed the fire" fed it
 *   take the hide              -> nothing, while "skin the deer" took it
 *   lay in more wood           -> nothing, while "gather firewood" gathered it
 *   what do I need most        -> nothing, while "how am I doing" answered
 *   take my boots off          -> nothing, while "take off my boots" removed them
 * </pre>
 *
 * <p>The last one is the shape of the whole batch in miniature: English prefers the separable order for a
 * particle verb, and the classifier knew only the joined one.
 *
 * <p>Two of the fixes are upstream corrections the sweep itself turned up, not widenings. "take the lid off" was
 * already a phrase the container rule knew — and it then asked for a SECOND container noun in the same sentence,
 * so it worked only if you also said "pot". "take the bark off this birch" was not one of the six verbs the bark
 * rule knew. Both were reaching an unequip, and an unequip answers <i>you are not wearing a lid</i>, which is the
 * confidently wrong answer this project triages above the missing one.
 *
 * <p><b>Asserted in both directions, in the same test.</b> Widening a rule to catch one phrase reliably steals
 * another, so every phrase that already had a better claim is asserted to keep it — the lid to the container, the
 * bark to the tree, the hide to the carcass, and the yield words standing as the MATERIAL of a made thing
 * ("store the hide sack") to storage. Skips without Docker.
 */
@SpringBootTest
class ThePlainestWordsForWhatTheWorldDoesIntegrationTest {

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

    /** This class resolves forty sentences in a row, and the suite shares one clock. Pinned and put back. */
    private Timestamp clockWas;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        // The body keeps pace with the clock it is moved to, or the Chronicle starves between one sentence and
        // the next. Forty resolves is a long way to go on whatever the previous class left behind.
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
    void thePhrasingAPersonActuallyUsesReachesTheActThatAlreadyExisted() {
        awaken();
        Map<String, String> plain = new LinkedHashMap<>();
        // Warming yourself at a fire. "warm my hands" worked; nobody says that when they sit down.
        plain.put("sit by the fire", "WARM_BODY");
        plain.put("rest by the fire", "WARM_BODY");
        plain.put("rub my hands together", "WARM_BODY");
        // Getting out of the weather. Found in a snowstorm on high ground, where it mattered.
        plain.put("get out of the wind", "SHELTER_BODY");
        plain.put("find shelter", "SHELTER_BODY");
        plain.put("get inside", "SHELTER_BODY");
        // Washing after a kill.
        plain.put("wash the blood off", "WASH");
        // Feeding a fire under pressure, with wolves close.
        plain.put("build up the fire against them", "FEED_FIRE");
        // Naming the yield is naming the act. Every phrasing the rule knew named the animal instead.
        plain.put("take the hide", "HARVEST_CARCASS");
        plain.put("keep the antlers", "HARVEST_CARCASS");
        // Laying in fuel before a storm is gathering it.
        plain.put("lay in more wood", "GATHER_BRANCHES");
        // Asking the body a question the body can answer.
        plain.put("what do I need most", "SENSE_BODY");
        plain.put("am I freezing", "SENSE_BODY");
        // The separable particle, which English prefers.
        plain.put("take my boots off", "UNEQUIP");
        plain.put("take my cloak off", "UNEQUIP");
        plain.put("pull my boots off", "UNEQUIP");
        plain.put("take them off", "UNEQUIP");
        // The two upstream claims the sweep turned up, each of which was answering with an unequip.
        plain.put("take the lid off", "OPEN_CONTAINER");
        plain.put("take the bark off", "STRIP_BARK");
        plain.put("take off the bark", "STRIP_BARK");
        eachReaches(plain);
    }

    @Test
    void andNothingThatAlreadyHadABetterClaimLosesIt() {
        awaken();
        Map<String, String> held = new LinkedHashMap<>();
        // The phrasings each widened rule was built around must be untouched.
        held.put("warm my hands", "WARM_BODY");
        held.put("take shelter", "SHELTER_BODY");
        held.put("wash my hands", "WASH");
        held.put("feed the fire", "FEED_FIRE");
        held.put("skin the deer", "HARVEST_CARCASS");
        held.put("gather firewood", "GATHER_BRANCHES");
        held.put("how am I doing", "SENSE_BODY");
        held.put("take off my boots", "UNEQUIP");
        held.put("open the pot", "OPEN_CONTAINER");
        // The fire has four other acts, and "build up"/"more wood on" must not take any of them.
        held.put("light a fire", "LIGHT_FIRE");
        held.put("bank the fire for the night", "BANK_FIRE");
        held.put("put the fire out", "EXTINGUISH_FIRE");
        // "sit/stand/stay/rest by the fire" is gated on the fire, so the bare words stay where they were.
        held.put("rest", "REST");
        held.put("sleep", "SLEEP");
        held.put("take a drink", "DRINK");
        held.put("tend the crop", "WEED_CROP");
        held.put("gather stones", "GATHER_STONE");
        // The yield words standing as the MATERIAL of a made thing are a take of the thing, not of a carcass.
        // This is the whole reason the yield rule wants the noun to END the phrase or be followed by off/from.
        held.put("store the hide sack", "STORE");
        held.put("put the skin bag away", "STORE");
        held.put("equip my hide cloak", "EQUIP");
        // And a trailing "off" belongs to whoever has the better claim, which the unequip rule is last to ask.
        held.put("take the hide off", "HARVEST_CARCASS");
        eachReaches(held);

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
