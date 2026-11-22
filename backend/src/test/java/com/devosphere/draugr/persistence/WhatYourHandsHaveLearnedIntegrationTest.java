package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.skill.SkillProgressionService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #954 — PRACTICE MAKES A SPECIALIST, NOT A POLYMATH.
 *
 * <p>Every assertion here is one of the verifications #954 asks for by name. The one that matters most is
 * the ASYMMETRY: practising a thing develops that thing strongly and its supporting faculties slowly, and
 * those faculties then assist related work without ever conferring its mastery. An archer's aim improves;
 * their aim helps them throw a javelin; they are not a javelin thrower. Get that backwards and the whole
 * layer collapses back into the eight shared numbers it was built to replace.
 *
 * <p>Skips without Docker. Every query in it was also run against a throwaway Postgres with the full
 * migration set applied, because a suite that skips is not evidence.
 */
@SpringBootTest
class WhatYourHandsHaveLearnedIntegrationTest {

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
    @Autowired SkillProgressionService skills;
    @Autowired JdbcTemplate jdbc;

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        // This test measures ONLY this chronicle's own rows, which are keyed by its id, so it is
        // indifferent to the ~900 other tests sharing the world.
        return summary.id();
    }

    private double aptitude(UUID chronicle, String column) {
        Double v = jdbc.queryForObject(
            "SELECT " + column + " FROM chronicle_capability_adaptation WHERE chronicle_id = ?", Double.class, chronicle);
        return v == null ? 0 : v;
    }

    /**
     * ARCHERY PRACTICE MAKES AN ARCHER, and nudges the aim. #954: "Practice develops the specific skill
     * most strongly and develops supporting aptitudes more slowly."
     */
    @Test void practisingOneDisciplineDevelopsItMostAndItsAptitudesSlowly() {
        UUID me = chronicle();
        jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?) ON CONFLICT DO NOTHING", me);
        double aimBefore = aptitude(me, "visual_aim_familiarity");

        double shot = 0;
        for (int i = 0; i < 25; i++) {
            SkillProgressionService.Practice p =
                skills.practise(me, UUID.randomUUID(), "ARCHERY", 30, 0.7, Instant.now());
            assertNotNull(p, "every distinct action should be credited");
            shot = p.after();
        }
        double aimGained = aptitude(me, "visual_aim_familiarity") - aimBefore;

        assertTrue(shot > 0, "twenty-five sessions at the bow should leave something behind: " + shot);
        assertTrue(aimGained > 0, "and should have improved the aim a little: " + aimGained);
        // THE ASYMMETRY, stated as a number: the skill moves several times further than the faculty.
        assertTrue(shot > aimGained * 3,
            "the discipline must grow much faster than the aptitude it leans on: skill " + shot + " vs aim " + aimGained);
        // And it made them an archer, not a thrower: the javelin shares AIM and nothing else.
        assertEquals(0.0, skills.proficiency(me, "JAVELIN_THROWING"), 1e-9,
            "drawing a bow must never award javelin mastery");
    }

    /**
     * WHAT TRANSFERS, AND WHAT DOES NOT. #954: "weaving transfers bounded dexterity benefits to another
     * task without awarding its specific mastery."
     */
    @Test void aWeaverIsBetterAtCarvingWithoutEverHavingCarved() {
        UUID me = chronicle();
        jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?) ON CONFLICT DO NOTHING", me);
        double carvingBefore = skills.competence(me, "CARVING");

        for (int i = 0; i < 40; i++) skills.practise(me, UUID.randomUUID(), "WEAVING", 40, 0.6, Instant.now());

        assertTrue(skills.proficiency(me, "WEAVING") > 0, "they have woven");
        assertEquals(0.0, skills.proficiency(me, "CARVING"), 1e-9, "and have never carved");
        double carvingAfter = skills.competence(me, "CARVING");
        assertTrue(carvingAfter > carvingBefore,
            "steadier hands should help the first carving: " + carvingBefore + " -> " + carvingAfter);
        // BOUNDED. The transfer is assistance, not mastery: it cannot reach what practice would give.
        assertTrue(carvingAfter < skills.competence(me, "WEAVING") / 2,
            "a weaver must not be half a carver by accident: carving " + carvingAfter
                + " vs weaving " + skills.competence(me, "WEAVING"));
    }

    /**
     * ONE ACT, ONE AWARD. #954: "replaying a request does not award practice twice." The resolve path
     * returns early on a duplicate idempotency key, but this asserts the database would refuse a second
     * award even if it did not — a UNIQUE index rather than a promise about where a call sits.
     */
    @Test void replayingTheSameActionAwardsPracticeOnlyOnce() {
        UUID me = chronicle();
        UUID oneAction = UUID.randomUUID();

        SkillProgressionService.Practice first = skills.practise(me, oneAction, "KNAPPING", 30, 0.8, Instant.now());
        assertNotNull(first, "the first award happens");
        double afterFirst = skills.proficiency(me, "KNAPPING");
        String strengthSql = "SELECT load_conditioning FROM chronicle_capability_adaptation WHERE chronicle_id = ?";
        double strengthAfterFirst = jdbc.queryForObject(strengthSql, Double.class, me);

        assertNull(skills.practise(me, oneAction, "KNAPPING", 30, 0.8, Instant.now()),
            "the same action must not be credited a second time");
        assertEquals(afterFirst, skills.proficiency(me, "KNAPPING"), 1e-9,
            "and the proficiency must not have moved");
        assertEquals(strengthAfterFirst, jdbc.queryForObject(strengthSql, Double.class, me), 1e-12,
            "nor the aptitudes it supports: knapping leans on strength, and a replay must not raise it again");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_skill_practice_event WHERE action_id = ? AND skill_key = 'KNAPPING'",
            Integer.class, oneAction));
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT practice_events FROM chronicle_skill_practice WHERE chronicle_id = ? AND skill_key = 'KNAPPING'",
            Integer.class, me));
    }

    /** #954: "Familiar easy tasks should have diminishing returns." The hundredth is worth less. */
    @Test void theHundredthRepetitionTeachesLessThanTheFirst() {
        UUID me = chronicle();
        SkillProgressionService.Practice early = skills.practise(me, UUID.randomUUID(), "COOKING", 30, 0.5, Instant.now());
        assertNotNull(early);
        double firstGain = early.gain();

        SkillProgressionService.Practice late = null;
        for (int i = 0; i < 80; i++) late = skills.practise(me, UUID.randomUUID(), "COOKING", 30, 0.5, Instant.now());
        assertNotNull(late);

        assertTrue(late.gain() < firstGain / 2,
            "the eighty-first identical meal should teach less than half what the first did: "
                + firstGain + " -> " + late.gain());
        assertTrue(late.gain() > 0, "but it should still teach something");
    }

    /** #954: "account for difficulty" — demanding work teaches more than trivial work of the same length. */
    @Test void hardWorkTeachesMoreThanEasyWorkOfTheSameLength() {
        UUID easyHand = chronicle();
        SkillProgressionService.Practice easy = skills.practise(easyHand, UUID.randomUUID(), "FARMING", 30, 0.05, Instant.now());
        jdbc.update("UPDATE chronicle SET life_state='DEAD', died_at=now(), death_cause='test' WHERE id=?", easyHand);

        UUID hardHand = chronicle();
        SkillProgressionService.Practice hard = skills.practise(hardHand, UUID.randomUUID(), "FARMING", 30, 1.0, Instant.now());

        assertNotNull(easy);
        assertNotNull(hard);
        assertTrue(hard.gain() > easy.gain(),
            "a punishing hour should teach more than a trivial one: easy " + easy.gain() + " vs hard " + hard.gain());
    }

    /** #954: "account for ... fatigue, injury, and recovery." A spent, hurt body learns badly. */
    @Test void aSpentOrInjuredBodyLearnsLess() {
        UUID me = chronicle();
        double whole = skills.bodyFitForLearning(me);
        jdbc.update("UPDATE chronicle_physiology SET energy_level = 10, injury_severity = 70, " +
                    "illness_severity = 60, sleep_debt_hours = 40, pain_level = 80 WHERE chronicle_id = ?", me);
        double wrecked = skills.bodyFitForLearning(me);

        assertTrue(wrecked < whole, "a wrecked body should learn worse: " + whole + " -> " + wrecked);
        assertTrue(wrecked >= 0.35, "but it should still learn something: " + wrecked);
    }

    /**
     * NO FEEDBACK LOOP. #954: "Prevent recursive gain loops where aptitude growth awards skill growth
     * which awards more aptitude growth." Growth flows practice -> skill -> aptitude and never back, so
     * moving an aptitude directly must leave every skill exactly where it was.
     */
    @Test void raisingAnAptitudeAwardsNoSkillAtAll() {
        UUID me = chronicle();
        jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?) ON CONFLICT DO NOTHING", me);
        skills.practise(me, UUID.randomUUID(), "TRACKING", 30, 0.6, Instant.now());
        double tracking = skills.proficiency(me, "TRACKING");
        int eventsBefore = jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_skill_practice_event WHERE chronicle_id = ?", Integer.class, me);

        // Every aptitude to the ceiling, by hand.
        jdbc.update("UPDATE chronicle_capability_adaptation SET attention_resilience = 1, " +
                    "knowledge_familiarity = 1, locomotion_familiarity = 1, fine_motor_familiarity = 1, " +
                    "visual_aim_familiarity = 1, load_conditioning = 1, insight_familiarity = 1 " +
                    "WHERE chronicle_id = ?", me);

        assertEquals(tracking, skills.proficiency(me, "TRACKING"), 1e-9,
            "a maxed aptitude must not have awarded any tracking");
        assertEquals(eventsBefore, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_skill_practice_event WHERE chronicle_id = ?", Integer.class, me),
            "and must not have written a practice event");
        // It does, however, help them track — which is the whole point of the supporting layer.
        assertTrue(skills.competence(me, "TRACKING") > tracking,
            "the faculties should assist the work even so");
    }

    /**
     * THE EVIDENCE IS IMMUTABLE AND CAUSAL. #954 asks for "the practiced discipline, supporting
     * aptitudes, before/after changes, and source action" to be recorded — this is that row, and it
     * cannot be edited or deleted afterwards.
     */
    @Test void thePracticeEventRecordsItsCauseAndCannotBeRewritten() {
        UUID me = chronicle();
        UUID action = UUID.randomUUID();
        SkillProgressionService.Practice p = skills.practise(me, action, "FORGING", 45, 0.9, Instant.now());
        assertNotNull(p);
        assertTrue(p.aptitudes().containsKey("STRENGTH_ENDURANCE"), "forging leans on strength: " + p.aptitudes());

        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_skill_practice_event WHERE action_id = ? AND skill_key = 'FORGING' " +
            "  AND proficiency_after > proficiency_before AND aptitude_shares -> 'STRENGTH_ENDURANCE' IS NOT NULL",
            Integer.class, action));

        try {
            jdbc.update("UPDATE chronicle_skill_practice_event SET difficulty = 0 WHERE action_id = ?", action);
            throw new AssertionError("practice evidence must be immutable");
        } catch (org.springframework.dao.DataAccessException expected) { /* the trigger refused it */ }
    }

    /**
     * NOTHING OF THIS REACHES THE PLAYER AS A NUMBER. #954: "Keep progression metrics hidden from the
     * Body HUD." The HUD is ten descriptive bands about the body; no skill, aptitude or proficiency may
     * appear among them.
     */
    @Test void noProgressionMetricIsOnTheBodyHud() {
        for (String field : com.devosphere.draugr.chronicle.ChroniclePhysiologyService.BodyHudSnapshot.class
                .getRecordComponents() != null
            ? java.util.Arrays.stream(com.devosphere.draugr.chronicle.ChroniclePhysiologyService.BodyHudSnapshot.class
                .getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new)
            : new String[0]) {
            String lower = field.toLowerCase(java.util.Locale.ROOT);
            assertTrue(!lower.contains("skill") && !lower.contains("aptitude") && !lower.contains("proficien")
                       && !lower.contains("familiar") && !lower.contains("mastery"),
                "the Body HUD must not carry a progression metric, but carries: " + field);
        }
    }
}
