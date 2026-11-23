package com.devosphere.draugr.persistence;

import com.devosphere.draugr.capability.CapabilityAdaptationService;
import com.devosphere.draugr.capability.CausalMetricService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #953 — WHAT CHANGED, AND WHY.
 *
 * <p>Every write to the eight hidden numbers had been blind — {@code SET col = LEAST(1, col + ?)} — so the
 * value before the change was never read, never recorded, and nothing could say what a Chronicle's
 * conditioning had been or what moved it. And seven of the eight could only ever RISE, so a Chronicle who
 * hauled timber for a week and then sat by the fire for a month was exactly as conditioned as the day they
 * stopped. #953 asks for both halves; these hold them.
 *
 * <p>Skips without Docker. Every assertion here was also run against a booted stack on a throwaway
 * Postgres, because a suite that skips is not evidence.
 */
@SpringBootTest
class WhatChangedAndWhyIntegrationTest {

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
    @Autowired CausalMetricService metrics;
    @Autowired CapabilityAdaptationService capability;
    @Autowired JdbcTemplate jdbc;

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID id = summary.id();
        jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?) ON CONFLICT DO NOTHING", id);
        return id;
    }

    /**
     * THE SUITE SHARES ONE LIVING CHRONICLE, so other tests (this class's own among them) leave changes in its
     * ledger that were never told. A test that counts what is felt must start from a ledger with nothing
     * waiting, or it measures its neighbours' work rather than its own.
     */
    private void nothingWaitingToBeTold(UUID me) {
        jdbc.update("UPDATE chronicle_metric_change SET told = TRUE WHERE chronicle_id = ? AND NOT told", me);
    }

    /** Every metric must point at a column that exists, or a recorded change writes nowhere. */
    @Test void everyTrackedMetricNamesARealColumn() {
        List<String> dangling = jdbc.queryForList(
            "SELECT m.metric_key || ' -> ' || m.source_table || '.' || m.source_column FROM metric_subject m " +
            " WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns " +
            "                    WHERE table_name = m.source_table AND column_name = m.source_column) ORDER BY 1",
            String.class);
        assertTrue(dangling.isEmpty(), "metrics pointing at columns that do not exist: " + dangling);
        // MY OWN ASSERTION WAS THE BRITTLE PART. I wrote assertEquals(8, ...) in #953, which forbids ever
        // tracking another metric -- and #959 immediately added three (ENERGY_RESERVES, NUTRITION,
        // STARVATION_SEVERITY), so it broke on the very next slice. A catalogue that is expected to GROW is
        // asserted with >=, which is what the registry tests in this project already do.
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM metric_subject", Integer.class) >= 11,
            "the catalogue should not have shrunk");
    }

    /** A change carries its whole account: before, after, delta, cause and what caused it. */
    @Test void aChangeRecordsTheValueBeforeItAndTheReasonForIt() {
        UUID me = chronicle();
        UUID action = UUID.randomUUID();
        Instant at = Instant.now();

        CausalMetricService.Change change =
            metrics.move(me, "LOAD_CONDITIONING", 0.04, "PRACTICE", action, null, 30, 0.5, at);
        assertNotNull(change);
        assertEquals(0.04, change.delta(), 1e-6);
        assertTrue(change.rose());

        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_metric_change WHERE chronicle_id = ? AND metric_key = 'LOAD_CONDITIONING' " +
            "  AND source_action_id = ? AND cause = 'PRACTICE' AND exposure_minutes = 30 " +
            "  AND abs(delta - (after_value - before_value)) < 0.000001", Integer.class, me, action));
        // And the column it describes actually moved to the value the row claims.
        assertEquals(change.after(), jdbc.queryForObject(
            "SELECT load_conditioning FROM chronicle_capability_adaptation WHERE chronicle_id = ?", Double.class, me), 1e-6);
    }

    /**
     * A NUMBER AT ITS CEILING HAS NOT STRENGTHENED. #953: "avoid claims of adaptation without a recorded
     * mechanical effect." If the clamp eats the delta, nothing happened and nothing is written.
     */
    @Test void aCappedMetricRecordsNoChangeAndMakesNoClaim() {
        UUID me = chronicle();
        jdbc.update("UPDATE chronicle_capability_adaptation SET attention_resilience = 1.0 WHERE chronicle_id = ?", me);
        int before = jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_metric_change WHERE chronicle_id = ?", Integer.class, me);

        assertNull(metrics.move(me, "ATTENTION_RESILIENCE", 0.05, "PRACTICE", UUID.randomUUID(), null, 30, 0.5, Instant.now()),
            "a metric already at its ceiling did not move");
        assertEquals(before, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_metric_change WHERE chronicle_id = ?", Integer.class, me));
        // Nor at the floor.
        jdbc.update("UPDATE chronicle_capability_adaptation SET visual_aim_familiarity = 0 WHERE chronicle_id = ?", me);
        assertNull(metrics.move(me, "VISUAL_AIM_FAMILIARITY", -0.05, "NEGLECT", null, "test", null, null, Instant.now()));
    }

    /**
     * WHAT IS NOT USED FADES — the half that did not exist. Seven of the eight columns were written only as
     * {@code col + delta}, so nothing a Chronicle had ever built could be lost by neglecting it.
     */
    @Test void whatHasNotBeenPractisedInWeeksSlipsAway() {
        UUID me = chronicle();
        Instant now = Instant.now();
        jdbc.update("UPDATE chronicle_capability_adaptation SET visual_aim_familiarity = 0.40 WHERE chronicle_id = ?", me);
        // Last genuine practice, six weeks back.
        jdbc.update("INSERT INTO chronicle_metric_change (chronicle_id, metric_key, occurred_at, before_value, " +
                    " after_value, delta, cause, source_event) VALUES (?,?,?,?,?,?,'PRACTICE','set up by a test')",
            me, "VISUAL_AIM_FAMILIARITY", java.sql.Timestamp.from(now.minus(42, ChronoUnit.DAYS)), 0.39, 0.40, 0.01);

        List<CausalMetricService.Change> slipped = metrics.letTheUnpractisedSlip(me, now);
        assertTrue(slipped.stream().anyMatch(c -> c.metricKey().equals("VISUAL_AIM_FAMILIARITY")),
            "six weeks without drawing a bow should tell: " + slipped);

        double aim = jdbc.queryForObject(
            "SELECT visual_aim_familiarity FROM chronicle_capability_adaptation WHERE chronicle_id = ?", Double.class, me);
        assertTrue(aim < 0.40, "the aim should have slipped: " + aim);
        assertTrue(aim > 0.20, "but gently, not to nothing: " + aim);
        // And the decline is as reconstructable as a gain.
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_metric_change WHERE chronicle_id = ? AND cause = 'NEGLECT' " +
            "  AND metric_key = 'VISUAL_AIM_FAMILIARITY' AND delta < 0", Integer.class, me));
    }

    /** Practised recently, nothing slips. The grace is the point: a week off is not neglect. */
    @Test void workDoneThisWeekDoesNotSlip() {
        UUID me = chronicle();
        Instant now = Instant.now();
        jdbc.update("UPDATE chronicle_capability_adaptation SET load_conditioning = 0.30 WHERE chronicle_id = ?", me);
        jdbc.update("INSERT INTO chronicle_metric_change (chronicle_id, metric_key, occurred_at, before_value, " +
                    " after_value, delta, cause, source_event) VALUES (?,?,?,?,?,?,'PRACTICE','set up by a test')",
            me, "LOAD_CONDITIONING", java.sql.Timestamp.from(now.minus(2, ChronoUnit.DAYS)), 0.29, 0.30, 0.01);

        assertTrue(metrics.letTheUnpractisedSlip(me, now).stream()
                .noneMatch(c -> c.metricKey().equals("LOAD_CONDITIONING")),
            "two days is not neglect");
        assertEquals(0.30, jdbc.queryForObject(
            "SELECT load_conditioning FROM chronicle_capability_adaptation WHERE chronicle_id = ?", Double.class, me), 1e-6);
    }

    /**
     * THE NOTICE WAITS UNTIL THERE IS SOMETHING TO NOTICE. #953: "Avoid repetitive notices on every tiny
     * change." One practice event moves a familiarity by about 0.002 and a felt step is 0.05.
     */
    @Test void nothingIsSaidUntilTheChangeWouldActuallyBeFelt() {
        UUID me = chronicle();
        nothingWaitingToBeTold(me);
        // Pinned mid-range: a shared chronicle may already have hands near 1.0, where the clamp would swallow
        // the step this test needs to be felt.
        jdbc.update("UPDATE chronicle_capability_adaptation SET fine_motor_familiarity = 0.30 WHERE chronicle_id = ?", me);
        metrics.move(me, "FINE_MOTOR_FAMILIARITY", 0.002, "PRACTICE", UUID.randomUUID(), null, 30, 0.5, Instant.now());
        assertTrue(metrics.whatIsFelt(me).isEmpty(), "one session of work is not a felt change");

        metrics.move(me, "FINE_MOTOR_FAMILIARITY", 0.06, "PRACTICE", UUID.randomUUID(), null, 30, 0.5, Instant.now());
        List<String> felt = metrics.whatIsFelt(me);
        assertEquals(1, felt.size(), "a step's worth of change should be worth one sentence: " + felt);
        assertTrue(felt.get(0).toLowerCase(java.util.Locale.ROOT).contains("hands"),
            "and it should be about the hands, not about a number: " + felt.get(0));
        // No number, ever.
        for (String sentence : felt)
            assertFalse(sentence.matches(".*\\d.*"), "a notice must carry no figures: " + sentence);

        // And it is said once.
        assertTrue(metrics.whatIsFelt(me).isEmpty(), "the same change must not be announced twice");
    }

    /** A decline is narrated too, and differently. Strengthening and slackening are not one sentence. */
    @Test void aDeclineIsFeltAsADeclineAndNotAsAGain() {
        UUID me = chronicle();
        nothingWaitingToBeTold(me);
        jdbc.update("UPDATE chronicle_capability_adaptation SET load_conditioning = 0.50 WHERE chronicle_id = ?", me);
        metrics.move(me, "LOAD_CONDITIONING", -0.08, "NEGLECT", null, "a test", null, null, Instant.now());
        List<String> felt = metrics.whatIsFelt(me);
        assertEquals(1, felt.size(), felt.toString());
        assertTrue(felt.get(0).contains("weight") || felt.get(0).contains("back"),
            "a lost conditioning should be felt as weight: " + felt.get(0));
        assertFalse(felt.get(0).contains("easier"), "and must not read as an improvement: " + felt.get(0));

        assertNotEquals(CausalMetricService.sentenceFor("LOAD_CONDITIONING", true),
                        CausalMetricService.sentenceFor("LOAD_CONDITIONING", false));
    }

    /**
     * A SPAN OF NEGLECT IS PAID FOR ONCE. The slip is asked on every resolved action, and it measured the idle
     * span from the last practice — the same span each time — so ten acts in one afternoon took ten times the
     * decline. Run far in the future, on a metric no other test here slips, so neither this test's rows nor
     * another's can reach the other's window.
     */
    @Test void aSpanOfNeglectIsChargedOnceHoweverOftenTheWorldLooks() {
        UUID me = chronicle();
        Instant base = Instant.now().plus(400, ChronoUnit.DAYS);
        jdbc.update("UPDATE chronicle_capability_adaptation SET insight_familiarity = 0.40 WHERE chronicle_id = ?", me);
        jdbc.update("INSERT INTO chronicle_metric_change (chronicle_id, metric_key, occurred_at, before_value, " +
                    " after_value, delta, cause, source_event) VALUES (?,?,?,?,?,?,'PRACTICE','set up by a test')",
            me, "INSIGHT_FAMILIARITY", java.sql.Timestamp.from(base.minus(42, ChronoUnit.DAYS)), 0.39, 0.40, 0.01);

        metrics.letTheUnpractisedSlip(me, base);
        double afterFirst = insight(me);
        assertTrue(afterFirst < 0.40, "five weeks past the grace should cost something: " + afterFirst);

        // The same afternoon, looked at twice more: nothing more is owed.
        metrics.letTheUnpractisedSlip(me, base.plus(1, ChronoUnit.HOURS));
        metrics.letTheUnpractisedSlip(me, base.plus(2, ChronoUnit.HOURS));
        assertEquals(afterFirst, insight(me), 1e-9, "the same idle span must not be charged again");

        // Three more days of idleness are owed three days of slip — a fraction of the first charge.
        metrics.letTheUnpractisedSlip(me, base.plus(3, ChronoUnit.DAYS));
        double afterThreeDays = insight(me);
        double firstCharge = 0.40 - afterFirst, laterCharge = afterFirst - afterThreeDays;
        assertTrue(laterCharge > 0, "three more idle days should cost something");
        assertTrue(laterCharge < firstCharge, "but only three days' worth: first " + firstCharge + ", later " + laterCharge);
        assertEquals(2, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_metric_change WHERE chronicle_id = ? AND cause = 'NEGLECT' " +
            "  AND metric_key = 'INSIGHT_FAMILIARITY' AND occurred_at >= ?", Integer.class,
            me, java.sql.Timestamp.from(base)));
    }

    private double insight(UUID me) {
        return jdbc.queryForObject(
            "SELECT insight_familiarity FROM chronicle_capability_adaptation WHERE chronicle_id = ?", Double.class, me);
    }

    /** What the action narration asks for: one run of prose when a change is ripe, then nothing for it again. */
    @Test void theFeltChangeIsToldOnceToTheNarration() {
        UUID me = chronicle();
        metrics.whatIsFelt(me); // whatever another test here left ripe is not this test's to hear
        jdbc.update("UPDATE chronicle_capability_adaptation SET locomotion_familiarity = 0.20 WHERE chronicle_id = ?", me);
        metrics.move(me, "LOCOMOTION_FAMILIARITY", 0.06, "PRACTICE", UUID.randomUUID(), null, 60, 0.5, Instant.now());

        String felt = capability.whatIsFelt(me);
        assertNotNull(felt, "a felt step of practice over bad ground should be told");
        assertTrue(felt.contains("feet"), "and told about the feet: " + felt);
        assertFalse(felt.matches(".*\\d.*"), "with no figures in it: " + felt);
        assertNull(capability.whatIsFelt(me), "and never told again");
    }

    /** Recorded evidence cannot be rewritten; only the fact that a notice was given may be set, once. */
    @Test void theLedgerCannotBeRewrittenAfterTheFact() {
        UUID me = chronicle();
        metrics.move(me, "KNOWLEDGE_FAMILIARITY", 0.03, "PRACTICE", UUID.randomUUID(), null, 20, 0.5, Instant.now());
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM chronicle_metric_change WHERE chronicle_id = ?",
            Integer.class, me) > 0, "there must be a row, or the refusals below prove nothing");

        jdbc.update("UPDATE chronicle_metric_change SET told = TRUE WHERE chronicle_id = ?", me);   // allowed, once
        for (String rewrite : List.of(
                "UPDATE chronicle_metric_change SET delta = 0.5 WHERE chronicle_id = ?",
                "UPDATE chronicle_metric_change SET told = FALSE WHERE chronicle_id = ?",
                "UPDATE chronicle_metric_change SET cause = 'CORRECTION' WHERE chronicle_id = ?",
                "DELETE FROM chronicle_metric_change WHERE chronicle_id = ?")) {
            try {
                jdbc.update(rewrite, me);
                throw new AssertionError("the ledger must have refused: " + rewrite);
            } catch (org.springframework.dao.DataAccessException expected) { /* the trigger refused it */ }
        }
    }

    /**
     * AND AN ORDINARY ACT GOES THROUGH IT. The capability service's blind UPDATE is gone, so a resolved
     * action's effect on the body is on record with its cause rather than only its exposure.
     */
    @Test void anOrdinaryActionsEffectIsOnRecordWithItsCause() {
        UUID me = chronicle();
        UUID action = UUID.randomUUID();
        capability.record(me, action, "LOAD", 45, 0.2, 0.5, Instant.now());

        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_metric_change WHERE chronicle_id = ? AND source_action_id = ? " +
            "  AND metric_key = 'LOAD_CONDITIONING' AND cause = 'PRACTICE' AND delta > 0", Integer.class, me, action));
        // The old evidence row is still written: #953 asks to extend the system, not replace it.
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM chronicle_capability_evidence WHERE chronicle_id = ? AND action_id = ?",
            Integer.class, me, action));
        // And familiarity() still answers, reading the column metric_subject names.
        assertTrue(capability.familiarity(me, "LOAD") > 0);
    }

    private static void assertNotEquals(String a, String b) {
        org.junit.jupiter.api.Assertions.assertNotEquals(a, b);
    }
}
