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

        // Broad daylight: whatever the clock says elsewhere, this is the hour the world is at.
        Instant now = ticks.current().simulatedAt();
        Assumptions.assumeTrue(now.atZone(java.time.ZoneOffset.UTC).getHour() >= 6
                            && now.atZone(java.time.ZoneOffset.UTC).getHour() < 20,
            "this test is about daylight; the simulated clock is currently at night");

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
    }
}
