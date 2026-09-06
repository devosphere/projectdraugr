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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reachability regression for the perimeter trip-line alarm (M1 #126/#127, EPIC #123).
 *
 * <p>WildlifeEncounterService.passiveEncounter already read a completed CAMP_ALARM construction to rob a
 * stalking predator of its surprise, but nothing could build one — the kind was unregistered and no action
 * produced it, so the effect was dead code. This proves the acquisition path end to end: a Chronicle with a
 * line and something that clatters rigs a trip-line through the real action pipeline, and the persistent
 * CAMP_ALARM it leaves is exactly what the ambush check looks for — so the previously-inert defence is live.
 *
 * <p>Skips gracefully where no Docker engine is reachable; runs for real in CI.
 */
@SpringBootTest
class CampAlarmIntegrationTest {

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
    @Autowired com.devosphere.draugr.construction.ConstructionService construction;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** The exact predicate passiveEncounter uses to decide the Chronicle is alarmed at this chunk. */
    private boolean chunkIsAlarmed(UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='CAMP_ALARM' AND cp.state='COMPLETED' " +
                "AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, chunk));
    }

    @Test
    void riggingATripLineLeavesAPersistentAlarmThatThePredatorCheckReads() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();

        // No alarm yet — the predator check must read the ground as unalarmed.
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_project WHERE project_kind='CAMP_ALARM'", Integer.class),
                "no alarm should exist before one is built");
        assertTrue(!chunkIsAlarmed(chunk), "an un-rigged camp must read as unalarmed");

        // A line and something that clatters — the physical inputs the forest floor provides — never an
        // injected capability.
        items.createCarriedItem(chronicle, "fiber_cordage", "Processed fiber cordage", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "animal_bone", "Animal bone", now, "TEST_SEED");

        ChronicleActionService.ActionResult rigged = actions.resolve("I rig a trip-line alarm around the camp.");
        assertEquals("SUCCEEDED", rigged.outcome(), () -> "rigging a trip-line with a line and clatter to hand must succeed: " + rigged.perception());

        // A persistent CAMP_ALARM construction now stands at the chunk, exactly as the ambush check expects.
        UUID alarm = jdbc.queryForObject(
                "SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE cp.project_kind='CAMP_ALARM' AND cp.state='COMPLETED' AND w.current_location_id=? AND w.lifecycle_state='ACTIVE'",
                UUID.class, chunk);
        assertNotNull(alarm, "a completed CAMP_ALARM construction must persist at the camp after rigging");
        assertTrue(chunkIsAlarmed(chunk), "the predator ambush check must now read the camp as alarmed (#126/#127)");

        // The line and the clatter were spent — real materials consumed, not conjured.
        assertEquals(0, (int) jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key IN ('fiber_cordage','animal_bone')",
                Integer.class, chronicle), "rigging the alarm consumes the line and the clatter");

        // Re-rigging refreshes the same alarm rather than stacking a second, and needs only fresh clatter.
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        ChronicleActionService.ActionResult restrung = actions.resolve("I re-string the trip-line alarm.");
        assertEquals("SUCCEEDED", restrung.outcome(), () -> "re-stringing an existing alarm must succeed: " + restrung.perception());
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_project WHERE project_kind='CAMP_ALARM'", Integer.class),
                "re-stringing refreshes the one alarm, it does not build a second");

        PersistentStateAuditor.AuditReport report = auditor.inspect();
        assertTrue(report.consistent(), () -> "the world must stay Auditor-consistent after building the alarm: " + report.violations());
    }

    /**
     * The purpose-made clatter must be accepted. The catalogue calls the {@code warning_rattle} "a rattle strung
     * to warn of approach" and it was missing from the clatter list entirely, so a Chronicle could cut the one
     * thing made for this line and still be told they had nothing to hang on it.
     */
    @Test
    void aWarningRattleIsClatterTheLineAccepts() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = ticks.current().simulatedAt();

        // Clear this ground and strip every improvised clatter, so the rattle is the only thing that could work.
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL WHERE current_owner_id=? AND id IN (" +
                "SELECT object_id FROM item_instance WHERE item_key IN ('animal_bone','deer_antler','dry_branch','warning_rattle'))", chronicle);

        // A line, but nothing to hang: the refusal must be grounded, not a success.
        items.createCarriedItem(chronicle, "fiber_cordage", "Processed fiber cordage", now, "TEST_SEED");
        String[] nothingToHang = construction.buildCampAlarm(chronicle, chunk, now);
        assertEquals("FAILED", nothingToHang[0], () -> "with no clatter at all the line cannot sound: " + nothingToHang[1]);

        // The rattle alone must be enough.
        items.createCarriedItem(chronicle, "warning_rattle", "Warning rattle", now, "TEST_SEED");
        String[] rigged = construction.buildCampAlarm(chronicle, chunk, now);
        assertEquals("SUCCEEDED", rigged[0], () -> "a rattle made for this line must be clatter the line accepts: " + rigged[1]);
        assertTrue(chunkIsAlarmed(chunk), "and the ambush check must read the camp as alarmed");

        // It is hung on the line, so it is spent like any other clatter.
        assertEquals(0, (int) jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='warning_rattle'",
                Integer.class, chronicle), "the rattle is hung on the line, not kept in the pouch");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
