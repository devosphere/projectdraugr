package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
 * It is dark in the rock at any hour (#75/#158).
 *
 * <p>The perception written for a cave mouth says the daylight gets a few paces in and no further, and the
 * mechanics did not agree with it: fine sight-work turned only on the clock, so a Chronicle could thread a needle
 * at the back of a cave at midday and be refused for the same work on open grass at dusk. This is what makes a
 * lamp, a rushlight or a hooded flame worth carrying to a PLACE rather than only to an hour.
 *
 * <p>Deliberately a separate question from nightfall, and this proves the separation holds: the night predator
 * raid still reads the clock, so standing in a cave at noon must not bring wolves onto the stock. Skips without
 * Docker.
 */
@SpringBootTest
class DarkInTheRockIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Take every light off the Chronicle, setting them down rather than orphaning them. */
    private void douse(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE current_owner_id=? AND id IN (" +
            "SELECT object_id FROM item_instance WHERE item_key IN " +
            "('firebrand','resin_torch','rush_light','tallow_candle','oil_lamp','fish_oil','rendered_tallow','stone_lantern_cover'))",
            chunk, chronicle);
        jdbc.update("DELETE FROM fire_state fs USING construction_project cp, world_object w " +
            "WHERE fs.construction_id=cp.object_id AND w.id=cp.object_id AND w.current_location_id=?", chunk);
    }

    @Test
    void closeWorkInACaveNeedsALightEvenAtMidday() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        UUID cave = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='CAVE_MOUTH' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID open = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(cave, "the world must have a cave mouth");
        assertNotNull(open, "the world must have open ground to compare against");

        // BROAD DAYLIGHT AND A FAIR SKY, PINNED RATHER THAN HOPED FOR, because this test owns both.
        //
        // It used to ASSUME the hour instead, reading the clock and skipping if it was night. That read one
        // instant and the action resolved at another — every action costs simulated minutes — so a run that
        // started at 19:50 passed the assumption and then did its work in the dark. It also never looked at the
        // sky at all, and a snowstorm darkens the day enough that the refusal fires on open grass. CI found both
        // at once: "close work on open ground in daylight must not be refused for want of light", answered with
        // "the weather takes any flame you try to strike" and snow coming down.
        //
        // Neither was a fault in the world. The control simply was not a control. Pinned and restored on the
        // #643 pattern, which the suite already uses for exactly this: the clock is shared by ~1,170 tests, so
        // whatever ran before this decides what hour it is unless the test says.
        //
        // THE CLOCK PIN IS THE LOAD-BEARING HALF, and the weather pin is not — measured on a booted backend
        // against a throwaway database, where a forced CLEAR was already back to RAIN by the time the action
        // resolved, because WeatherSimulationService.advanceTo recomputes the sky from (season, seed, day). It
        // did not matter: tooDarkForFineWork is `isDark(at) || a cave biome`, so at noon on open grass the
        // refusal cannot fire whatever the sky is doing, and the weather only ever chose the WORDING of a
        // refusal that is no longer reached. The pin is kept because it costs nothing and makes a failure
        // message legible, but nothing here relies on it holding.
        // queryForList, not queryForObject: world_weather can be EMPTY when this test runs, and the first
        // version of this threw EmptyResultDataAccessException reading a row that was not there — breaking the
        // test a different way than the one being fixed. A pin of something absent is simply not a pin.
        java.util.List<java.sql.Timestamp> clockWas = jdbc.queryForList(
            "SELECT simulated_at FROM simulation_clock WHERE id=1", java.sql.Timestamp.class);
        java.util.List<String> skyWas = jdbc.queryForList(
            "SELECT weather_kind FROM world_weather LIMIT 1", String.class);
        java.sql.Timestamp wasAt = clockWas.isEmpty() ? null : clockWas.get(0);
        String wasWeather = skyWas.isEmpty() ? null : skyWas.get(0);
        try {
        jdbc.update("UPDATE simulation_clock SET simulated_at = date_trunc('day', simulated_at) + interval '12 hours' WHERE id=1");
        jdbc.update("UPDATE world_weather SET weather_kind='CLEAR', intensity=0");
        Instant now = ticks.current().simulatedAt();

        // On open ground in daylight, close work needs no light at all — the control.
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", open, chronicle);
        douse(chronicle, open);
        var outside = actions.resolve("measure how far it is to the far wall");
        assertTrue(!outside.perception().contains("too dark") && !outside.perception().contains("gets a few paces"),
            () -> "close work on open ground in daylight must not be refused for want of light: " + outside.perception());

        // The same work, the same hour, inside the rock: refused, and the refusal names the rock rather than the
        // hour — being told "it is too dark" at midday would read as a fault in the world.
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", cave, chronicle);
        douse(chronicle, cave);
        var inside = actions.resolve("measure how far it is to the far wall");
        assertEquals("FAILED", inside.outcome(), () -> "close work in a cave with no light must fail: " + inside.perception());
        assertTrue(inside.perception().contains("gets a few paces into the rock"),
            () -> "and it must say it is the rock, not the hour: " + inside.perception());

        // A light in hand, and the same work goes ahead.
        items.createCarriedItem(chronicle, "resin_torch", "Resin torch", now, "TEST_SEED");
        var lit = actions.resolve("measure how far it is to the far wall");
        assertTrue(!"FAILED".equals(lit.outcome()) || !lit.perception().contains("gets a few paces"),
            () -> "with a torch raised, the rock is no longer what stops the work: " + lit.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
        } finally {
            // Put the hour and the sky back, or this test becomes the reason the next one fails. Only what was
            // actually read is restored — writing a null back would be inventing a world state, not restoring one.
            if (wasAt != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", wasAt);
            if (wasWeather != null) jdbc.update("UPDATE world_weather SET weather_kind=?", wasWeather);
        }
    }

    /**
     * The light reading answers from the same rules the work uses (#37, V437).
     *
     * <p>Four finished rules met in the dark and none could be asked about: the hour and the rock decide whether
     * close work is possible, {@code consumePortableLight} spends a brand, a torch, a rushlight, a candle and
     * last a lamp — in that order — {@code guttering} knows the weather takes a bare flame, and a stone lantern
     * cover is what stops it. A Chronicle could learn all of it only by failing at close work and being told
     * about whichever piece had gone wrong first.
     *
     * <p>What matters here is not the prose but the AGREEMENT: the reading must say there is nothing to burn
     * exactly when there is nothing to burn, and name a light exactly when a light would be spent. A reading
     * that kept its own copy of the rule could drift from it, and this test is what makes that impossible.
     */
    @Test
    void theLightReadingAndTheWorkAgree() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        UUID cave = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='CAVE_MOUTH' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(cave, "the world must have a cave mouth");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", cave, chronicle);
        douse(chronicle, cave);

        // NOTHING TO BURN, and both halves must say so.
        ChronicleActionService.ActionResult dark = actions.resolve("is there enough light to work");
        assertEquals("JUDGE_LIGHT", dark.intent(), () -> "asking about the light must reach the reading: " + dark.perception());
        assertTrue(dark.perception().contains("nothing on you that will burn"),
            () -> "with every light set down, the reading must say so: " + dark.perception());
        assertFalse(items.consumePortableLight(chronicle, Instant.now()),
            "and the work must agree there is nothing to strike");

        // A LIGHT IN HAND, and both halves must say that.
        items.createCarriedItem(chronicle, "resin_torch", "Resin torch", Instant.now(), "TEST_SEED");
        ChronicleActionService.ActionResult lit = actions.resolve("is there enough light to work");
        assertEquals("JUDGE_LIGHT", lit.intent());
        assertTrue(lit.perception().contains("resin torch"),
            () -> "the reading must name the light that would be spent: " + lit.perception());
        assertFalse(lit.perception().contains("nothing on you that will burn"),
            () -> "and must not still claim there is nothing: " + lit.perception());

        // THE ORDER IS THE SPENDING ORDER, not a list this reading keeps of its own. A brand outranks a torch in
        // consumePortableLight, so it must be named first here too.
        items.createCarriedItem(chronicle, "firebrand", "Firebrand", Instant.now(), "TEST_SEED");
        ChronicleActionService.ActionResult both = actions.resolve("what have I got to burn");
        assertEquals("JUDGE_LIGHT", both.intent());
        assertTrue(both.perception().indexOf("burning brand") < both.perception().indexOf("resin torch"),
            () -> "the brand is spent before the torch and must be named before it: " + both.perception());

        // And the act spends the brand first, which is the fact the sentence above is reporting.
        assertTrue(items.consumePortableLight(chronicle, Instant.now()), "a brand in hand is a light");
        assertEquals(0, items.reachCountAt(chronicle, cave, "firebrand"),
            "the brand is what was spent, before the torch");
        assertTrue(items.hasAtLeast(chronicle, "resin_torch", 1), "and the torch is still there, unspent");

        // Asking about the light is a reading and must not be an act on the world.
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
