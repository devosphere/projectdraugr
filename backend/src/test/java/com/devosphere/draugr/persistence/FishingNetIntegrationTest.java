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
 * Fishing-net usefulness regression (M1 #36/#43, EPIC #123). A woven fishing net is craftable, but until now
 * nothing read it — fish() knew trap, spear, line, and bare hands, but not a net — so a net caught no better
 * than empty hands, the whole reason to weave one gone. fish() now reaches for a carried net (a cast/gill net
 * works well; a landing net less so) when no other method is named.
 *
 * <p>Proven over a fixed set of casts at a wetland teeming with fish: a Chronicle with a net lands far more
 * than the same Chronicle bare-handed. The net raises the chance from 20% to 72%, so the margin is wide and
 * the assertion robust. Skips gracefully without Docker.
 */
@SpringBootTest
class FishingNetIntegrationTest {

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
        int caught = 0;
        for (UUID action : actionIds)
            if ("SUCCEEDED".equals(wildlife.fish(chronicle, chunk, action, now, "I fish in the shallows here.").outcome())) caught++;
        return caught;
    }

    @Test
    void aWovenNetLandsFarMoreFishThanBareHands() {
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
        // Ample capacity so a run of caught fish never overloads the Chronicle mid-test.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        java.util.Random rnd = new java.util.Random(11);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // Bare-handed baseline over the fixed casts.
        int bareHanded = landed(chronicle, chunk, now, actionIds);
        assertTrue(bareHanded >= 0, "baseline established");

        // The same casts with a woven net to hand — no other method named, so the net is what is used.
        items.createCarriedItem(chronicle, "fishing_net", "Fishing net", now, "TEST_SEED");
        int withNet = landed(chronicle, chunk, now, actionIds);

        assertTrue(withNet > bareHanded,
                () -> "a woven fishing net must land more than bare hands over the same casts (net=" + withNet + ", bare=" + bareHanded + ") — the net must actually be USED (#36/#43)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
