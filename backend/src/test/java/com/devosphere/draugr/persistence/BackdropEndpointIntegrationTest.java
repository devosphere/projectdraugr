package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.BackdropResolver;
import com.devosphere.draugr.world.VisualContextController;
import com.devosphere.draugr.world.VisualContextService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A place actually gets a backdrop out of the resolver (#225/#234).
 *
 * <p>{@link BackdropResolver} was written as a pure function and then called by nothing but its own unit test.
 * It decided the backdrop for no place in the world. A resolver nothing calls resolves nothing, so this covers
 * the boundary where the decision is really made — the same context the visual-context payload reports, run
 * through the same precedence, reached the way a caller reaches it.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class BackdropEndpointIntegrationTest {

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
    @Autowired VisualContextController controller;
    @Autowired VisualContextService visual;
    @Autowired SimulationTickService ticks;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID plainGround() {
        return jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    }

    private UUID chronicleOn(UUID chunk) {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        return summary.id();
    }

    /** Open grassland with nothing on it still calls for a backdrop, and says why. */
    @Test
    void openGroundResolvesToItsBiome() {
        world();
        UUID ground = plainGround();
        assertNotNull(ground, "the world must have open ground with nothing on it");
        chronicleOn(ground);

        VisualContextController.Backdrop chosen = controller.backdrop();
        assertNotNull(chosen, "a Chronicle standing somewhere must get a backdrop for it");
        assertEquals(VisualContextService.VERSION, chosen.version(), "the backdrop must declare the same contract version");
        assertEquals("biome.grassland", chosen.key(), "open grassland calls for the grassland backdrop");
        assertEquals("BIOME", chosen.reason(), "and must say, safely, why");
        assertNotNull(chosen.fingerprint(), "the backdrop must carry the context fingerprint a caller caches on");
    }

    /**
     * The fingerprint is the caching contract, and it must track the place rather than the key: raising something
     * here changes what is seen, so the fingerprint must move even when the chosen key is reached the same way.
     */
    @Test
    void buildingHereMovesTheFingerprint() {
        world();
        UUID ground = plainGround();
        assertNotNull(ground);
        UUID chronicle = chronicleOn(ground);

        VisualContextController.Backdrop before = controller.backdrop();
        assertNotNull(before.fingerprint());

        Instant now = ticks.current().simulatedAt();
        UUID built = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE','Lean-to',?)", built, ground);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                    "VALUES (?,'LEAN_TO','COMPLETED',100,?,100)", built, Timestamp.from(now));

        VisualContextController.Backdrop after = controller.backdrop();
        assertNotEquals(before.fingerprint(), after.fingerprint(),
            "a finished shelter changes what this place looks like, so the caching fingerprint must move");
        assertEquals("built.lean-to", after.key(), "evidence of habitation outranks the bare ground");
        assertEquals("BUILT_HERE", after.reason());

        // And it stops qualifying the moment it is gone — the resolver has no memory to go stale.
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_cause='TEST_TEARDOWN', " +
                    "current_location_id=NULL, current_owner_id=NULL WHERE id=?", built);
        assertEquals(before.key(), controller.backdrop().key(),
            "a shelter pulled down stops deciding the backdrop immediately");
    }

    /** Whatever the ground, the endpoint always names a key — reaching the fallback is an answer, not a failure. */
    @Test
    void everyGroundInTheWorldGetsAKey() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        for (UUID chunk : jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 40", UUID.class)) {
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
            VisualContextController.Backdrop chosen = controller.backdrop();
            assertNotNull(chosen.key(), () -> "no ground may be left without a backdrop: " + chunk);
            assertFalse(chosen.key().isBlank(), () -> "no ground may be left without a backdrop: " + chunk);
            assertNotNull(chosen.reason(), () -> "and every choice must be able to say why: " + chunk);
            assertTrue(chosen.key().equals(BackdropResolver.FALLBACK_KEY) || chosen.key().contains("."),
                () -> "a key must be a namespaced slug: " + chosen.key());
        }
    }

    /**
     * The endpoint must agree with the resolver, always. If these could differ, the controller would have become a
     * second place where backdrop precedence is decided, which is the thing #234 exists to prevent.
     */
    @Test
    void theEndpointDecidesNothingOfItsOwn() {
        world();
        UUID ground = plainGround();
        assertNotNull(ground);
        chronicleOn(ground);

        VisualContextService.VisualContext here = visual.active(ticks.current().simulatedAt());
        BackdropResolver.Choice direct = BackdropResolver.resolve(here);
        VisualContextController.Backdrop served = controller.backdrop();
        assertEquals(direct.key(), served.key(), "the endpoint must serve the resolver's choice, not its own");
        assertEquals(direct.reason(), served.reason());
        assertEquals(direct.candidates(), served.candidates(),
            "the eligibility chain must be served whole — a caller cannot degrade through a chain it never sees");
        assertEquals(here.fingerprint(), served.fingerprint());
    }

    /**
     * The chain has to survive the wire, or #236 decided nothing. A caller walks it and takes the first it has an
     * image for, so it must arrive non-empty, ordered, and ending in the key the tiers guaranteed.
     */
    @Test
    void theEligibilityChainReachesTheCaller() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        for (UUID chunk : jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 25", UUID.class)) {
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
            VisualContextController.Backdrop served = controller.backdrop();
            assertNotNull(served.candidates(), () -> "no ground may be served without a chain: " + chunk);
            assertFalse(served.candidates().isEmpty(), () -> "an empty chain leaves a caller nothing to try: " + chunk);
            assertEquals(served.key(), served.candidates().get(served.candidates().size() - 1),
                () -> "the chain must end in the key the tiers guaranteed: " + served.candidates());
            assertTrue(served.candidates().size() <= 4, () -> "the chain must stay bounded: " + served.candidates());
            assertEquals(served.candidates().stream().distinct().toList(), served.candidates(),
                () -> "no candidate may be offered twice: " + served.candidates());
        }
    }
}
