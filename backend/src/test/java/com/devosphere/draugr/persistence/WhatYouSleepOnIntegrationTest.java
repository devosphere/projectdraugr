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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #919 and #893 — WHAT YOU SLEEP ON, AND WHETHER IT SHOWS.
 *
 * <p>{@code beddedAt} kept a hand-written list of three project kinds, and the world had grown two more it
 * never learned. A Chronicle who built a RAISED_BED_PLATFORM or a SLEEPING_BENCH — both catalogued, both
 * buildable — lay down on it and was warmed by nothing, because literals cannot grow when the catalogue does.
 *
 * <p>And the bed was spent the moment a roof was overhead: {@code safe ? 1.5 : (bed ? 1.0 : 0.7)} means a
 * raised bed inside a debris hut rested a body exactly as well as its bare floor. A roof turns the weather
 * and a bed keeps the ground's cold out of you; neither does the other's work.
 *
 * <p>Then the words. {@code sleep} returned a bare "was it sheltered", so a Chronicle who had gathered reeds,
 * worked them into a pallet and lain down on it at an open camp was told they settled onto the bare ground.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WhatYouSleepOnIntegrationTest {

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
        UUID id = summary.id();
        UUID ground = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground, id);
        return id;
    }

    /** Nothing of anyone else's standing on this ground: this test measures what IT puts there. */
    private void clearTheGround(UUID chronicle) {
        jdbc.update("UPDATE construction_project SET state='DESTROYED' WHERE object_id IN " +
                    "(SELECT id FROM world_object WHERE current_location_id = " +
                    " (SELECT current_location_id FROM world_object WHERE id=?))", chronicle);
    }

    private void raise(UUID chronicle, String kind, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) " +
                    "SELECT ?,'CONSTRUCTION',?,current_location_id FROM world_object WHERE id=?", id, name, chronicle);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                    "VALUES (?,?,'COMPLETED',100,now(),100)", id, kind);
    }

    /** A body with the same debt every time, so the only thing that differs is what it lay on. */
    private void readyToSleep(UUID chronicle) {
        jdbc.update("UPDATE chronicle_physiology SET sleep_debt_hours=20, energy_level=40, hours_without_food=0, " +
                    "hours_without_water=0, core_temperature_c=37, wetness_level=60, illness_severity=0, " +
                    "injury_severity=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
    }

    private double debtAfterSleeping(UUID chronicle) {
        readyToSleep(chronicle);
        physiology.sleep(chronicle, 480);
        return jdbc.queryForObject("SELECT sleep_debt_hours FROM chronicle_physiology WHERE chronicle_id=?",
            Double.class, chronicle);
    }

    /**
     * EVERY BEDDING THE CATALOGUE DECLARES IS ONE TO SLEEP ON — not the three a list in Java happened to name.
     * The two that were missed, RAISED_BED_PLATFORM and SLEEPING_BENCH, are what #919 was filed about.
     */
    @Test
    void everyCataloguedBeddingRestsABodyBetterThanBareGround() {
        UUID chronicle = chronicle();
        clearTheGround(chronicle);
        double onBareGround = debtAfterSleeping(chronicle);

        List<String> kinds = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE is_bedding ORDER BY project_kind", String.class);
        assertTrue(kinds.size() >= 5, () -> "the catalogue should know several beddings, found " + kinds);

        List<String> noBetterThanTheGround = new ArrayList<>();
        for (String kind : kinds) {
            clearTheGround(chronicle);
            raise(chronicle, kind, kind);
            if (debtAfterSleeping(chronicle) >= onBareGround) noBetterThanTheGround.add(kind);
        }
        assertEquals(List.of(), noBetterThanTheGround,
            () -> "sleeping on these left the body no better rested than the bare earth: " + noBetterThanTheGround);
        clearTheGround(chronicle);
    }

    /**
     * #919's own acceptance: "sleeping on a raised bed inside a debris hut should retain more core heat than
     * sleeping on bare ground inside that hut". Measured as rest, which is what sleep moves.
     */
    @Test
    void aBedUnderARoofBeatsABareFloorUnderTheSameRoof() {
        UUID chronicle = chronicle();

        clearTheGround(chronicle);
        raise(chronicle, "DEBRIS_HUT", "Debris hut");
        double underCoverOnTheFloor = debtAfterSleeping(chronicle);

        clearTheGround(chronicle);
        raise(chronicle, "DEBRIS_HUT", "Debris hut");
        raise(chronicle, "RAISED_BED_PLATFORM", "Raised bed platform");
        double underCoverOnABed = debtAfterSleeping(chronicle);

        assertTrue(underCoverOnABed < underCoverOnTheFloor,
            () -> "a bed under a roof must beat that roof's bare floor: floor left " + underCoverOnTheFloor
                  + "h of debt, bed left " + underCoverOnABed + "h");
        clearTheGround(chronicle);
    }

    /**
     * #893: the sleep REPORTS what it was slept on. The wording built from this is asserted in
     * {@code AFatalSleepDoesNotWakeTest}, beside the narration itself; what matters here is that the fact
     * leaves the physiology at all, which is what it never used to do.
     */
    @Test
    void theSleepReportsWhatItWasSleptOn() {
        UUID chronicle = chronicle();

        clearTheGround(chronicle);
        readyToSleep(chronicle);
        ChroniclePhysiologyService.Slept bare = physiology.sleep(chronicle, 480);
        assertTrue(!bare.bedded(), "nothing was built: this is the bare ground");
        assertTrue(!bare.sheltered(), "and nothing is over it");

        clearTheGround(chronicle);
        raise(chronicle, "GROUND_BED", "Bedding pallet");
        readyToSleep(chronicle);
        ChroniclePhysiologyService.Slept onAPallet = physiology.sleep(chronicle, 480);
        assertTrue(onAPallet.bedded(), "a pallet is somewhere to sleep, and the sleep must say so");
        assertTrue(!onAPallet.sheltered(), "with nothing over it — which is the pair #893 could not tell apart");
        clearTheGround(chronicle);
    }

    /**
     * #919: MORE COMFORTABLE BEDDING RESTORES MORE. V470 made every catalogued bedding count and left them
     * all worth exactly the same — a groundsheet, a thick reed pallet and a built sleeping bench each took
     * the same off a night, so going to the trouble of raising a platform bought nothing over throwing down
     * a bark sheet. The grades are the catalogue's (V471) and this walks them in order.
     */
    @Test
    void aBetterBedRestsABodyBetter() {
        UUID chronicle = chronicle();
        clearTheGround(chronicle);
        double onBareGround = debtAfterSleeping(chronicle);

        // Every grade the catalogue declares, worst first, each one better than the last.
        List<Integer> grades = jdbc.queryForList(
            "SELECT DISTINCT bedding_comfort FROM construction_kind WHERE is_bedding ORDER BY bedding_comfort",
            Integer.class);
        assertTrue(grades.size() >= 3, () -> "bedding should come in grades, found " + grades);

        double previous = onBareGround;
        for (int grade : grades) {
            String kind = jdbc.queryForObject(
                "SELECT project_kind FROM construction_kind WHERE is_bedding AND bedding_comfort=? ORDER BY project_kind LIMIT 1",
                String.class, grade);
            clearTheGround(chronicle);
            raise(chronicle, kind, kind);
            double left = debtAfterSleeping(chronicle);
            final double was = previous;
            assertTrue(left < was,
                () -> "grade " + grade + " (" + kind + ") must rest a body better than the grade below it: "
                      + "left " + left + "h of debt against " + was + "h");
            previous = left;
        }
        clearTheGround(chronicle);
    }

    /**
     * #919: THE COLD COMES UP OUT OF THE GROUND. Measured in isolation, because the metabolic tick's own
     * drift over a night is several degrees and would drown a term this size — a bed is a bed and not a
     * fire. Two sleeps from the identical starting core, nothing ticking in between.
     */
    @Test
    void aBedKeepsTheGroundsColdOutOfASleepingBody() {
        UUID chronicle = chronicle();

        clearTheGround(chronicle);
        readyToSleep(chronicle);
        jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=34.5 WHERE chronicle_id=?", chronicle);
        physiology.sleep(chronicle, 480);
        double onBareGround = jdbc.queryForObject(
            "SELECT core_temperature_c FROM chronicle_physiology WHERE chronicle_id=?", Double.class, chronicle);

        clearTheGround(chronicle);
        raise(chronicle, "SLEEPING_BENCH", "Sleeping bench");
        readyToSleep(chronicle);
        jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=34.5 WHERE chronicle_id=?", chronicle);
        physiology.sleep(chronicle, 480);
        double onABed = jdbc.queryForObject(
            "SELECT core_temperature_c FROM chronicle_physiology WHERE chronicle_id=?", Double.class, chronicle);

        assertTrue(onABed > onBareGround,
            () -> "a night off the cold ground must leave a body warmer: bare " + onBareGround
                  + "C against bedded " + onABed + "C");
        assertTrue(onABed <= 37.0, () -> "and never warmer than a body gets: " + onABed + "C");
        clearTheGround(chronicle);
    }

    /**
     * #919's last ask: bedding that is missing, ruined or not finished yet buys nothing. The conditions have
     * always been in the query; nothing held them, so nothing would have noticed them going.
     */
    @Test
    void beddingNotFitToLieOnBuysNothing() {
        UUID chronicle = chronicle();
        clearTheGround(chronicle);
        double onBareGround = debtAfterSleeping(chronicle);

        clearTheGround(chronicle);
        raiseAs(chronicle, "SLEEPING_BENCH", "Half-built bench", "IN_PROGRESS", 100);
        assertEquals(onBareGround, debtAfterSleeping(chronicle), 0.001,
            "a bench nobody has finished is not somewhere to sleep");

        clearTheGround(chronicle);
        raiseAs(chronicle, "SLEEPING_BENCH", "Ruined bench", "COMPLETED", 0);
        assertEquals(onBareGround, debtAfterSleeping(chronicle), 0.001,
            "and neither is one worn through to nothing");
        clearTheGround(chronicle);
    }

    private void raiseAs(UUID chronicle, String kind, String name, String state, int integrity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) " +
                    "SELECT ?,'CONSTRUCTION',?,current_location_id FROM world_object WHERE id=?", id, name, chronicle);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                    "VALUES (?,?,?,100,now(),?)", id, kind, state, integrity);
    }
}
