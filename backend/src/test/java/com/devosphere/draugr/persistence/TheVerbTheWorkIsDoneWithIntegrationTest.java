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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The verb the work is actually done with (#37).
 *
 * <p>Eight gather rules, each of which had grown its own three or four verbs over as many tickets, and no two
 * lists the same. Swept live — thirteen verbs a person would use against seventeen things the world actually
 * grows or holds — <b>114 of 221 verb/object pairs reached nothing</b>:
 *
 * <pre>
 *   gather reeds   -> GATHER_PLANT        cut reeds    -> nothing   (and a blade is how reeds are taken)
 *   pick mushrooms -> GATHER_PLANT        pick stones  -> nothing
 *   dig clay       -> GATHER_CLAY         dig a root   -> nothing
 *   gather bark    -> STRIP_BARK          harvest bark -> nothing
 * </pre>
 *
 * <p>One shared clause now answers for all of them — <b>one rule, one place</b>, or they drift apart again. After
 * it, 44 pairs remain dead and every one is a verb held out on purpose: <i>take</i> and <i>get</i> mean a dozen
 * other things ("take my stone axe" is not a request to gather stones), <i>grab</i> reaches the take of what lies
 * here and should, and <i>crop</i> is the noun for a sown stand.
 *
 * <p>The sweep also turned up two <b>confidently wrong</b> answers, which this project triages above the missing
 * one, and both were substring collisions of exactly the kind #37 keeps finding:
 *
 * <pre>
 *   crop the watercress  ->  WATER_CROP   because "watercress" contains "water"
 *   get dry grass        ->  DRY_BODY     because "get dry" is a prefix of it
 * </pre>
 *
 * <p>Asserted in both directions. Felling and coppicing claim "cut down" and "cut rods" above the gathers, bark
 * has its own rule and is excluded from the plant gather, and each of those is asserted here — because widening a
 * rule to catch one phrase reliably steals another. Skips without Docker.
 */
@SpringBootTest
class TheVerbTheWorkIsDoneWithIntegrationTest {

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
    void everyGatherAnswersToTheSameVerbs() {
        awaken();
        Map<String, String> plain = new LinkedHashMap<>();
        // The verb you hold a blade to do, across the families that grow.
        for (String object : List.of("reeds", "bulrushes", "nettles", "meadow grass", "watercress")) {
            plain.put("cut " + object, "GATHER_PLANT");
            plain.put("pull " + object, "GATHER_PLANT");
            plain.put("snip " + object, "GATHER_PLANT");
        }
        // And "pick", which worked for mushrooms and for nothing else.
        plain.put("pick stones", "GATHER_STONE");
        plain.put("pick branches", "GATHER_BRANCHES");
        plain.put("pick plant fiber", "GATHER_FIBER");
        plain.put("pick clay", "GATHER_CLAY");
        plain.put("pick a stone slab", "GATHER_STONE_SLAB");
        // Digging, which is how a root and a lump of clay both come up.
        plain.put("dig up a root", "GATHER_PLANT");
        plain.put("dig clay", "GATHER_CLAY");
        // Bark shares the clause too, having known only six verbs of its own.
        plain.put("harvest bark", "STRIP_BARK");
        plain.put("take bark from the birch", "STRIP_BARK");
        eachReaches(plain);
    }

    @Test
    void andTheRulesWithABetterClaimOnThoseVerbsKeepIt() {
        awaken();
        Map<String, String> held = new LinkedHashMap<>();
        // Felling and coppicing are above the gathers and own their own phrasings of "cut".
        held.put("cut down the tree", "FELL_TREE");
        held.put("coppice the hazel", "COPPICE");
        held.put("cut rods from the hazel", "COPPICE");
        // Bark has its own rule, so the plant gather is explicitly told to leave it alone — without that, "cut
        // the bark off this birch" became a plant gather the moment "cut" joined the clause.
        held.put("cut the bark off this birch", "STRIP_BARK");
        held.put("strip the bark", "STRIP_BARK");
        held.put("peel the bark", "STRIP_BARK");
        // Digging out a structure is building it, not gathering.
        held.put("dig a latrine", "BUILD_LATRINE");
        held.put("dig a fire pit", "BUILD_FIRE_PIT");
        // Taking up what lies here is a take, not a gather.
        held.put("pick up the axe", "PICK_UP");
        // And the farming pair, which share these words.
        held.put("harvest the crop", "HARVEST_CROP");
        held.put("water the crops", "WATER_CROP");
        // Drawing water is its own act.
        held.put("collect water", "COLLECT_WATER");
        // Drying yourself is still drying yourself.
        held.put("dry myself off", "DRY_BODY");
        held.put("get dry", "DRY_BODY");
        eachReaches(held);

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void andTwoWrongAnswersAreNoLongerGiven() {
        awaken();
        // "watercress" contains "water", so asking to crop it was answered by watering a field. The rule now asks
        // for water as a WORD. The phrase is a plant gather, which is what it always was.
        ChronicleActionService.ActionResult cress = actions.resolve("crop the watercress");
        assertNotEquals("WATER_CROP", cress.intent(),
            () -> "cropping watercress is not watering a field: " + cress.intent() + " / " + cress.perception());

        // "get dry" is a prefix of "get dry grass", so asking for tinder was answered by drying yourself off.
        // Dry grass is in the plant gather's nouns, and could never be reached while this rule took the sentence.
        ChronicleActionService.ActionResult grass = actions.resolve("get dry grass");
        assertNotEquals("DRY_BODY", grass.intent(),
            () -> "dry grass is tinder, not a towel: " + grass.intent() + " / " + grass.perception());
    }
}
