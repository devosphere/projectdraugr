package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CHRONICLE WHO HAS PUT EVERYTHING RIGHT MUST HAVE SOMEWHERE TO GO BUT DOWN (#909), AND MUST BE TOLD WHAT
 * THE WORK COST THEM (#920).
 *
 * <p>Illness had only addition. Every source of it is answerable — wash, get dry, get warm, vent the hearth,
 * dig a latrine, dress the wound feeding it — and answering all of them left the Chronicle exactly as sick as
 * before. Above 80 that is Critical and at 100 it is Systemic Illness, so the one Chronicle who had done
 * everything the simulation asked still had no way back.
 *
 * <p>And ten overexertion strains landed in one life, each a real injury of 3, with not one of them mentioned
 * in the narration of the action that caused it. The player found out by reading the backend's history.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheBodyRecoversIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        return summary.id();
    }

    /**
     * The HUD's own reading, which derives health from the scalars live — the authority, not the
     * {@code chronicle_body} row, which is only refreshed when something asks it to be.
     */
    private String healthOf() {
        return physiology.activeBody().health();
    }

    @Test
    void anIllnessWithNothingStillDrivingItMends() {
        UUID chronicle = awaken();
        Instant now = ticks.current().simulatedAt();

        // The ground this is measured on must be the test's own. A camp choked with refuse breeds sickness
        // whatever the body is doing about it, and in a suite that shares one world the Chronicle is standing
        // wherever the last test left them — which on the first CI round was a river bank another test had
        // fouled to 85. Illness went UP, past 100, and the Chronicle died inside the tick; the physiology row
        // is not written on the death path, so it still read 90 and the assertion reported "it did not fall".
        jdbc.update("UPDATE chunk_refuse SET refuse_level=0 WHERE chunk_id IN " +
                    "(SELECT current_location_id FROM world_object WHERE id=?)", chronicle);

        // Chronicle 49's state, with every cause of it already answered: warm, dry, clean, fed, watered,
        // rested, and no wound severe enough to feed it. The only thing left is the sickness itself.
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=90, injury_severity=0, blood_loss_ml=0, " +
                    "hygiene_level=90, wetness_level=0, core_temperature_c=37.0, hours_without_food=4, " +
                    "hours_without_water=2, sleep_debt_hours=0, energy_level=90, energy_reserves=70, nutrition=70, " +
                    "starvation_severity=0, last_metabolic_update=? " +
                    "WHERE chronicle_id=?", java.sql.Timestamp.from(now), chronicle);
        assertEquals("Critical", healthOf(), "illness above 80 is Critical, and that part is unchanged");

        // A day and a half of KEEPING YOURSELF, which is the thing being claimed — not a day and a half of
        // standing still. Three hours at a time, washing, drying and warming between, because that is what a
        // Chronicle climbing out of a sickness actually does; left alone for 36 hours straight a body gets
        // filthy, wet and cold again and the pressure comes back, which is the simulation working, not a bug.
        //
        // The physiology tick is driven on its own rather than through the world clock: this suite shares one
        // world, and a 36-hour jump of the shared clock spoils food and weathers structures under every other
        // test in it.
        Instant later = now;
        for (int kept = 0; kept < 12; kept++) {
            jdbc.update("UPDATE chronicle_physiology SET hygiene_level=90, wetness_level=0, core_temperature_c=37.0, " +
                        "hours_without_food=4, hours_without_water=2, sleep_debt_hours=0, energy_reserves=70, nutrition=70, " +
                        "starvation_severity=0, last_metabolic_update=? " +
                        "WHERE chronicle_id=?", java.sql.Timestamp.from(later), chronicle);
            later = later.plus(Duration.ofHours(3));
            physiology.advanceTo(later);
        }

        assertEquals("LIVING", jdbc.queryForObject("SELECT life_state FROM chronicle WHERE id=?", String.class, chronicle),
            "the Chronicle must come through it alive — a recovery that kills you is not one");
        int illness = jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?",
            Integer.class, chronicle);
        assertTrue(illness < 90, "illness must be able to FALL at all once nothing is driving it: " + illness);
        assertTrue(illness <= 80, "and far enough to leave Critical behind: " + illness);
        assertTrue(!"Critical".equals(healthOf()),
            "a living Chronicle must be able to climb out of an illness-driven Critical: " + healthOf());

        // AND IT STILL RISES WHEN SOMETHING IS DRIVING IT. A fix that made illness fall unconditionally would
        // have taken the teeth out of every source of it at once.
        Instant still = later.plus(Duration.ofHours(10));
        int before = jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?",
            Integer.class, chronicle);
        jdbc.update("UPDATE chronicle_physiology SET hygiene_level=0, last_metabolic_update=? WHERE chronicle_id=?",
            java.sql.Timestamp.from(later), chronicle);
        physiology.advanceTo(still);
        assertTrue(jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?",
            Integer.class, chronicle) > before, "filth must still breed sickness");

        // AND THE PLAYER IS TOLD WHICH WAY IT IS GOING, from what the tick measured rather than from a second
        // reading of a partial view of the world (#909).
        assertTrue(physiology.ailments(chronicle).illnessTrend() > 0,
            "a sickness being fed must be recorded as worsening");
        String worsening = actions.resolve("check myself").perception();
        assertTrue(worsening.contains("getting worse"), () -> "and the reading must say so: " + worsening);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void theActionThatStrainsYouSaysSo() {
        UUID chronicle = awaken();
        Instant now = ticks.current().simulatedAt();
        jdbc.update("UPDATE chronicle_physiology SET energy_level=5, injury_severity=0, last_metabolic_update=? WHERE chronicle_id=?",
            java.sql.Timestamp.from(now), chronicle);

        int strainsBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_condition_event WHERE chronicle_id=? AND condition_kind='STRAIN'",
            Integer.class, chronicle);
        ChronicleActionService.ActionResult worked = actions.resolve("gather field stone");
        int strainsAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_condition_event WHERE chronicle_id=? AND condition_kind='STRAIN'",
            Integer.class, chronicle);

        // The strain is the point; if this heavy labour did not strain a body at energy 5, the test proves
        // nothing and must say so rather than pass quietly.
        assertEquals(strainsBefore + 1, strainsAfter,
            () -> "heavy labour on an empty tank must strain the body: " + worked.perception());
        assertTrue(worked.perception().contains("strain"),
            () -> "the action that caused it must SAY so, in its own result: " + worked.perception());

        // ...and the history must keep it, because the player reads the timeline back after a relaunch.
        String stored = jdbc.queryForObject("SELECT narration FROM chronicle_action WHERE id=?", String.class, worked.actionId());
        assertTrue(stored != null && stored.contains("strain"),
            () -> "and the saved narration must keep it: " + stored);
    }
}
