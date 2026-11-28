package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.simulation.SimulationTickService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #960 — STARVATION MENDS SLOWLY, AND IT IS WHAT KILLS.
 *
 * <p>The death rule was "721 hours since eating". So a Chronicle who had eaten well every other day for a
 * month died on the same schedule as one who had eaten nothing, because the counter was reset by a mouthful
 * and the threshold never asked what the mouthful had done. Death follows the damage now.
 *
 * <p>And the clock-based "Starving" condition is gone with it: a Chronicle three hundred hours past their
 * last meal with a full stomach and sound reserves used to read Underfed off the clock, which is the exact
 * confusion #959 took apart.
 *
 * <p>Skips without Docker. Every assertion here was also taken live against a booted stack.
 */
@SpringBootTest
class StarvationMendsSlowlyIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired SimulationTickService ticks;
    @Autowired JdbcTemplate jdbc;

    /**
     * THE ONLY LIVING CHRONICLE IN THE WORLD, which a test that TICKS has to be.
     *
     * <p>{@code advanceTo} takes the first living Chronicle and no others — {@code if (!rs.next()) return} —
     * so a body left alive by an earlier test is the one every tick in this class would advance, and the
     * body under test would never move at all. Measured in CI, where this class runs after some nine
     * hundred others: five of its seven tests failed, and the shapes were exactly what that implies —
     * energy still at the 100 the test had set, and a death by DEHYDRATION in a test about starvation,
     * because the body being ticked was a stranger's.
     *
     * <p>The suite shares one world, so a test that ticks must own the only body in it.
     */
    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        // AND A RETIRED BODY LETS GO OF ITS GEAR. The Auditor counts an equipment_attachment on a dead
        // Chronicle as a violation, and nearly every integration test in the suite asserts the Auditor is
        // consistent — so retiring equipped bodies without unequipping them turned one failure into some
        // three hundred. Measured: "452 equipment attachment(s) reference an inactive item or dead
        // Chronicle", from tests that have nothing to do with food.
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id IN " +
                    " (SELECT id FROM chronicle WHERE life_state='LIVING')");
        jdbc.update("UPDATE chronicle SET life_state='DEAD', died_at=now(), " +
                    " death_cause=COALESCE(death_cause,'retired by a test that needs the tick to itself') " +
                    " WHERE life_state='LIVING'");
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        return summary.id();
    }

    /** Set the body, and put the metabolic clock back by `hoursAgo` so the next tick spans that long. */
    private void body(UUID me, int fullness, int reserves, int nutrition, int starvation,
                      double hoursSinceFood, int hoursAgo) {
        // stomach_kcal at 1.58 kcal/ml is what the flat model valued this bulk at (#962), so what these
        // tests measure is unchanged by foods having been given their own worth.
        jdbc.update("UPDATE chronicle_physiology SET stomach_fullness_ml=?, stomach_kcal=ROUND(?*1.58), energy_reserves=?, nutrition=?, " +
                    " starvation_severity=?, hours_without_food=?, hours_without_water=0, " +
                    " core_temperature_c=37.0, illness_severity=0, injury_severity=0, blood_loss_ml=0, " +
                    " sleep_debt_hours=0, energy_level=70, " +
                    " last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1) - make_interval(hours => ?) WHERE chronicle_id=?",
            fullness, fullness, reserves, nutrition, starvation, hoursSinceFood, hoursAgo, me);
    }

    /**
     * Hand the body back. A test that ticks must leave no living stranger behind for the next one, and a
     * retired body has to let go of its gear or the Auditor counts the attachment as a violation — and
     * nearly every integration test in this suite asserts the Auditor is consistent.
     */
    private void retire(UUID me) {
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", me);
        jdbc.update("UPDATE chronicle SET life_state='DEAD', died_at=now(), " +
                    " death_cause=COALESCE(death_cause,'test') WHERE id=?", me);
    }

    private int guts(UUID me, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM chronicle_physiology WHERE chronicle_id=?",
            Integer.class, me);
    }

    /**
     * THE CLOCK NO LONGER KILLS. A Chronicle a thousand hours past their last meal, with food in them and a
     * sound store, is alive — and under the old rule they were dead at 721 whatever they had eaten.
     */
    @Test void theElapsedFoodClockNoLongerDecidesDeath() {
        UUID me = chronicle();
        body(me, 900, 80, 75, 0, 1000, 1);
        ticks.advanceBy(java.time.Duration.ofHours(1));

        assertEquals("LIVING", jdbc.queryForObject(
            "SELECT life_state FROM chronicle WHERE id=?", String.class, me),
            "a thousand hours since a meal is not a cause of death; starvation is");
        // And the label agrees: hours are history.
        assertEquals("Healthy", jdbc.queryForObject(
            "SELECT health FROM chronicle_body WHERE chronicle_id=?", String.class, me));
        retire(me);
    }

    /** And the damage does. Death is named for starvation because starvation is what it was. */
    @Test void starvationItselfIsWhatKills() {
        UUID me = chronicle();
        body(me, 0, 0, 10, 99, 0, 2);
        ticks.advanceBy(java.time.Duration.ofHours(2));

        assertEquals("DEAD", jdbc.queryForObject(
            "SELECT life_state FROM chronicle WHERE id=?", String.class, me));
        assertEquals("Critical Starvation", jdbc.queryForObject(
            "SELECT death_cause FROM chronicle WHERE id=?", String.class, me));
    }

    /**
     * #984 — A CHRONICLE IS REMEMBERED BY THE BODY THEY HAD.
     *
     * <p>The death snapshot wrote its Hunger from the CLOCK while the living HUD had read it from the stomach
     * since #959, and the two do not share a vocabulary: the clock says Satisfied / Hungry / Very Hungry /
     * Starving / Critical Starvation, the stomach says Full / Satisfied / Peckish / Hungry / Empty. So a
     * remembered body could never read "Full" — and since #962 made the clock history rather than a debt, one
     * bilberry resets it, so a Chronicle who nibbled a berry and was then killed was recorded "Satisfied"
     * with four millilitres in them.
     *
     * <p>The row is read back: {@code ChronicleService} serves it as the journey's {@code finalBody}.
     */
    @Test void theRememberedBodyReadsItsHungerFromTheStomach() {
        UUID me = chronicle();
        // A full belly, and a death that has nothing to do with food: thirst past DEHYDRATION_DEATH_HOURS.
        // One hour, because a stomach digests while the tick runs — four hours of it takes 1200 ml down to
        // 840, which is honestly "Satisfied" and would be testing the wrong thing.
        body(me, 1200, 60, 60, 0, 300, 0);
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=200 WHERE chronicle_id=?", me);
        ticks.advanceBy(java.time.Duration.ofHours(1));

        assertEquals("Critical Dehydration", jdbc.queryForObject(
            "SELECT death_cause FROM chronicle WHERE id=?", String.class, me));
        String remembered = jdbc.queryForObject(
            "SELECT body_snapshot->>'hunger' FROM chronicle_death_snapshot WHERE chronicle_id=?",
            String.class, me);
        assertEquals("Full", remembered,
            "a body that died with a full stomach must be remembered as full, not read off a clock that says "
                + "it had not eaten for three hundred hours");
    }

    /** And the other way about: a berry an hour ago is not a fed body, and must not be remembered as one. */
    @Test void aBerryAnHourAgoIsNotRememberedAsAMeal() {
        UUID me = chronicle();
        body(me, 4, 60, 60, 0, 0, 2);              // 4 ml in them, clock freshly reset by the berry
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=200 WHERE chronicle_id=?", me);
        ticks.advanceBy(java.time.Duration.ofHours(2));

        String remembered = jdbc.queryForObject(
            "SELECT body_snapshot->>'hunger' FROM chronicle_death_snapshot WHERE chronicle_id=?",
            String.class, me);
        assertEquals("Empty", remembered,
            "the clock said nought hours since eating and would have recorded Satisfied: " + remembered);
    }

    /**
     * SUSTAINED intake on FOOD THAT IS WORTH EATING, which is what the ticket means and what took three
     * measurements to get right.
     *
     * <p>One stomachful does not feed a body for forty hours: 1200 ml digests out in about thirteen, so a
     * single long tick made BOTH bodies worse and read as though mending did not work at all. And a
     * stomachful of ORDINARY food does not feed one for sixteen hours either, in the world this suite runs in:
     * the canonical world is cold, a chilled body spends up to 1.6 reserve points an hour keeping warm on
     * top of its basal 0.85, and a watery 1200 ml is worth about 17. Measured: shallow 12 -> 33.
     *
     * <p>So the body is fed on PEMMICAN, through the real {@link ChroniclePhysiologyService#eatOneOf} path
     * (#962) — a day of living in 380 ml, which is what the stuff is for. That is what "adequate sustained
     * intake" has to mean somewhere cold, and it is the only shape in which the comparison below is a
     * statement about the RATE of mending rather than about the weather.
     */
    private int mendedOverFourFedStretches(int startingStarvation) {
        UUID me = chronicle();
        jdbc.update("UPDATE chronicle_physiology SET starvation_severity=?, energy_reserves=60, nutrition=60 " +
                    " WHERE chronicle_id=?", startingStarvation, me);
        for (int stretch = 0; stretch < 4; stretch++) {
            jdbc.update("UPDATE chronicle_physiology SET stomach_fullness_ml=0, stomach_kcal=0, " +
                        " hours_without_water=0, sleep_debt_hours=0, " +
                        " illness_severity=0, injury_severity=0, blood_loss_ml=0 WHERE chronicle_id=?", me);
            physiology.eatOneOf(me, "pemmican", null);
            physiology.eatOneOf(me, "pemmican", null);
            physiology.eatOneOf(me, "pemmican", null);
            jdbc.update("UPDATE chronicle_physiology SET hours_without_water=0, sleep_debt_hours=0, " +
                        " last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1)" +
                        " WHERE chronicle_id=?", me);
            ticks.advanceBy(java.time.Duration.ofHours(FED_STRETCH_HOURS));
        }
        int mended = startingStarvation - guts(me, "starvation_severity");
        retire(me);
        return mended;
    }

    @Test void theDeeperTheDamageTheSlowerItMends() {
        int shallowMended = mendedOverFourFedStretches(12);
        int deepMended = mendedOverFourFedStretches(90);

        assertTrue(shallowMended > 0, "a shallow deficit should mend when genuinely fed: " + shallowMended);
        assertTrue(deepMended > 0, "and so should a deep one, slowly: " + deepMended);
        assertTrue(shallowMended > deepMended,
            "the same fed hours should mend less of a deep deficit: shallow=" + shallowMended + " deep=" + deepMended);
    }

    /**
     * #960: mending wants water and rest too, not only food. Fed identically on pemmican, so the only
     * difference between the two bodies is the thirst and the sleep they were kept from.
     */
    @Test void aBodyMendsOnFoodAndWaterAndRestTogether() {
        int keptMended = mendedOverTenFedHours(40, false);
        int parchedMended = mendedOverTenFedHours(40, true);

        assertTrue(keptMended > 0, "a fed, watered, rested body should mend: " + keptMended);
        assertTrue(keptMended > parchedMended,
            "and faster than one kept from water and sleep: kept=" + keptMended
                + " parched and sleepless=" + parchedMended);
    }

    /**
     * HOW LONG A FED STRETCH CAN BE, and it is not a free choice.
     *
     * <p>Mending wants food AND water AND rest (#960), and the tick reads thirst and sleep as they stand at
     * the END of the span: {@code water < 18} and {@code sleepDebt < 20} are the thresholds. So a stretch of
     * twenty hours starts a rested, watered body and finishes a parched, sleepless one, and the mending it
     * was measuring is cancelled by the span measuring it. Measured: a 20-hour stretch mends 0 where a
     * 16-hour stretch mends 1, with everything else identical.
     *
     * <p>Sixteen is the longest stretch that stays inside both thresholds, and three blocks of pemmican
     * (1140 ml at 65 ml/h) is just over sixteen hours of food — so the body is fed for the whole of it.
     */
    private static final int FED_STRETCH_HOURS = 16;

    /** Sixteen genuinely fed hours, with or without water and rest. */
    private int mendedOverTenFedHours(int startingStarvation, boolean parchedAndSleepless) {
        UUID me = chronicle();
        jdbc.update("UPDATE chronicle_physiology SET starvation_severity=?, energy_reserves=60, nutrition=60, " +
                    " stomach_fullness_ml=0, stomach_kcal=0, illness_severity=0, injury_severity=0, " +
                    " blood_loss_ml=0 WHERE chronicle_id=?", startingStarvation, me);
        physiology.eatOneOf(me, "pemmican", null);
        physiology.eatOneOf(me, "pemmican", null);
        physiology.eatOneOf(me, "pemmican", null);
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=?, sleep_debt_hours=?, " +
                    " last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1)" +
                    " WHERE chronicle_id=?",
            parchedAndSleepless ? 40 : 0, parchedAndSleepless ? 40 : 0, me);
        ticks.advanceBy(java.time.Duration.ofHours(FED_STRETCH_HOURS));
        int mended = startingStarvation - guts(me, "starvation_severity");
        retire(me);
        return mended;
    }

    /**
     * ENERGY ANSWERS TO THE STORE (#960: starvation "must influence Health, Energy, Condition..."). A body
     * with nothing in reserve cannot be Rested however long it lies down.
     */
    @Test void energyCannotExceedWhatThereIsToSpend() {
        UUID me = chronicle();
        body(me, 0, 0, 40, 30, 0, 1);
        jdbc.update("UPDATE chronicle_physiology SET energy_level=100 WHERE chronicle_id=?", me);
        ticks.advanceBy(java.time.Duration.ofHours(1));

        int energy = guts(me, "energy_level");
        retire(me);
        assertTrue(energy <= 30, "an empty store cannot support a full tank: " + energy);
        assertTrue(energy > 0, "but a starving body can still stand up: " + energy);
    }

    /** #960: "Record why starvation worsens or improves." The ledger says which of them it was. */
    @Test void theLedgerSaysWhyTheStarvationMoved() {
        UUID me = chronicle();
        body(me, 0, 0, 30, 30, 0, 12);
        ticks.advanceBy(java.time.Duration.ofHours(12));

        List<String> causes = jdbc.queryForList(
            "SELECT DISTINCT cause FROM chronicle_metric_change " +
            " WHERE chronicle_id=? AND metric_key='STARVATION_SEVERITY' ORDER BY 1", String.class, me);
        assertTrue(!causes.isEmpty(), "a change in starvation should be accounted for");
        for (String cause : causes)
            assertTrue(List.of("NOURISHMENT", "EXPOSURE", "ILLNESS").contains(cause),
                "the cause should be one the model actually knows: " + cause);

        String why = jdbc.queryForObject(
            "SELECT source_event FROM chronicle_metric_change WHERE chronicle_id=? " +
            " AND metric_key='STARVATION_SEVERITY' ORDER BY occurred_at DESC LIMIT 1", String.class, me);
        retire(me);
        assertNotNull(why, "and it should say so in words");
        assertTrue(why.length() > 8, "words, not a code: " + why);
    }

    /** Every felt sentence for the nutrition metrics is written, so none falls back to the generic one. */
    @Test void theNutritionMetricsHaveTheirOwnWords() {
        for (String metric : List.of("ENERGY_RESERVES", "NUTRITION", "STARVATION_SEVERITY"))
            for (boolean rose : new boolean[]{true, false}) {
                String said = com.devosphere.draugr.capability.CausalMetricService.sentenceFor(metric, rose);
                assertNotNull(said, metric);
                assertTrue(!said.contains("Something in you has"),
                    metric + " should have its own words, not the fallback: " + said);
                assertTrue(!said.matches(".*\\d.*"), "and no figures: " + said);
            }
    }
}
