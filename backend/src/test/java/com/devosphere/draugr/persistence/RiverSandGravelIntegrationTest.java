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
 * Story #55 - river sand and gravel are gatherable building stock. Proves that at riverbank/wetland ground a Chronicle
 * can gather each named aggregate by hand and hold a UUID-backed yield. Gathering is a chance per attempt, so the test
 * makes several attempts (the aggregate is abundant, rarity 0.9) and asserts the stock is obtained. Skips without Docker.
 */
@SpringBootTest
class RiverSandGravelIntegrationTest {

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

    @Test
    void sandAndGravelAreGatherableFromARiverBar() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome IN ('RIVER_BANK','WETLAND') ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must hold riverbank or wetland ground");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Gathering succeeds by chance each attempt (rarity 0.9, named ~0.675); over many attempts the abundant
        // aggregate is reliably obtained. No tool is needed — it is scooped by hand.
        boolean gotSand = gatherUntil("gather river sand from the bar", "river_sand", chronicle, chunk);
        assertTrue(gotSand, "river sand must be gatherable by hand from a river bar");
        boolean gotGravel = gatherUntil("gather river gravel from the bar", "river_gravel", chronicle, chunk);
        assertTrue(gotGravel, "river gravel must be gatherable by hand from a river bar");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * One pass through the real router, then the rest at the service boundary it dispatches into.
     *
     * <p>This loop used to make every attempt through {@code actions.resolve}, and each resolve advances the
     * world by the action's duration — forty minutes for a mineral search. Thirty attempts for the sand and
     * thirty for the gravel is up to forty simulated hours of a Chronicle standing in a marsh with no fire, no
     * shelter and nothing to drink, and the assertion is only reached if they survive it. They did not always:
     * the suite failed on development with "No living Chronicle exists", thrown from the next resolve after the
     * body gave out. Exposure or thirst, the test does not say which, and it should not have to — proving that
     * river sand is gatherable has nothing to do with how long a person lasts in a fen.
     *
     * <p>So the routing claim and the yield claim are made separately, and each is made where it belongs. The
     * first attempt goes through {@code resolve} and its intent is asserted, which is the only part of this that
     * needed the router at all: it proves "gather river sand from the bar" is heard as prospecting rather than
     * as foraging or as fishing. Every attempt after it calls {@code gatherMineral} directly — the same method
     * the router dispatches into, on the same text — so the chance-per-attempt is exercised exactly as before
     * without moving the clock at all.
     */
    private boolean gatherUntil(String phrase, String itemKey, UUID chronicle, UUID chunk) {
        assertEquals("GATHER_MINERAL", actions.resolve(phrase).intent(),
            () -> "the phrase must be heard as prospecting, or the rest of this proves nothing: " + phrase);
        // Naming a specific mineral searches at rarity*0.75 — about 0.26 for these aggregates — so thirty
        // attempts left roughly a one-in-eight-thousand chance of turning none up, which across a suite run
        // often enough is a flake waiting its turn. The attempts cost no simulated time now, so there is no
        // longer any reason to be stingy with them: eighty puts it past one in ten billion.
        Instant at = ticks.current().simulatedAt();
        for (int i = 0; i < 80 && !items.hasAtLeast(chronicle, itemKey, 1); i++) {
            items.gatherMineral(chronicle, chunk, phrase, at);
        }
        return items.hasAtLeast(chronicle, itemKey, 1);
    }
}
