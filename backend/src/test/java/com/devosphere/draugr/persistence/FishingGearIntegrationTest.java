package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
 * Fishing-gear usefulness regression (M1 #36/#43, EPIC #123). A woven fish trap and a bone fish hook were
 * craftable but, like the net, read by nothing — so they caught no better than bare hands. fish() now reaches
 * for carried gear when no method is named: a fish trap works best, then nets, then a bone hook (angling).
 * This proves a trap and a hook each land more than empty hands over the same fixed set of casts.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class FishingGearIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int landed(UUID chronicle, UUID chunk, Instant now, java.util.List<UUID> actionIds) {
        // Measure gear effectiveness from a full stretch: reset the finite fish stock (#181/#36) first, so this
        // battery reflects the method's catch chance, not how much prior fishing (here or in another test sharing the
        // database) has drawn the water down.
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", chunk);
        int caught = 0;
        for (UUID action : actionIds)
            if ("SUCCEEDED".equals(wildlife.fish(chronicle, chunk, action, now, "I fish in the shallows here.").outcome())) caught++;
        return caught;
    }

    @Test
    void aFishTrapAndABoneHookEachOutfishBareHands() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the approved world must contain a wetland chunk with fish");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        java.util.Random rnd = new java.util.Random(23);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        int bareHanded = landed(chronicle, chunk, now, actionIds);

        // A bone hook — angling, method LINE (45%). Better than bare hands (20%).
        items.createCarriedItem(chronicle, "bone_fish_hook", "Bone fish hook", now, "TEST_SEED");
        int withHook = landed(chronicle, chunk, now, actionIds);

        // A woven fish trap — method TRAP (75%), checked before the hook, so it is what is used now.
        items.createCarriedItem(chronicle, "fish_trap", "Fish trap", now, "TEST_SEED");
        int withTrap = landed(chronicle, chunk, now, actionIds);

        assertTrue(withHook > bareHanded,
                () -> "a bone fish hook must land more than bare hands (hook=" + withHook + ", bare=" + bareHanded + ") — the hook must be USED (#36/#43)");
        assertTrue(withTrap > bareHanded,
                () -> "a woven fish trap must land more than bare hands (trap=" + withTrap + ", bare=" + bareHanded + ") — the trap must be USED (#36/#43)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
