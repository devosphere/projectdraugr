package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
import com.devosphere.draugr.item.PhysicalItemService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #870 and #904 — THE METHOD KNEW, AND THE SENTENCE NEVER ASKED.
 *
 * <p>Two reports, one shape. {@code workLeanTo} is the step that sets a lean-to COMPLETED and renames the
 * thing from "Lean-to frame" to "Lean-to" — and returned a bare true, the same true as the first course of
 * four, so the bind that finished a shelter said "a little further into place" and the atmosphere beside it
 * went on calling it a half-built frame while the player was already sheltering under it.
 *
 * <p>And a making never fails for want of carrying room: the thing is set on the ground in front instead
 * (#19). Nothing said so, and {@code craftBasket} returned the Chronicle as the owner whatever had happened
 * — so a basket woven with a full pack was described as made and then, on {@code drop the woven basket},
 * denied: "you are not carrying anything by that name".
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheMakingSaysWhatItDidIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired JdbcTemplate jdbc;

    private UUID aChronicleOnForestGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID ground = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground, chronicle);
        // The work moves the clock, and a body that starves or freezes partway measures nothing.
        jdbc.update("UPDATE chronicle_physiology SET hours_without_food=0, hours_without_water=0, energy_level=100, " +
                    "core_temperature_c=37, wetness_level=0, illness_severity=0, injury_severity=0, blood_loss_ml=0 " +
                    "WHERE chronicle_id=?", chronicle);
        return chronicle;
    }

    private void roomToCarryAnything(UUID chronicle) {
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
                    "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
    }

    private void give(UUID chronicle, String key, String name, int count) {
        Instant at = ticks.current().simulatedAt();
        for (int i = 0; i < count; i++) items.createCarriedItem(chronicle, key, name, at, "TEST_SEED");
    }

    /** #870: the bind that finishes a shelter says it is finished. */
    @Test
    void theCourseThatFinishesTheShelterSaysSo() {
        UUID chronicle = aChronicleOnForestGround();
        roomToCarryAnything(chronicle);
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        // This test owns its own shelter: a frame another suite left standing here would be worked to a
        // different percentage and the course that finishes it would not be the one counted below.
        jdbc.update("UPDATE construction_project SET state='DESTROYED' WHERE project_kind='LEAN_TO' AND object_id IN " +
                    "(SELECT id FROM world_object WHERE current_location_id=?)", here);
        give(chronicle, "dry_branch", "Dry branch", 8);
        give(chronicle, "plant_fiber", "Plant fiber", 8);

        ChronicleActionService.ActionResult started = actions.resolve("start a lean-to");
        assertEquals("SUCCEEDED", started.outcome(), started::perception);

        // Each course is a quarter. The ones before the last say so, and must not claim completion.
        for (int course = 1; course <= 3; course++) {
            ChronicleActionService.ActionResult bound = actions.resolve("work on the lean-to");
            assertEquals("SUCCEEDED", bound.outcome(), bound::perception);
            if (construction.finishedLeanToHere(here)) break;
            final int c = course;
            assertFalse(bound.perception().contains("stands finished"),
                () -> "course " + c + " of four must not claim the shelter is done: " + bound.perception());
        }

        ChronicleActionService.ActionResult last = actions.resolve("work on the lean-to");
        assertEquals("SUCCEEDED", last.outcome(), last::perception);
        assertTrue(construction.finishedLeanToHere(here), () -> "the fourth course finishes it: " + last.perception());
        assertTrue(last.perception().contains("stands finished"),
            () -> "and the sentence that reports it must say so: " + last.perception());
        assertFalse(last.perception().contains("a little further into place"),
            () -> "a finished shelter is not a frame bound a little further: " + last.perception());
    }

    /** #904: a thing made with no room to carry it says where it went — and really is there. */
    @Test
    void aBasketWithNoRoomToCarryItSaysWhereItWent() {
        UUID chronicle = aChronicleOnForestGround();
        roomToCarryAnything(chronicle);
        give(chronicle, "plant_fiber", "Plant fiber", 12);

        // With room, the sentence says nothing extra — the ordinary case wants no words.
        ChronicleActionService.ActionResult carried = actions.resolve("weave a basket");
        assertEquals("SUCCEEDED", carried.outcome(), carried::perception);
        assertFalse(carried.perception().contains("rests on the ground"),
            () -> "a basket you can carry is not reported as set down: " + carried.perception());

        // And with none at all, it says so, and the basket really is on the ground.
        give(chronicle, "plant_fiber", "Plant fiber", 12);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=1, direct_bulk_ml=1, " +
                    "maximum_single_lift_grams=1 WHERE chronicle_id=?", chronicle);
        ChronicleActionService.ActionResult setDown = actions.resolve("weave a basket");
        assertEquals("SUCCEEDED", setDown.outcome(), setDown::perception);
        assertTrue(setDown.perception().contains("rests on the ground"),
            () -> "a basket there is no room to carry must be reported as set down: " + setDown.perception());

        Integer onTheGround = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE i.item_key='woven_basket' AND w.lifecycle_state='ACTIVE' AND w.current_owner_id IS NULL " +
            "  AND w.current_location_id IS NOT NULL", Integer.class);
        assertNotNull(onTheGround);
        assertTrue(onTheGround >= 1, "and the world must agree with the sentence: the basket is on the ground");
    }
}
