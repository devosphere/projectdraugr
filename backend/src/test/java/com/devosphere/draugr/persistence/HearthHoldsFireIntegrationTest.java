package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.FireService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hearth you built must take a fire (#71/#77).
 *
 * <p>{@code FireService} named {@code STONE_FIRE_PIT} literally in five places — lighting, banking, feeding,
 * finding the fire that burns here, and counting active fires — and the Auditor and the charcoal rule named it in
 * one more each. So the stone fire pit was the only thing in the world that could hold a flame.
 *
 * <p>Meanwhile the catalogue lets a Chronicle build a {@code CLAY_LINED_HEARTH} in three staged pieces of work:
 * ring the stones, line them with clay, let the lining cure. At the end of that they had a hearth they could not
 * light. Not one that burned badly or burned out fast — one that could not take a fire at all, because seven
 * queries were looking for a different word.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class HearthHoldsFireIntegrationTest {

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
    @Autowired FireService fires;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID chronicleHere(UUID location) {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID id = summary.id();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", location, id);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", id);
        return id;
    }

    private UUID someGround() {
        return jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
    }

    /** Stand a completed structure of the given kind on this ground, as a finished build would. */
    private UUID raise(String projectKind, String displayName, UUID location, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)",
            id, displayName, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) " +
            "VALUES (?,?,'COMPLETED',100,?)", id, projectKind, Timestamp.from(now));
        return id;
    }

    /** The catalogue must declare more than one fireplace, or the rule below is about one row. */
    @Test
    void theWorldKnowsMoreThanOneKindOfFireplace() {
        List<String> holders = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE holds_fire ORDER BY project_kind", String.class);
        assertTrue(holders.contains("STONE_FIRE_PIT"), "the stone fire pit must still hold a fire: " + holders);
        assertTrue(holders.contains("CLAY_LINED_HEARTH"),
            "a clay-lined hearth is three stages of work ending in something you could not light: " + holders);

        List<String> burnable = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE holds_fire AND flammable", String.class);
        assertTrue(burnable.isEmpty(),
            () -> "a hearth is what contains a fire, not what feeds it: " + burnable);
    }

    /** The defect itself: build the hearth, light it, feed it. */
    @Test
    void aClayHearthTakesAndKeepsAFire() {
        world();
        Instant now = Instant.now();
        UUID ground = someGround();
        assertNotNull(ground, "the world must have open ground to build on");

        UUID hearth = raise("CLAY_LINED_HEARTH", "Clay-lined hearth", ground, now);
        UUID chronicle = chronicleHere(ground);
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "FORAGED_FROM_GROUND");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "FORAGED_FROM_GROUND");
        items.createCarriedItem(chronicle, "tinder_nest", "Tinder nest", now, "CRAFTED");

        FireService.LightResult lit = fires.light(chronicle, ground, now, true);
        assertNotEquals(FireService.LightResult.NO_PIT, lit,
            "a completed clay hearth IS a fireplace — reporting no pit is the defect this closes");

        Assumptions.assumeTrue(lit == FireService.LightResult.LIT,
            "this attempt failed for a fuel or kit reason, not for want of a hearth (" + lit + ")");

        Integer burning = jdbc.queryForObject(
            "SELECT COUNT(*) FROM fire_state WHERE construction_id=? AND active=true", Integer.class, hearth);
        assertEquals(1, burning, "the fire must be burning in the hearth that was built");

        int before = jdbc.queryForObject("SELECT fuel_minutes FROM fire_state WHERE construction_id=?", Integer.class, hearth);
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "FORAGED_FROM_GROUND");
        assertTrue(fires.feed(chronicle, ground, now), "a hearth that can be lit must also be feedable");
        int after = jdbc.queryForObject("SELECT fuel_minutes FROM fire_state WHERE construction_id=?", Integer.class, hearth);
        assertTrue(after > before, "feeding must add fuel (" + before + " -> " + after + ")");

        assertTrue(auditor.inspect().consistent(),
            () -> "a fire burning in a hearth is not a displaced fire — the Auditor named the stone pit literally "
                + "too, and would have called this world broken: " + auditor.inspect().violations());
    }

    /** And a shelter is not a fireplace. The rule must still refuse what is not declared. */
    @Test
    void aShelterIsNotAFireplace() {
        world();
        Instant now = Instant.now();
        UUID ground = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        assertNotNull(ground);
        jdbc.update("DELETE FROM fire_state WHERE construction_id IN " +
            "(SELECT object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=?)", ground);

        raise("LEAN_TO", "Lean-to", ground, now);
        UUID chronicle = chronicleHere(ground);
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "FORAGED_FROM_GROUND");
        items.createCarriedItem(chronicle, "tinder_nest", "Tinder nest", now, "CRAFTED");

        assertEquals(FireService.LightResult.NO_PIT, fires.light(chronicle, ground, now, true),
            "a lean-to is somewhere to sleep, not somewhere to burn — the registry must still refuse it");
    }
}
