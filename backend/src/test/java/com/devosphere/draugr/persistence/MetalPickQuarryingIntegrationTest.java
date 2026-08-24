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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quarrying with a metal pick (EPIC #180 heavy industry / #183 quarrying). A metal pickaxe breaks building stone
 * out of the ground far faster than bare hands prising at it, so the metal a Chronicle smelts earns them stone at
 * scale too — the pick's second use, beside winning finer ore.
 *
 * <p>Proven: gathering stone with a bronze pickaxe to hand yields a markedly bigger load than gathering it
 * bare-handed. Skips gracefully without Docker.
 */
@SpringBootTest
class MetalPickQuarryingIntegrationTest {

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

    private int fieldStones(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='field_stone' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
    }

    @Test
    void aMetalPickQuarriesMoreStoneThanBareHands() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Bare-handed: prise a few stones loose from the ground.
        int before = fieldStones(chronicle);
        actions.resolve("I gather stones from the ground.");
        int bareHanded = fieldStones(chronicle) - before;
        assertTrue(bareHanded > 0, "gathering stone bare-handed must yield some (else the test proves nothing)");

        // With a bronze pickaxe to hand: the same ground gives up a much bigger load.
        items.createCarriedItem(chronicle, "bronze_pickaxe", "Bronze pickaxe", ticks.current().simulatedAt(), "TEST_SEED");
        int mid = fieldStones(chronicle);
        actions.resolve("I gather stones from the ground.");
        int withPick = fieldStones(chronicle) - mid;

        assertTrue(withPick > bareHanded,
                () -> "a metal pick must quarry more stone than bare hands: withPick=" + withPick + " bare=" + bareHanded + " (#183)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
