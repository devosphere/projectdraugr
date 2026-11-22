package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A full load is not an empty wood (#896). Every gather takes only what the load has room for, and a load at its
 * bulk came back as the same zero as bare ground, so a player standing in a wood with ample weight to spare was
 * told they "search the leaf litter for dry wood, then leave with empty hands". Here the Chronicle's own bulk
 * capacity is set to exactly what it already carries: the gather must say it is the LOAD, name bulk (not weight),
 * and point at the remedy. The control lifts the cap and the same gather in the same wood succeeds.
 * Skips without Docker.
 */
@SpringBootTest
class AFullLoadIsNotAnEmptyWoodIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Awaken a fed, rested Chronicle in temperate forest at June midday, on a tile with dry wood to take. */
    private UUID awakenInTheWood() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        assertNotNull(chunk, "the approved world must contain a TEMPERATE_FOREST chunk");
        Instant noon = Instant.parse("2026-06-18T12:00:00Z");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        jdbc.update("UPDATE simulation_clock SET simulated_at=?", Timestamp.from(noon));
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, "
                  + "sleep_debt_hours=0, illness_severity=0, injury_severity=0, blood_loss_ml=0, core_temperature_c=37.0 WHERE chronicle_id=?",
            Timestamp.from(noon), summary.id());
        // The wood is stocked by this test, not by whatever other tests left of it: the shared world is depleted by them.
        jdbc.update("INSERT INTO world_chunk_resource (chunk_id,resource_key,available_units,capacity_units,last_regenerated_at) "
                  + "VALUES (?,'dry_branch',20,20,?) ON CONFLICT (chunk_id,resource_key) DO UPDATE SET available_units=20, "
                  + "capacity_units=GREATEST(world_chunk_resource.capacity_units,20), last_regenerated_at=EXCLUDED.last_regenerated_at",
            chunk, Timestamp.from(noon));
        return summary.id();
    }

    /** The bulk the Chronicle already carries, read the way the load is computed: everything owned, and what that holds. */
    private int carriedBulkMl(UUID chronicle) {
        Integer ml = jdbc.queryForObject(
            "WITH RECURSIVE carried(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' "
          + "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN carried c ON ic.container_id=c.id) "
          + "SELECT COALESCE(SUM(d.unit_volume_ml),0) FROM carried JOIN item_instance i ON i.object_id=carried.id "
          + "JOIN item_definition d ON d.item_key=i.item_key", Integer.class, chronicle);
        return ml == null ? 0 : ml;
    }

    private int carriedBranches(UUID chronicle) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id "
            + "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='dry_branch'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aGatherWithNoBulkLeftSaysItIsTheLoad() {
        UUID chronicle = awakenInTheWood();
        // Bulk one millilitre short of a branch (direct_bulk_ml must stay > 0, so not "exactly full", which is 0 for an
        // empty-handed body); weight left as it was, which is ample for a branch.
        Integer branchMl = jdbc.queryForObject("SELECT unit_volume_ml FROM item_definition WHERE item_key='dry_branch'", Integer.class);
        assertNotNull(branchMl);
        jdbc.update("UPDATE chronicle_carry_capacity SET direct_bulk_ml=? WHERE chronicle_id=?", carriedBulkMl(chronicle) + branchMl - 1, chronicle);
        // The shared world may hand back a Chronicle that already gathered here (the control test, run first), so
        // "nothing was taken" is a delta against what it held before, not an absolute zero.
        int branchesBefore = carriedBranches(chronicle);

        ChronicleActionService.ActionResult gathered = actions.resolve("gather dry branches");

        assertEquals("FAILED", gathered.outcome(), gathered.perception());
        String said = gathered.perception().toLowerCase(Locale.ROOT);
        assertFalse(said.contains("leaf litter"), "a full load must not be told as an empty wood: " + gathered.perception());
        assertTrue(said.contains("your load is at its bulk"), "the limit must be named as bulk: " + gathered.perception());
        assertTrue(said.contains("though not its weight"), "and as not weight, which had room: " + gathered.perception());
        assertTrue(said.contains("set something down"), "the remedy must be said: " + gathered.perception());
        assertEquals(branchesBefore, carriedBranches(chronicle), "nothing was taken");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void theSameGatherWithRoomTakesTheWood() {
        UUID chronicle = awakenInTheWood();
        jdbc.update("UPDATE chronicle_carry_capacity SET direct_bulk_ml=? WHERE chronicle_id=?", carriedBulkMl(chronicle) + 50_000, chronicle);
        int branchesBefore = carriedBranches(chronicle);

        ChronicleActionService.ActionResult gathered = actions.resolve("gather dry branches");

        assertEquals("SUCCEEDED", gathered.outcome(), gathered.perception());
        assertFalse(gathered.perception().toLowerCase(Locale.ROOT).contains("your load is"),
            "a gather with room is told as the gather: " + gathered.perception());
        assertTrue(carriedBranches(chronicle) > branchesBefore, "the wood is taken");
    }
}
