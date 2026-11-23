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
 * #962 — WHAT A FOOD IS WORTH.
 *
 * <p>#959 gave the body a stomach. Every food still went into it as the same thing: a flat 320 ml of
 * nothing-in-particular. So, measured against the catalogue as it stood, ONE BILBERRY (3 g, 4 ml) filled
 * a quarter of a stomach and fed a body as well as a 540 g ROAST HAUNCH; a skin of BOILED WATER — which
 * is category FOOD, and which a bare {@code eat} would take ahead of the haunch because it sorts earlier
 * by key — nourished exactly as much as the haunch; and a handful of WATERCRESS (11 kcal/100 g) was worth
 * a handful of PEMMICAN (580).
 *
 * <p>V482 says what each of the 118 foods is made of, in kilocalories by mass, so the data can be checked
 * against the world and argued with. Bulk is not declared twice: it is the unit's own volume, which the
 * carry weight has always read.
 *
 * <p>Skips without Docker. Every assertion here was also taken live against a booted stack on a throwaway
 * Postgres, because a suite that skips is not evidence.
 */
@SpringBootTest
class WhatAFoodIsWorthIntegrationTest {

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

    /**
     * An empty, sound body with its metabolic clock set to the simulated NOW, so the next
     * {@code advanceBy(n)} spans exactly n hours.
     *
     * <p>Putting the clock BACK as well double-counts: {@code advanceBy} moves the simulated clock on from
     * where it stands, and physiology measures from {@code last_metabolic_update} to that new now — so
     * "back ten and advance ten" is a twenty-hour tick. Measured, and it is why two of these tests first
     * read as though the model did not work: a twenty-hour span starts a watered, rested body and ends a
     * parched, sleepless one, and over twenty-four cold hours every body's store reaches nought, which
     * leaves nothing for a comparison to be about.
     */
    private void emptyAndSound(UUID me) {
        jdbc.update("UPDATE chronicle_physiology SET stomach_fullness_ml=0, stomach_kcal=0, " +
                    " stomach_digest_ml_per_hour=90, energy_reserves=40, nutrition=60, starvation_severity=0, " +
                    " hours_without_food=0, hours_without_water=20, energy_level=60, stress_level=40, " +
                    " core_temperature_c=37.0, illness_severity=0, injury_severity=0, blood_loss_ml=0, " +
                    " sleep_debt_hours=0, " +
                    " last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1) " +
                    " WHERE chronicle_id=?", me);
    }

    private int guts(UUID me, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM chronicle_physiology WHERE chronicle_id=?",
            Integer.class, me);
    }

    private void retire(UUID me) {
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", me);
        jdbc.update("UPDATE chronicle SET life_state='DEAD', died_at=now(), death_cause='test' WHERE id=?", me);
    }

    // ---------------------------------------------------------------------------------------------
    // THE DATA
    // ---------------------------------------------------------------------------------------------

    /**
     * EVERY FOOD HAS A DECLARED WORTH. The migration refuses to apply without this; the suite holds the
     * same line, so a future migration that adds a food and forgets its nourishment is caught here even
     * on a database that was hand-patched rather than built from nothing.
     */
    @Test void everyFoodInTheCatalogueSaysWhatItIsWorth() {
        List<String> silent = jdbc.queryForList(
            "SELECT d.item_key FROM item_definition d LEFT JOIN food_nourishment f ON f.item_key = d.item_key " +
            " WHERE d.category='FOOD' AND f.item_key IS NULL ORDER BY 1", String.class);
        assertTrue(silent.isEmpty(), "a food with no declared nourishment feeds the player a guess: " + silent);

        // And bulk has to come from somewhere, since it is not declared in food_nourishment.
        List<String> bulkless = jdbc.queryForList(
            "SELECT d.item_key FROM item_definition d JOIN food_nourishment f USING (item_key) " +
            " WHERE d.unit_volume_ml <= 0 ORDER BY 1", String.class);
        assertTrue(bulkless.isEmpty(), "a food with no volume goes into the stomach as nothing: " + bulkless);
    }

    /**
     * NO RECIPE MAKES NOURISHMENT OUT OF NOTHING (#962: "processing or splitting food does not create
     * unlimited nourishment").
     *
     * <p>This is the gate that found six of them. Mass balanced in every case — the Auditor was satisfied
     * throughout — because the missing mass came in as water, and water carries no nourishment. A third
     * more usable energy than went in is allowed, because cooking and drying make food more digestible;
     * beyond that it is creation.
     */
    @Test void noRecipeTurnsFoodIntoMoreNourishmentThanWentIntoIt() {
        List<String> creating = jdbc.queryForList(
            "WITH kcal AS (" +
            "    SELECT d.item_key, d.unit_mass_grams * f.kcal_per_100g / 100.0 AS k" +
            "      FROM item_definition d JOIN food_nourishment f USING (item_key))," +
            " plain AS (" +
            "    SELECT i.process_key, SUM(COALESCE(i.quantity,1) * k) AS kcal" +
            "      FROM material_process_input i JOIN kcal USING (item_key) GROUP BY i.process_key)," +
            // A GROUP IS A CHOICE, NOT A SUM: the cheapest way to make the thing is the one a player
            // will find, so it is the one that has to add up. Summing the options hid two of the worst.
            " cheapest AS (" +
            "    SELECT g.process_key, g.group_name, MIN(COALESCE(g.quantity,1) * k) AS kcal" +
            "      FROM material_process_input_group g JOIN kcal USING (item_key)" +
            "     GROUP BY g.process_key, g.group_name)," +
            " grouped AS (SELECT process_key, SUM(kcal) AS kcal FROM cheapest GROUP BY process_key)," +
            " ins AS (" +
            "    SELECT process_key, SUM(kcal) AS kcal FROM (" +
            "        SELECT process_key, kcal FROM plain" +
            "        UNION ALL" +
            "        SELECT process_key, kcal FROM grouped) y GROUP BY process_key)" +
            " SELECT p.process_key || ': ' || ROUND(i.kcal) || ' kcal in, '" +
            "        || ROUND(COALESCE(p.output_max, p.output_min, 1) * k.k) || ' out'" +
            "   FROM material_process p" +
            "   JOIN kcal k ON k.item_key = p.output_item_key" +
            "   JOIN ins  i ON i.process_key = p.process_key" +
            "  WHERE i.kcal > 0" +
            "    AND COALESCE(p.output_max, p.output_min, 1) * k.k > i.kcal * 1.35" +
            "  ORDER BY 1", String.class);
        assertTrue(creating.isEmpty(), "these recipes make nourishment out of nothing: " + creating);
    }

    /** A drink is declared as a drink: water carries no nourishment at all, and says so. */
    @Test void waterNourishesNothing() {
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT max(kcal_per_100g) FROM food_nourishment " +
            " WHERE item_key IN ('clean_water','filtered_water','raw_water')", Integer.class),
            "water is not food, and the catalogue has to be the thing that says so");
    }

    // ---------------------------------------------------------------------------------------------
    // EATING
    // ---------------------------------------------------------------------------------------------

    /** THE HEADLINE. A berry is not a meal, and nothing before this could tell the difference. */
    @Test void aBerryAndARoastHaunchAreNotTheSameMeal() {
        UUID me = chronicle();
        emptyAndSound(me);
        physiology.eatOneOf(me, "bilberry", null);
        int berryBulk = guts(me, "stomach_fullness_ml"), berryKcal = guts(me, "stomach_kcal");
        retire(me);

        UUID them = chronicle();
        emptyAndSound(them);
        physiology.eatOneOf(them, "cooked_game_meat", null);
        int haunchBulk = guts(them, "stomach_fullness_ml"), haunchKcal = guts(them, "stomach_kcal");
        retire(them);

        assertTrue(berryBulk < 20, "one bilberry is 4 ml of a 1200 ml stomach: " + berryBulk);
        assertTrue(berryKcal < 10, "and almost nothing to live on: " + berryKcal);
        assertTrue(haunchBulk > 500, "a roast haunch is a meal: " + haunchBulk);
        assertTrue(haunchKcal > 100 * berryKcal,
            "the haunch should be worth hundreds of berries, not the same as one: berry=" + berryKcal
                + " haunch=" + haunchKcal);
    }

    /**
     * HALF A MEAL IS HALF THE NOURISHMENT (#962: "no nourishment duplication"). A body that forces a
     * second helping down a full stomach gains only what fitted — and a third gains nothing at all,
     * which is #960's guard against instant recovery by repeated eating, enforced by the stomach.
     */
    @Test void onlyWhatFitsIsWorthAnything() {
        UUID me = chronicle();
        emptyAndSound(me);
        physiology.eatOneOf(me, "cooked_game_meat", null);
        int oneBulk = guts(me, "stomach_fullness_ml"), oneKcal = guts(me, "stomach_kcal");

        physiology.eatOneOf(me, "cooked_game_meat", null);
        int twoBulk = guts(me, "stomach_fullness_ml"), twoKcal = guts(me, "stomach_kcal");

        physiology.eatOneOf(me, "cooked_game_meat", null);
        int threeBulk = guts(me, "stomach_fullness_ml"), threeKcal = guts(me, "stomach_kcal");
        retire(me);

        assertEquals(ChroniclePhysiologyService.STOMACH_CAPACITY_ML, twoBulk, "a stomach has a size");
        // The second helping's nourishment must be in the same proportion as its bulk: no more, no less.
        double fittedShare = (twoBulk - oneBulk) / (double) oneBulk;
        double gainedShare = (twoKcal - oneKcal) / (double) oneKcal;
        assertTrue(Math.abs(fittedShare - gainedShare) < 0.03,
            "nourishment must go in with the bulk, in step: bulk share=" + fittedShare
                + " kcal share=" + gainedShare);
        assertEquals(twoBulk, threeBulk, "a third helping does not fit");
        assertEquals(twoKcal, threeKcal, "and so is worth nothing — eating again cannot be a recovery");
    }

    /**
     * A FOOD'S WATER SLAKES THIRST (#962: "Preserve relevant hydration ... effects"). At 50 ml to the
     * hour, which is the rate {@code drink()}'s long-standing ten hours for a 500 ml skin already implied.
     */
    @Test void theWaterInAFoodIsWorthSomethingToAThirstyBody() {
        UUID me = chronicle();
        emptyAndSound(me);
        double before = jdbc.queryForObject(
            "SELECT hours_without_water FROM chronicle_physiology WHERE chronicle_id=?", Double.class, me);
        physiology.eatOneOf(me, "cooked_game_meat", null);   // 540 g at 60% water
        double afterMeat = jdbc.queryForObject(
            "SELECT hours_without_water FROM chronicle_physiology WHERE chronicle_id=?", Double.class, me);
        retire(me);

        UUID them = chronicle();
        emptyAndSound(them);
        physiology.eatOneOf(them, "dried_meat", null);       // 260 g at 12% water
        double afterDried = jdbc.queryForObject(
            "SELECT hours_without_water FROM chronicle_physiology WHERE chronicle_id=?", Double.class, them);
        retire(them);

        assertTrue(before - afterMeat > 3, "a wet roast should ease thirst: " + (before - afterMeat) + "h");
        assertTrue(before - afterDried < 1.5, "dried meat should not: " + (before - afterDried) + "h");
    }

    /** A hot infusion is a comfort, and the comfort is a property of the drink rather than of a branch. */
    @Test void aPreparedDishSettlesTheNervesAndRawScrapsDoNot() {
        UUID me = chronicle();
        emptyAndSound(me);
        physiology.eatOneOf(me, "herbal_infusion", null);
        int afterInfusion = guts(me, "stress_level");
        retire(me);

        UUID them = chronicle();
        emptyAndSound(them);
        physiology.eatOneOf(them, "raw_game_meat", null);
        int afterRaw = guts(them, "stress_level");
        retire(them);

        assertTrue(afterInfusion < 40, "a hot infusion should settle the mind: " + afterInfusion);
        assertEquals(40, afterRaw, "raw meat is not a comfort: " + afterRaw);
    }

    // ---------------------------------------------------------------------------------------------
    // DIGESTION
    // ---------------------------------------------------------------------------------------------

    /**
     * A STOMACHFUL OF LEAVES IS NOT A MEAL. The body eats until it can eat no more and still loses
     * ground, which is #956's own point and the thing the flat model could not express.
     */
    @Test void aBodyCanFillItselfWithGreensAndStarveAnyway() {
        UUID cress = chronicle();
        emptyAndSound(cress);
        for (int i = 0; i < 40; i++) physiology.eatOneOf(cress, "watercress_bundle", null);
        int cressBulk = guts(cress, "stomach_fullness_ml");
        readyToTick(cress);
        ticks.advanceBy(java.time.Duration.ofHours(12));
        int cressLeft = guts(cress, "energy_reserves");
        retire(cress);

        UUID fed = chronicle();
        emptyAndSound(fed);
        physiology.eatOneOf(fed, "cooked_game_meat", null);
        physiology.eatOneOf(fed, "cooked_game_meat", null);
        int fedBulk = guts(fed, "stomach_fullness_ml");
        readyToTick(fed);
        ticks.advanceBy(java.time.Duration.ofHours(12));
        int fedLeft = guts(fed, "energy_reserves");
        retire(fed);

        assertEquals(cressBulk, fedBulk, "both bodies filled the same stomach to the same place");
        assertTrue(fedLeft > cressLeft + 5,
            "and the same bulk of leaves should leave a body far worse off than the same bulk of meat: "
                + "greens=" + cressLeft + " meat=" + fedLeft);
    }

    /** Anchor the metabolic clock at the simulated now without disturbing the stomach just filled. */
    private void readyToTick(UUID me) {
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = " +
                    " (SELECT simulated_at FROM simulation_clock WHERE id=1) WHERE chronicle_id=?", me);
    }

    /**
     * A FOOD LEAVES THE STOMACH AT ITS OWN RATE (#962: "digestion behavior for each supported food").
     * Water is gone in minutes and fat sits for half a day, and a mixture digests at the bulk-weighted
     * mean of what is in there — the only honest answer, and one that needs no record of each mouthful.
     */
    @Test void waterPassesThroughAndFatSits() {
        UUID quick = chronicle();
        emptyAndSound(quick);
        physiology.eatOneOf(quick, "clean_water", null);
        int fastRate = guts(quick, "stomach_digest_ml_per_hour");
        retire(quick);

        UUID slow = chronicle();
        emptyAndSound(slow);
        physiology.eatOneOf(slow, "pemmican", null);
        int slowRate = guts(slow, "stomach_digest_ml_per_hour");
        retire(slow);

        UUID both = chronicle();
        emptyAndSound(both);
        physiology.eatOneOf(both, "clean_water", null);
        physiology.eatOneOf(both, "pemmican", null);
        int mixedRate = guts(both, "stomach_digest_ml_per_hour");
        retire(both);

        assertTrue(fastRate > slowRate * 3, "water should leave far faster than fat: water=" + fastRate
            + " pemmican=" + slowRate);
        assertTrue(mixedRate > slowRate && mixedRate < fastRate,
            "a mixture digests between the two: water=" + fastRate + " mixed=" + mixedRate
                + " pemmican=" + slowRate);
    }

    /**
     * NOURISHMENT LEAVES THE STOMACH WITH THE BULK IT CAME IN WITH, and reaches the store. A tick that
     * empties half the stomach releases about half the nourishment — not all of it, and not none.
     */
    @Test void digestionTurnsWhatWasEatenIntoTheStoreItCameFrom() {
        UUID me = chronicle();
        emptyAndSound(me);
        physiology.eatOneOf(me, "cooked_game_meat", null);
        int bulkBefore = guts(me, "stomach_fullness_ml"), kcalBefore = guts(me, "stomach_kcal");
        int storeBefore = guts(me, "energy_reserves");

        readyToTick(me);                                   // 80 ml/h for four hours: about 320 of 700 ml
        ticks.advanceBy(java.time.Duration.ofHours(4));

        int bulkAfter = guts(me, "stomach_fullness_ml"), kcalAfter = guts(me, "stomach_kcal");
        int storeAfter = guts(me, "energy_reserves");
        retire(me);

        assertTrue(bulkAfter > 0 && bulkAfter < bulkBefore, "part of the meal should still be in there: "
            + bulkBefore + " -> " + bulkAfter);
        double bulkGone = (bulkBefore - bulkAfter) / (double) bulkBefore;
        double kcalGone = (kcalBefore - kcalAfter) / (double) kcalBefore;
        assertTrue(Math.abs(bulkGone - kcalGone) < 0.05,
            "nourishment must leave in step with the bulk: bulk " + bulkGone + " kcal " + kcalGone);

        // AND IT REACHES THE STORE — said as a comparison, because an absolute would be a claim about the
        // WEATHER. The canonical world is cold, and four hours of keeping warm can cost more than four
        // hours of a roast gives back, so "the store went up" is not a fact about digestion. "The fed body
        // ended better off than the unfed one" is.
        UUID unfed = chronicle();
        emptyAndSound(unfed);
        int unfedBefore = guts(unfed, "energy_reserves");
        ticks.advanceBy(java.time.Duration.ofHours(4));
        int unfedAfter = guts(unfed, "energy_reserves");
        retire(unfed);

        assertEquals(storeBefore, unfedBefore, "both bodies started with the same store");
        assertTrue(storeAfter > unfedAfter,
            "four hours of digesting a roast must leave a body better off than four hours of nothing: "
                + "fed " + storeBefore + "->" + storeAfter + ", unfed " + unfedBefore + "->" + unfedAfter);
    }

    /**
     * AND NUTRITION FOLLOWS WHAT THE FOOD WAS, not merely that something was eaten. The old rule asked
     * {@code digested > 0}, which is true of a stomach full of leaves — so a Chronicle could live on
     * watercress and grow better nourished by the day.
     */
    @Test void livingOnLeavesDoesNotImproveNutrition() {
        UUID cress = chronicle();
        emptyAndSound(cress);
        jdbc.update("UPDATE chronicle_physiology SET nutrition=60 WHERE chronicle_id=?", cress);
        for (int i = 0; i < 40; i++) physiology.eatOneOf(cress, "watercress_bundle", null);
        readyToTick(cress);
        ticks.advanceBy(java.time.Duration.ofHours(24));
        int onLeaves = guts(cress, "nutrition");
        retire(cress);

        UUID fed = chronicle();
        emptyAndSound(fed);
        jdbc.update("UPDATE chronicle_physiology SET nutrition=60 WHERE chronicle_id=?", fed);
        physiology.eatOneOf(fed, "cooked_game_meat", null);
        physiology.eatOneOf(fed, "cooked_game_meat", null);
        readyToTick(fed);
        ticks.advanceBy(java.time.Duration.ofHours(24));
        int onMeat = guts(fed, "nutrition");
        retire(fed);

        assertTrue(onLeaves <= 60, "a diet of leaves should not improve how well fed a body is: " + onLeaves);
        assertTrue(onMeat > onLeaves, "and a proper meal should: leaves=" + onLeaves + " meat=" + onMeat);
    }

    // ---------------------------------------------------------------------------------------------
    // TRANSITION
    // ---------------------------------------------------------------------------------------------

    /**
     * A CHRONICLE WHO WAS MID-MEAL WHEN THE MIGRATION RAN KEEPS THAT MEAL (#962: "Provide compatible
     * values for existing food instances and a deliberate transition for existing Chronicles").
     *
     * <p>And the compatibility holds in the code too: a caller that has not yet named its food gets an
     * ordinary portion at the density the flat model implied, so nothing that worked before is starved by
     * this change.
     */
    @Test void aPortionWithNothingSaidAboutItIsStillAnOrdinaryPortion() {
        UUID me = chronicle();
        emptyAndSound(me);
        physiology.eat(me);
        int bulk = guts(me, "stomach_fullness_ml"), kcal = guts(me, "stomach_kcal");
        retire(me);

        assertEquals(320, bulk, "the unnamed portion is what it always was");
        assertTrue(kcal > 400 && kcal < 600,
            "and it is worth what the flat model made it worth, about 1.58 kcal/ml: " + kcal);
    }
}
