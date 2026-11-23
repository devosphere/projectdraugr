package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #959 — A FULL STOMACH IS NOT A FED BODY.
 *
 * <p>One number was doing four jobs. {@code hours_without_food} rose with time, fell when you ate, drove
 * the Hunger label, and decided starvation death. So, measured against the old code: {@code eat()}
 * subtracted 8 hours, {@code eatCookedMeal()} 16, and "Starving" began at 336 — a Chronicle a fortnight
 * without food had to eat <b>forty-two</b> helpings to stop being Starving, and a single blackberry
 * relieved exactly as much as a whole roast fish, because the counter knew nothing about portions.
 *
 * <p>Four things now, because there are four: what is in the stomach, the store the body runs on, how well
 * it has been fed over weeks, and the damage done once the store is gone.
 *
 * <p>Skips without Docker. Every assertion here was also taken live against a booted stack on a throwaway
 * Postgres, because a suite that skips is not evidence.
 */
@SpringBootTest
class AFullStomachIsNotAFedBodyIntegrationTest {

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

    /** Put this body in a known state, so the test owns what it measures. */
    private void setGuts(UUID me, int fullness, int reserves, int nutrition, int starvation) {
        jdbc.update("UPDATE chronicle_physiology SET stomach_fullness_ml=?, energy_reserves=?, nutrition=?, " +
                    " starvation_severity=?, core_temperature_c=37.0, hours_without_water=0, " +
                    " hours_without_food=0, illness_severity=0, injury_severity=0, blood_loss_ml=0, " +
                    " last_metabolic_update=now() WHERE chronicle_id=?",
            fullness, reserves, nutrition, starvation, me);
    }

    private int guts(UUID me, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM chronicle_physiology WHERE chronicle_id=?",
            Integer.class, me);
    }

    /** THE FOUR EXIST AND ARE BOUNDED. A stomach that can hold anything is not a stomach. */
    @Test void theFourAreRealColumnsWithRealBounds() {
        for (String column : List.of("stomach_fullness_ml", "energy_reserves", "nutrition", "starvation_severity"))
            assertEquals(1, (int) jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                " WHERE table_name='chronicle_physiology' AND column_name=?", Integer.class, column),
                column + " should exist");

        UUID me = chronicle();
        // The CHECKs are the guarantee, not the code that writes them.
        for (String bad : List.of(
                "UPDATE chronicle_physiology SET energy_reserves = 140 WHERE chronicle_id='" + me + "'",
                "UPDATE chronicle_physiology SET nutrition = -5 WHERE chronicle_id='" + me + "'",
                "UPDATE chronicle_physiology SET starvation_severity = 200 WHERE chronicle_id='" + me + "'",
                "UPDATE chronicle_physiology SET stomach_fullness_ml = 9000 WHERE chronicle_id='" + me + "'")) {
            try {
                jdbc.update(bad);
                throw new AssertionError("the database should have refused: " + bad);
            } catch (org.springframework.dao.DataAccessException expected) { /* refused */ }
        }
    }

    /**
     * EATING FILLS A STOMACH, AND A STOMACH IS A STOMACH. #960's "prevent instant recovery through
     * repeated eating commands" is enforced here rather than by a rule about commands: what will not fit
     * does not go in.
     */
    @Test void aStomachTakesWhatItHoldsAndNoMore() {
        UUID me = chronicle();
        setGuts(me, 0, 50, 70, 0);

        physiology.eatPortion(me, 700, com.devosphere.draugr.quality.QualityGrade.SOUND, 0);
        int afterOne = guts(me, "stomach_fullness_ml");
        assertTrue(afterOne > 400, "a prepared meal should be felt: " + afterOne);

        // Told to eat four more times in the same breath.
        for (int i = 0; i < 4; i++) physiology.eatPortion(me, 700, com.devosphere.draugr.quality.QualityGrade.SOUND, 0);
        int afterFive = guts(me, "stomach_fullness_ml");
        assertTrue(afterFive <= ChroniclePhysiologyService.STOMACH_CAPACITY_ML,
            "five meals must not fit in one stomach: " + afterFive);
        assertTrue(afterFive > afterOne, "but the second did go in on top of the first: " + afterFive);
    }

    /**
     * HUNGER IS THE STOMACH, NOT THE CLOCK — and this is the defect the whole ticket is about. Hours since
     * eating is zero in both bodies below; one has eaten and one has not.
     */
    @Test void hungerReadsTheStomachAndNotTheHoursSinceEating() {
        UUID me = chronicle();

        setGuts(me, 0, 50, 70, 0);
        physiology.refreshBody(me);
        assertEquals("Empty", jdbc.queryForObject(
            "SELECT hunger FROM chronicle_body WHERE chronicle_id=?", String.class, me),
            "an empty stomach is Empty, whatever the clock says");

        setGuts(me, 1000, 50, 70, 0);
        physiology.refreshBody(me);
        assertEquals("Full", jdbc.queryForObject(
            "SELECT hunger FROM chronicle_body WHERE chronicle_id=?", String.class, me),
            "a full stomach is Full, whatever the clock says");
    }

    /**
     * #956's OWN EXAMPLE: "A Chronicle with a full stomach can still be malnourished and exhausted."
     *
     * <p>And the thing I could not give #958 at the time, because nothing modelled it: "Malnourished" is
     * derived now rather than invented.
     */
    @Test void aFullStomachOnAStarvedBodyStillReportsStarvedAndMalnourished() {
        UUID me = chronicle();
        setGuts(me, 1200, 5, 30, 40);
        physiology.refreshBody(me);

        assertEquals("Full", jdbc.queryForObject(
            "SELECT hunger FROM chronicle_body WHERE chronicle_id=?", String.class, me));

        String conditions = jdbc.queryForObject(
            "SELECT condition_summary FROM chronicle_body WHERE chronicle_id=?", String.class, me);
        assertTrue(conditions.contains("Malnourished") || conditions.contains("malnourished"),
            "a poorly fed body should say so: " + conditions);
        assertTrue(conditions.contains("underfed") || conditions.contains("Starving"),
            "and the starvation damage should be named: " + conditions);

        String health = jdbc.queryForObject("SELECT health FROM chronicle_body WHERE chronicle_id=?", String.class, me);
        assertTrue(!"Healthy".equals(health), "a starving body is not Healthy: " + health);
    }

    /**
     * AND THE STORED ROW AGREES WITH THE SCREEN. #957 asks for Health consistency "across the HUD,
     * self-assessment, action responses, reloads, and death snapshots"; the stored row took its Hunger from
     * the clock while the live read took it from the stomach, so a reload disagreed with what had just been
     * on screen. Found live: on screen "Full", after reload "Satisfied".
     */
    @Test void whatIsStoredIsWhatWasShown() {
        UUID me = chronicle();
        setGuts(me, 1200, 5, 30, 40);
        physiology.refreshBody(me);

        ChroniclePhysiologyService.BodyHudSnapshot live = physiology.activeBody();
        assertNotNull(live);
        assertEquals(live.hunger(), jdbc.queryForObject(
            "SELECT hunger FROM chronicle_body WHERE chronicle_id=?", String.class, me));
        assertEquals(live.health(), jdbc.queryForObject(
            "SELECT health FROM chronicle_body WHERE chronicle_id=?", String.class, me));
        assertEquals(live.condition(), jdbc.queryForObject(
            "SELECT condition_summary FROM chronicle_body WHERE chronicle_id=?", String.class, me));
    }

    /**
     * #959: "A small snack must not erase accumulated starvation." Verified live: 200 ml into an empty
     * starved body, one hour on, starvation unmoved at 40.
     */
    @Test void aSnackDoesNotUndoWhatGoingWithoutHasDone() {
        UUID me = chronicle();
        setGuts(me, 0, 0, 30, 40);
        physiology.eatPortion(me, 200, com.devosphere.draugr.quality.QualityGrade.SOUND, 0);

        assertEquals(40, guts(me, "starvation_severity"), "eating does not touch the damage");
        assertTrue(guts(me, "stomach_fullness_ml") > 0, "it does put something in the stomach");
        // Nor does the hours counter, which is history now: it is reset because they HAVE just eaten.
        assertEquals(0, jdbc.queryForObject(
            "SELECT round(hours_without_food) FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, me));
    }

    /** The three new metrics joined #953's ledger, so #960 has somewhere to record why they move. */
    @Test void theNewMetricsAreTrackedForCausalRecording() {
        for (String metric : List.of("ENERGY_RESERVES", "NUTRITION", "STARVATION_SEVERITY")) {
            assertEquals(1, (int) jdbc.queryForObject(
                "SELECT count(*) FROM metric_subject WHERE metric_key=? AND source_table='chronicle_physiology'",
                Integer.class, metric), metric + " should be a tracked metric");
            // They are not faculties that fade from disuse: they have their own arithmetic.
            assertEquals(0.0, jdbc.queryForObject(
                "SELECT slips_per_idle_week FROM metric_subject WHERE metric_key=?", Double.class, metric), 1e-9);
        }
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM metric_subject m WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns " +
            " WHERE table_name=m.source_table AND column_name=m.source_column)", Integer.class),
            "every tracked metric must point at a column that exists");
    }
}
