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
 * Your own gear and the sky (#37, V400).
 *
 * <p>Act fifteen swept the two domains the earlier acts never did — what a person says about their own gear,
 * and what they say about the world over their head. <b>22 of 35 phrasings reached nothing</b>, and in both
 * cases the answer was sitting in a column the simulation maintains.
 *
 * <p><b>The sky.</b> {@code world_weather.wind_speed_kph} is the sharpest of them: a column the weather
 * simulation keeps, carried all the way into {@link com.devosphere.draugr.simulation.BiomeClimate.Local} as a
 * wind FELT at this elevation and aspect, read by the body when it decides how fast you lose heat — and
 * <b>no sentence in the game could reach it</b>. The hour and the month were the same story in
 * {@code simulation_clock}. Day runs 06:00–20:00, the same hours the fine-work check uses, so the answer about
 * the light can never disagree with whether close work is possible.
 *
 * <p><b>Your own gear.</b> {@code condition_state} (SOUND / WORN / BROKEN), {@code use_count} and
 * {@code quality_grade} have been on every item since the table existed, and the LOAD is computed on every
 * single action. {@code how heavy is my pack} did reach MEASURE, and answered <i>"You have nothing by that name
 * in hand to weigh"</i> — the load computed on every action, answered as though a pack were an object to put on
 * scales.
 *
 * <p>A sentence that NAMES a thing is answered about that thing. The first cut answered <i>"how is my axe"</i>
 * with a list of the Chronicle's clothes, which is the approximate answer this project triages above a missing
 * one.
 *
 * <p><b>22 → 2</b>, and both remainders are unmodelled rather than unrouted: oiling leather and greasing an
 * axle have no maintenance mechanic behind them. Skips without Docker.
 */
@SpringBootTest
class YourOwnGearAndTheSkyIntegrationTest {

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
        // Mid-morning in June: the light is up, so the sky reading's daylight branch is the one under test.
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T10:00:00Z' WHERE id=1");
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
    void theSkyAnswersTheQuestionsItHasAlwaysKnown() {
        awaken();
        Map<String, String> asked = new LinkedHashMap<>();
        asked.put("what time is it", "READ_THE_SKY");
        asked.put("how long until dark", "READ_THE_SKY");
        asked.put("is it getting dark", "READ_THE_SKY");
        asked.put("how high is the sun", "READ_THE_SKY");
        asked.put("what month is it", "READ_THE_SKY");
        asked.put("is it near winter", "READ_THE_SKY");
        asked.put("what is the wind doing", "READ_THE_SKY");
        asked.put("is the wind getting up", "READ_THE_SKY");
        asked.put("is it going to rain", "READ_THE_SKY");
        asked.put("is there frost", "READ_THE_SKY");
        asked.put("will it freeze tonight", "READ_THE_SKY");
        asked.put("which way is north", "READ_THE_SKY");
        eachReaches(asked);

        // And the reading itself must carry the four things it exists for: the hour, the light left, the month,
        // and the WIND — the column that was maintained and unaskable.
        String sky = actions.resolve("what is the wind doing").perception();
        assertTrue(sky.contains("hours of light") || sky.contains("working light"),
            () -> "the sky must say how much light is left: " + sky);
        assertTrue(sky.contains("June"), () -> "and the month: " + sky);
        assertTrue(sky.toLowerCase(java.util.Locale.ROOT).contains("wind") || sky.contains("air is almost still")
                || sky.contains("breeze"), () -> "and the wind, which nothing could ask before: " + sky);
        assertTrue(sky.contains("north"), () -> "and the bearing the sun gives: " + sky);
    }

    @Test
    void andTheGearAnswersForItself() {
        UUID chronicle = awaken();
        Map<String, String> asked = new LinkedHashMap<>();
        asked.put("what am I carrying", "TAKE_STOCK_OF_GEAR");
        asked.put("check my tools", "TAKE_STOCK_OF_GEAR");
        asked.put("what tools do I have", "TAKE_STOCK_OF_GEAR");
        asked.put("is anything broken", "TAKE_STOCK_OF_GEAR");
        asked.put("am I carrying too much", "TAKE_STOCK_OF_GEAR");
        asked.put("how heavy is my pack", "TAKE_STOCK_OF_GEAR");
        eachReaches(asked);

        // A Chronicle awakens clothed, so the reading has something to name and says how the load sits.
        String gear = actions.resolve("what am I carrying").perception();
        assertTrue(gear.contains("to hand") || gear.contains("carrying nothing"),
            () -> "the reading must name what is carried: " + gear);
        assertTrue(gear.contains("load") || gear.contains("travelling light") || gear.contains("room for"),
            () -> "and say how near the limit it sits — the figure computed on every action: " + gear);

        // A sentence that NAMES a thing is answered about THAT thing, not with the whole inventory.
        String shirt = jdbc.queryForObject(
            "SELECT lower(d.display_name) FROM item_instance i JOIN item_definition d ON d.item_key=i.item_key " +
            "JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            "ORDER BY d.display_name LIMIT 1", String.class, chronicle);
        Assumptions.assumeTrue(shirt != null, "this needs the Chronicle to be carrying something");
        ChronicleActionService.ActionResult named = actions.resolve("how is my " + shirt);
        assertEquals("TAKE_STOCK_OF_GEAR", named.intent(), () -> "asking after one thing is still the gear question");
        assertTrue(named.perception().startsWith("The " + shirt),
            () -> "asked about the " + shirt + ", the answer must be about the " + shirt + ": " + named.perception());
        assertTrue(named.perception().length() < 200,
            () -> "and about that alone, not the whole pack: " + named.perception());

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void andNothingWithABetterClaimLosesIt() {
        awaken();
        Map<String, String> held = new LinkedHashMap<>();
        // FEEL owns the season and the temperature, and IntentClassificationRegressionTest holds it to them.
        // "what time OF YEAR is it" is a season question, and the first cut of the sky rule took it.
        held.put("what time of year is it", "FEEL");
        held.put("what season is it", "FEEL");
        held.put("how cold is it", "FEEL");
        held.put("what is the weather doing", "FEEL");
        held.put("feel the air", "FEEL");
        // The other stocktakes keep their own subjects.
        held.put("check my food stores", "TAKE_STOCK_OF_FOOD");
        held.put("take stock of the camp", "TAKE_STOCK_OF_CAMP");
        held.put("how am I doing", "SENSE_BODY");
        // Mending and equipping are acts, not questions.
        held.put("mend my axe", "REPAIR_ITEM");
        held.put("what needs mending", "REPAIR_ITEM");
        held.put("equip my cloak", "EQUIP");
        // And the measures that were already answered.
        held.put("how far have I come", "MEASURE");
        held.put("how many days have I been here", "MEASURE");
        eachReaches(held);
    }

    @Test
    void andLookingAtTheSkyOrInYourPackChangesNeither() {
        UUID chronicle = awaken();
        // Read-only, asserted rather than assumed: the weather the world holds and the condition of what is
        // carried must both be untouched by being looked at.
        Integer windBefore = jdbc.queryForObject("SELECT wind_speed_kph FROM world_weather LIMIT 1", Integer.class);
        String conditionsBefore = jdbc.queryForObject(
            "SELECT COALESCE(string_agg(i.condition_state, ',' ORDER BY i.object_id), '') FROM item_instance i " +
            "JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=?", String.class, chronicle);
        for (int i = 0; i < 3; i++) { actions.resolve("what is the wind doing"); actions.resolve("what am I carrying"); }
        assertEquals(windBefore, jdbc.queryForObject("SELECT wind_speed_kph FROM world_weather LIMIT 1", Integer.class),
            "looking at the sky must not change the weather");
        assertEquals(conditionsBefore, jdbc.queryForObject(
            "SELECT COALESCE(string_agg(i.condition_state, ',' ORDER BY i.object_id), '') FROM item_instance i " +
            "JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=?", String.class, chronicle),
            "and looking in your pack must not wear what is in it");
    }

    @Test
    void andWhatIsUnmodelledStaysAnHonestMiss() {
        awaken();
        // Reporting the negative. Oiling leather and greasing an axle are the two phrasings act fifteen found
        // that this slice does NOT answer, and they are not routing gaps: there is no maintenance mechanic for
        // either, so routing them anywhere would be prose over nothing.
        for (String said : List.of("oil my boots", "grease the axle")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertNotEquals("TAKE_STOCK_OF_GEAR", r.intent(),
                () -> "\"" + said + "\" asks for maintenance that has no model, and must not be answered by a "
                    + "stocktake instead: " + r.intent() + " / " + r.perception());
        }
    }
}
