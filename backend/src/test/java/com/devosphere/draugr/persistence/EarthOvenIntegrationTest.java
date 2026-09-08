package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An earth oven goes on cooking after the fire is out (#77).
 *
 * <p>V289 made "what can hold a fire" a fact of the catalogue, so a new fireplace is now a data row rather than
 * five more literals in {@code FireService}. But a ring hearth, a cooking pit and a stone fireplace would all
 * behave identically to the stone fire pit already in the world — three names for one behaviour, which is the
 * decoration this catalogue is meant not to carry. So the one added here is the one that is genuinely different.
 *
 * <p>An earth oven is a pit lined with close-set stone, filled with fire until the rock is soaked through, then
 * raked out and covered. The stones hold that heat for hours and the food cooks in it with no flame at all. That
 * is the whole technology, and it is the only reason to dig one rather than lay a fire on the ground.
 *
 * <p>Asserted in three directions, because a rule that only ever permits is not a rule: the oven works cold, an
 * ordinary hearth does not, and the oven itself stops working once the stone has given the heat up. Skips
 * without Docker.
 */
@SpringBootTest
class EarthOvenIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** A completed structure of this kind, with a fire in it that went out at the given moment. */
    private UUID coldFireplace(String projectKind, UUID location, Instant wentOut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)",
            id, projectKind, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) " +
            "VALUES (?,?,'COMPLETED',100,?)", id, projectKind, Timestamp.from(wentOut));
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,false,0,?)",
            id, Timestamp.from(wentOut));
        return id;
    }

    private UUID groundAt(int fromEnd) {
        return jdbc.query("SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x OFFSET ? LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null, fromEnd);
    }

    /** Reflection-free reading of the private gate: ask a fire-needing process whether it can run here. */
    private boolean heatHere(UUID location, Instant at) throws Exception {
        var m = PhysicalItemService.class.getDeclaredMethod("heatToWorkWith", UUID.class, Instant.class);
        m.setAccessible(true);
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(items);
        return (boolean) m.invoke(target, location, at);
    }

    /** The catalogue must declare the oven, and declare it as the only thing holding heat. */
    @Test
    void theOvenIsDeclaredAndItIsTheOnlyOneThatHoldsHeat() {
        Integer ovens = jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE project_kind='EARTH_OVEN' AND holds_fire AND retained_heat_minutes > 0",
            Integer.class);
        assertEquals(1, ovens, "an earth oven that holds no fire, or no heat, is a hole in the ground");

        Integer holders = jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE retained_heat_minutes > 0", Integer.class);
        assertEquals(1, holders, "retained heat is the oven's alone until something else earns it");
    }

    /** It must be buildable: an assembly, ordered stages, and inputs that are real items. */
    @Test
    void theOvenCanActuallyBeBuilt() {
        Integer stages = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_stage WHERE assembly_key='earth_oven'", Integer.class);
        assertTrue(stages != null && stages >= 2, "digging a pit is not one motion; it wants stages (" + stages + ")");

        Integer phantom = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_stage_requirement r WHERE r.stage_key LIKE 'oven_%' " +
            "AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=r.item_key)", Integer.class);
        assertEquals(0, phantom, "a stage wanting an item that does not exist can never be completed");

        String kind = jdbc.queryForObject(
            "SELECT construction_kind FROM assembly_definition WHERE assembly_key='earth_oven'", String.class);
        assertEquals("EARTH_OVEN", kind, "the assembly must raise the structure the registry declares");
    }

    /** The whole point: heat outlasting the flame. */
    @Test
    void theStoneKeepsTheHeatAfterTheFireIsOut() throws Exception {
        world();
        Instant now = Instant.now();

        UUID ovenGround = groundAt(0);
        assertNotNull(ovenGround, "the world must have open ground to dig in");
        coldFireplace("EARTH_OVEN", ovenGround, now.minus(Duration.ofHours(2)));
        assertTrue(heatHere(ovenGround, now),
            "two hours after the fire died, the stone of an earth oven is still working — that is the only "
                + "reason to dig one instead of laying a fire on the ground");

        // Long enough and the stone gives it up. Four hours is the declared window.
        assertTrue(!heatHere(ovenGround, now.plus(Duration.ofHours(5))),
            "the heat must run out; an oven that stays hot for ever is a stove, not a pit of rock");
    }

    /** And an ordinary hearth is cold the moment it goes out. */
    @Test
    void anOrdinaryHearthIsColdWhenItIsOut() throws Exception {
        world();
        Instant now = Instant.now();

        UUID hearthGround = groundAt(1);
        assertNotNull(hearthGround, "the world must have a second piece of ground");
        coldFireplace("STONE_FIRE_PIT", hearthGround, now.minus(Duration.ofMinutes(10)));
        assertTrue(!heatHere(hearthGround, now),
            "a fire pit that has gone out is cold stone — if this passes, retained heat leaked to everything "
                + "and the oven is no longer distinguishable");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
