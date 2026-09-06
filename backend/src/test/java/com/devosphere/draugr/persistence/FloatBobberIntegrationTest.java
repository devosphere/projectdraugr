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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A float on the line must fish better than none (#75).
 *
 * <p>The catalogue calls the {@code float_bobber} "a light float bound for a fishing line", it is craftable and
 * equippable at the waist, and no code read it — so a Chronicle could bind one to their line and fish exactly as
 * they had before. It is the other half of the rig the lead sinker is the first half of: shot below to carry the
 * bait down, float above to hold it off the bottom at a set depth and show the take.
 *
 * <p>Proven the way the sinker is proven: the same fixed battery of casts with the fish stock reset before each
 * run, so the only thing that varies is the tackle. Skips without Docker.
 */
@SpringBootTest
class FloatBobberIntegrationTest {

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

    /** The same battery of casts, with the finite fish stock reset first so tackle is the only variable. */
    private int catches(UUID chronicle, UUID chunk, Instant now, List<UUID> actionIds) {
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", chunk);
        int taken = 0;
        for (UUID action : actionIds) {
            if ("SUCCEEDED".equals(wildlife.fish(chronicle, chunk, action, now, "fish with a line and hook").outcome())) taken++;
        }
        return taken;
    }

    @Test
    void aFloatOnTheLineLandsMoreFishThanABareLine() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have wetland water to fish");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Strip anything that would lift the same line by another route, so the float is the only thing that
        // changes between the two runs.
        jdbc.update("UPDATE world_object SET current_owner_id=NULL WHERE current_owner_id=? AND id IN (" +
                "SELECT object_id FROM item_instance WHERE item_key IN " +
                "('float_bobber','lead_sinker','stone_fishing_weight','bronze_fish_hook','iron_fish_hook','earthworm'))", chronicle);

        // A plain bone hook: a line, and nothing else on it.
        items.createCarriedItem(chronicle, "bone_fish_hook", "Bone fish hook", now, "TEST_SEED");

        java.util.Random rnd = new java.util.Random(23);
        List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 140; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        int bareLine = catches(chronicle, chunk, now, actionIds);
        assertTrue(bareLine > 0 && bareLine < actionIds.size(),
            () -> "a bare line must land some casts and lose some, or the float's margin cannot show at all "
                + "(bareLine=" + bareLine + " of " + actionIds.size() + ")");

        // Bind a float to it.
        items.createCarriedItem(chronicle, "float_bobber", "Float bobber", now, "TEST_SEED");
        int floated = catches(chronicle, chunk, now, actionIds);

        assertTrue(floated > bareLine,
            () -> "a float must land fish a bare line loses, or there is no reason to bind one on "
                + "(floated=" + floated + ", bareLine=" + bareLine + ")");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
