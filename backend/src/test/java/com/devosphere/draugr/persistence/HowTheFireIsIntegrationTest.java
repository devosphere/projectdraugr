package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.FireService;
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
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the fire is (#37, V406).
 *
 * <p>{@code fire_state.fuel_minutes} is the burning time remaining to the minute: {@code light} sets it,
 * {@code feed} adds forty-five, {@code bank} rakes the coals tighter and raises both it and its ceiling,
 * {@code advanceTo} counts it down every turn of the world, {@code ChroniclePhysiologyService} reads it when it
 * decides how fast a body loses heat, and {@link PersistentStateAuditor} holds that a fire cannot be ALIGHT with
 * none of it left. <b>Not one sentence could ask after it.</b>
 *
 * <p>The routing is held by {@code IntentClassificationRegressionTest}, which needs no world. This holds the two
 * things only a world can answer: that the reading reports the fuel the tick actually spends, and that
 * <b>"will it last the night" turns on the two numbers that decide it</b> — fuel against the dark still to come.
 *
 * <p>The hearth is this test's own and is taken out again. A fire left burning in the shared world would warm
 * every test that ran afterwards, which is the first shape of order-dependence in the method doc.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class HowTheFireIsIntegrationTest {

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
    @Autowired FireService fires;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockWas;
    private UUID myHearth;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "blood_loss_ml=0, energy_level=100");
    }

    @AfterEach
    void putItOut() {
        // A fire left burning in the shared world would warm every test that ran after this one.
        if (myHearth != null) {
            jdbc.update("DELETE FROM fire_state WHERE construction_id=?", myHearth);
            jdbc.update("DELETE FROM object_transition WHERE object_id=?", myHearth);
            jdbc.update("DELETE FROM construction_project WHERE object_id=?", myHearth);
            jdbc.update("DELETE FROM world_object WHERE id=?", myHearth);
            myHearth = null;
        }
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

    /**
     * Raise a hearth on the Chronicle's ground with a given fuel load, stamped at the world's own clock.
     *
     * <p>Stamped at the CLOCK, not at the wall clock: {@code advanceTo} burns down the minutes between
     * {@code last_updated_at} and now, so a hearth dated by {@code now()} in a world pinned to 2031 arrives five
     * years stale and is cold before anything reads it. That is the second of the three things about pinning the
     * clock, and it cost a CI round the first time this project met it.
     *
     * <p>The COMPLETED state carries its own CHECK constraint — progress at 100 and a completion date — which is
     * the sort of thing a fixture discovers by being run for real rather than by being read.
     */
    private void hearthWith(UUID chronicle, int fuelMinutes, boolean alight) {
        String kind = jdbc.query("SELECT project_kind FROM construction_kind WHERE holds_fire LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        Assumptions.assumeTrue(kind != null, "no construction kind holds a fire in this catalogue");
        UUID where = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);

        myHearth = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id, lifecycle_state) " +
            "VALUES (?, 'STRUCTURE', 'Stone fire pit', ?, 'ACTIVE')", myHearth, where);
        jdbc.update("INSERT INTO construction_project (object_id, project_kind, state, progress_percent, completed_at, " +
            "integrity_percent, last_structural_update) VALUES (?,?,'COMPLETED',100," +
            "(SELECT simulated_at FROM simulation_clock WHERE id=1),100,(SELECT simulated_at FROM simulation_clock WHERE id=1))",
            myHearth, kind);
        jdbc.update("INSERT INTO fire_state (construction_id, active, fuel_minutes, last_updated_at) " +
            "VALUES (?,?,?,(SELECT simulated_at FROM simulation_clock WHERE id=1))", myHearth, alight, fuelMinutes);
    }

    private Instant clockNow() {
        return jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class).toInstant();
    }

    @Test
    void theReadingReportsTheFuelTheTickSpends() {
        UUID me = awaken();
        hearthWith(me, 300, true);

        String said = fires.fireReading(me, jdbc.queryForObject(
            "SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me), clockNow(), 0);
        String lower = said.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("alight"), () -> "a burning fire must read as alight: " + said);
        // Five hours of fuel: the reading must say hours, and must not claim minutes only.
        assertTrue(lower.contains("hour"), () -> "five hours of fuel must be reported in hours: " + said);
        assertFalse(lower.contains("minutes only"), () -> "a well-fed fire is not minutes from out: " + said);

        // And the number is the one the tick spends. Burn an hour of the world and the reading must fall.
        jdbc.update("UPDATE simulation_clock SET simulated_at = simulated_at + INTERVAL '2 hours' WHERE id=1");
        fires.advanceTo(clockNow());
        int left = jdbc.queryForObject("SELECT fuel_minutes FROM fire_state WHERE construction_id=?", Integer.class, myHearth);
        assertTrue(left < 300, () -> "two hours of the world must cost the fire fuel: " + left);
        assertTrue(left >= 120, () -> "and not more than the two hours it was: " + left);
    }

    @Test
    void willItLastTheNightTurnsOnTheTwoNumbersThatDecideIt() {
        UUID me = awaken();
        UUID where = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);

        // Two hours of fuel against eight hours of dark: it will not.
        hearthWith(me, 120, true);
        String short_ = fires.fireReading(me, where, clockNow(), 8);
        assertTrue(short_.toLowerCase(Locale.ROOT).contains("will not see the night out"),
            () -> "two hours of fuel does not cover eight hours of dark: " + short_);

        // The same hour, ten hours of fuel: it will.
        jdbc.update("UPDATE fire_state SET fuel_minutes=600 WHERE construction_id=?", myHearth);
        String ample = fires.fireReading(me, where, clockNow(), 8);
        assertTrue(ample.toLowerCase(Locale.ROOT).contains("see the night out"),
            () -> "ten hours of fuel covers eight hours of dark: " + ample);
        assertFalse(ample.toLowerCase(Locale.ROOT).contains("will not see"),
            () -> "and must not say both: " + ample);

        // By DAY the question does not arise, and the reading must not answer it unasked.
        String byDay = fires.fireReading(me, where, clockNow(), 0);
        assertFalse(byDay.toLowerCase(Locale.ROOT).contains("night out"),
            () -> "at noon there is no night to see out: " + byDay);
    }

    @Test
    void aColdHearthSaysSoAndSaysWhetherTheStoneIsStillWorthAnything() {
        UUID me = awaken();
        UUID where = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        hearthWith(me, 0, false);

        String said = fires.fireReading(me, where, clockNow(), 0);
        assertTrue(said.toLowerCase(Locale.ROOT).contains("cold"), () -> "a dead fire reads as cold: " + said);
        assertFalse(said.toLowerCase(Locale.ROOT).contains("alight"), () -> "and not as alight: " + said);

        // Whatever it says about the stone must agree with what the PROCESSES accept heat by — that is the whole
        // reason heatToWorkWith is shared. A reading that promised warmth a smelt then refused would contradict
        // the game rather than the world.
        boolean usable = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id " +
            "JOIN construction_project cp ON cp.object_id=fs.construction_id " +
            "JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "WHERE w.current_location_id=? AND fs.active=false AND ck.retained_heat_minutes > 0 " +
            "  AND fs.last_updated_at > CAST(? AS timestamptz) - make_interval(mins => ck.retained_heat_minutes))",
            Boolean.class, where, Timestamp.from(clockNow()));
        assertEquals(usable, said.toLowerCase(Locale.ROOT).contains("yesterday's heat"),
            () -> "the reading and the processes must agree about usable heat: usable=" + usable + " / " + said);
    }

    @Test
    void andTheQuestionReachesItThroughTheComposer() {
        UUID me = awaken();
        hearthWith(me, 240, true);
        ChronicleActionService.ActionResult r = actions.resolve("is the fire still going");
        assertEquals("CHECK_FIRE", r.intent(), () -> "the question must reach the reading: " + r.perception());
        assertTrue(r.perception().toLowerCase(Locale.ROOT).contains("alight"),
            () -> "and report the fire that is burning here: " + r.perception());

        // Looking at a fire changes nothing — the impact card says so and V406 guards it, and this is the
        // behaviour behind that claim: the fuel is exactly what it was.
        int before = jdbc.queryForObject("SELECT fuel_minutes FROM fire_state WHERE construction_id=?", Integer.class, myHearth);
        actions.resolve("how long will it burn");
        int after = jdbc.queryForObject("SELECT fuel_minutes FROM fire_state WHERE construction_id=?", Integer.class, myHearth);
        assertTrue(after <= before, () -> "asking must never ADD fuel: " + before + " -> " + after);

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
