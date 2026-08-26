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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fire-using material process is a smoky working (EPIC #215, story #219 — hazard footprints). A smelt, a kiln
 * firing, a forge, a charcoal char all burn a fire, and that smoke marks the ground and drifts onto the neighbours
 * — the same footprint the charcoal code-intent already lays, now extended to the whole fire economy. Cold bench
 * work (grinding, knapping) leaves no smoke.
 *
 * <p>Proven: {@code actionIsFireProcess} tells a fire process from a cold one; and running a charcoal char through
 * the full action pipeline both produces charcoal AND raises a SMOKE disturbance on the ground that drifts a lesser
 * SMOKE_DRIFT onto the adjacent chunks. Skips gracefully without Docker.
 */
@SpringBootTest
class FireProcessSmokeFootprintIntegrationTest {

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

    private int count(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey, chronicle);
        return n == null ? 0 : n;
    }

    private int level(UUID chunk) {
        Integer v = jdbc.query("SELECT disturbance_level FROM chunk_disturbance WHERE chunk_id=?", rs -> rs.next() ? rs.getInt(1) : null, chunk);
        return v == null ? 0 : v;
    }

    @Test
    void aFireProcessLaysASmokeFootprintButAColdProcessDoesNot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        // The gate itself: a fire process reads as smoky; a cold grind does not.
        assertTrue(items.actionIsFireProcess("char the wood into charcoal"), "a charcoal char burns a fire — a smoky working");
        assertFalse(items.actionIsFireProcess("grind the grain into flour"), "grinding is cold bench work — no smoke");

        // An interior chunk (so the plume has neighbours to drift onto), and its world.
        UUID chunk = jdbc.queryForObject(
            "SELECT here.id FROM world_chunk here WHERE EXISTS (SELECT 1 FROM world_chunk n " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1) LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // A lit fire to fire the clamp from, and the wood to char.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,960,?)", pit, ts);
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");

        // Clean slate for the disturbance measurement across this world.
        jdbc.update("DELETE FROM chunk_disturbance WHERE chunk_id IN (SELECT id FROM world_chunk WHERE world_id=?)", world);

        // Char the clamp through the FULL action pipeline (not runProcess directly) — the smoke footprint is laid there.
        ChronicleActionService.ActionResult r = actions.resolve("char the wood into charcoal");
        assertNotNull(r, "the action must resolve");
        assertTrue(count(chronicle, "charcoal") >= 6, "the charcoal char must have run (a batch of charcoal)");

        // The smoky working marked the ground it stood on, and the plume drifted onto the neighbours.
        assertTrue(level(chunk) >= 15, () -> "a fire process must lay a SMOKE disturbance on its ground (#219), got " + level(chunk));
        Integer smokeHere = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chunk_disturbance_event WHERE chunk_id=? AND source_kind='SMOKE'", Integer.class, chunk);
        assertTrue(smokeHere != null && smokeHere >= 1, "the smoke must be kept as SMOKE history at the source");

        List<UUID> neighbours = jdbc.queryForList(
            "SELECT n.id FROM world_chunk n JOIN world_chunk here ON here.id=? " +
            "WHERE n.world_id=here.world_id AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1", UUID.class, chunk);
        boolean anyDrift = neighbours.stream().anyMatch(n -> level(n) > 0);
        assertTrue(anyDrift, "the plume must drift a SMOKE_DRIFT disturbance onto the adjacent ground (#219)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
