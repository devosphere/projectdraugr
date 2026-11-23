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

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        return summary.id();
    }

    /** Set the body, and put the metabolic clock back by `hoursAgo` so the next tick spans that long. */
    private void body(UUID me, int fullness, int reserves, int nutrition, int starvation,
                      double hoursSinceFood, int hoursAgo) {
        jdbc.update("UPDATE chronicle_physiology SET stomach_fullness_ml=?, energy_reserves=?, nutrition=?, " +
                    " starvation_severity=?, hours_without_food=?, hours_without_water=0, " +
                    " core_temperature_c=37.0, illness_severity=0, injury_severity=0, blood_loss_ml=0, " +
                    " sleep_debt_hours=0, energy_level=70, " +
                    " last_metabolic_update = now() - make_interval(hours => ?) WHERE chronicle_id=?",
            fullness, reserves, nutrition, starvation, hoursSinceFood, hoursAgo, me);
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
     * #960: "Severe deprivation should take longer to recover from than a short deficit." Two bodies, the
     * same meal and the same hours; the deeper one gets less of it back.
     */
    /**
     * SUSTAINED intake, which is what the ticket means and what I got wrong twice before measuring.
     *
     * <p>One stomachful does not feed a body for forty hours: 1200 ml digests out in about thirteen, and the
     * remaining twenty-seven are a body with nothing going in — so a single long tick made BOTH bodies
     * worse (shallow 12 → 13, deep 90 → 99) and read as though mending did not work at all. Over ten hours
     * instead, the deep rate is real but rounds to zero, which reads as stuck rather than slow.
     *
     * <p>Four fed ten-hour stretches is what "adequate sustained intake" actually looks like, and it is the
     * only shape in which the comparison is a statement about the RATE.
     */
    private int mendedOverFourFedStretches(int startingStarvation) {
        UUID me = chronicle();
        jdbc.update("UPDATE chronicle_physiology SET starvation_severity=?, energy_reserves=60, nutrition=60 " +
                    " WHERE chronicle_id=?", startingStarvation, me);
        for (int stretch = 0; stretch < 4; stretch++) {
            jdbc.update("UPDATE chronicle_physiology SET stomach_fullness_ml=1200, hours_without_food=0, " +
                        " hours_without_water=0, sleep_debt_hours=0, core_temperature_c=37.0, " +
                        " illness_severity=0, injury_severity=0, blood_loss_ml=0, " +
                        " last_metabolic_update = now() - make_interval(hours => 10) WHERE chronicle_id=?", me);
            ticks.advanceBy(java.time.Duration.ofHours(10));
        }
        int mended = startingStarvation - guts(me, "starvation_severity");
        jdbc.update("UPDATE chronicle SET life_state='DEAD', died_at=now(), death_cause='test' WHERE id=?", me);
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

    /** #960: mending wants water and rest too, not only food. */
    @Test void aBodyMendsOnFoodAndWaterAndRestTogether() {
        UUID kept = chronicle();
        body(kept, 1200, 60, 60, 40, 0, 10);
        ticks.advanceBy(java.time.Duration.ofHours(10));
        int keptMended = 40 - guts(kept, "starvation_severity");
        jdbc.update("UPDATE chronicle SET life_state='DEAD', died_at=now(), death_cause='test' WHERE id=?", kept);

        UUID parched = chronicle();
        body(parched, 1200, 60, 60, 40, 0, 10);
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=40, sleep_debt_hours=40 WHERE chronicle_id=?", parched);
        ticks.advanceBy(java.time.Duration.ofHours(10));
        int parchedMended = 40 - guts(parched, "starvation_severity");

        assertTrue(keptMended > parchedMended,
            "a fed body that is also watered and rested should mend faster: kept=" + keptMended
                + " parched and sleepless=" + parchedMended);
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
