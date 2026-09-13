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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The iron pickaxe and the iron fish hook (#134, V317).
 *
 * <p>Mining and angling code read 'iron_pickaxe' and 'iron_fish_hook' beside their bronze siblings, and neither key
 * existed. These tests prove the iron pick now does what that code always offered, and that both tools are forged
 * from the iron bloom and reforge back into one.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class IronPickAndHookIntegrationTest {

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

    /** An iron pick, like a bronze one, quarries more stone than bare hands — the read was always there. */
    @Test
    void anIronPickQuarriesMoreStoneThanBareHands() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y DESC, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        int before = fieldStones(chronicle);
        actions.resolve("I gather stones from the ground.");
        int bareHanded = fieldStones(chronicle) - before;
        assertTrue(bareHanded > 0, "gathering stone bare-handed must yield some (else the test proves nothing)");

        items.createCarriedItem(chronicle, "iron_pickaxe", "Iron pickaxe", ticks.current().simulatedAt(), "TEST_SEED");
        int mid = fieldStones(chronicle);
        actions.resolve("I gather stones from the ground.");
        int withPick = fieldStones(chronicle) - mid;
        assertTrue(withPick > bareHanded, () -> "an iron pick must quarry more stone than bare hands: withPick=" + withPick + " bare=" + bareHanded);

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Both are forged from the iron bloom, and both reforge back into it: neither is a phantom nor a dead end. */
    @Test
    void bothAreForgedFromTheBloomAndReforgeBackIntoIt() {
        for (String tool : new String[]{"iron_pickaxe", "iron_fish_hook"}) {
            assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM material_process mp JOIN material_process_input i ON i.process_key=mp.process_key " +
                "WHERE mp.output_item_key=? AND i.item_key='iron_bloom'", Integer.class, tool), tool + " must be forged from the iron bloom");
            assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM material_process_input_group WHERE process_key='reforge_iron_scrap' AND item_key=?", Integer.class, tool),
                tool + " must reforge back to a bloom");
        }
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM process_mass_balance WHERE process_key IN ('forge_iron_pickaxe','forge_iron_fish_hook','reforge_iron_scrap') " +
            "AND max_output_grams > min_input_grams", Integer.class), "no iron forge or reforge may make matter");
    }
}
