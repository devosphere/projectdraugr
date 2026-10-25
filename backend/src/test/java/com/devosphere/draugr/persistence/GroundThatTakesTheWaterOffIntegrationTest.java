package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drainage (#108, V435): a keeper who cuts a ditch stops the wet ground rotting their animals' feet.
 *
 * <p>Two halves of the same defect met here. The hoof rule has charged standing wet since V414 — "standing wet
 * softens the horn and rots it" — reading WETLAND and RIVER_BANK by biome, with no remedy but to leave. And
 * DRAINAGE_DITCH has been in the catalogue, buildable from a verified assembly and decaying like any other
 * field structure, read by <b>nothing</b>: a declared capability against a declared cost, each unaware of the
 * other.
 *
 * <p>Proved on wet ground, where the rot is real, from three sides: no ditch and the feet go off faster than
 * dry ground would take them; a sound ditch and the extra wear is gone; the same ditch coming apart and it is
 * back, because a silted ditch drains nothing. Skips without Docker.
 */
@SpringBootTest
class GroundThatTakesTheWaterOffIntegrationTest {

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

    private int hoof(UUID bond) {
        return jdbc.queryForObject("SELECT hoof_condition FROM wildlife_bond WHERE id=?", Integer.class, bond);
    }

    /** One turn of the hoof rule from a known foot, and what it took off. */
    private int wearOverOneTurn(UUID bond, Instant now) {
        jdbc.update("UPDATE wildlife_bond SET hoof_condition=80 WHERE id=?", bond);
        items.advanceHoofCondition(now);
        return 80 - hoof(bond);
    }

    @Test
    void aDitchCutToDrainTheGroundSavesTheirFeet() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        Instant now = Instant.parse("2026-05-02T10:00:00Z");
        Timestamp ts = Timestamp.from(now);

        // Wet ground with nothing standing on it. RIVER_BANK is generated (#156) and WETLAND is a map biome, so
        // either will do; the hoof rule reads them alike.
        UUID wet = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome IN ('WETLAND','RIVER_BANK')"
          + " AND NOT EXISTS (SELECT 1 FROM world_object w JOIN construction_project cp ON cp.object_id=w.id"
          + "                  WHERE w.current_location_id=c.id AND w.lifecycle_state='ACTIVE')"
          + " ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(wet, "the world must have wet ground with nothing built on it");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", wet, chronicle);

        // A beast with feet that need paring — the declared flag the rule itself reads.
        String hoofed = jdbc.queryForObject(
            "SELECT ws.species_key FROM wildlife_species ws WHERE ws.feet_need_paring ORDER BY ws.species_key LIMIT 1", String.class);
        assertNotNull(hoofed, "some species must have feet that need paring");
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, wet);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Wet pasture',?)", site, wet);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Wet pasture',30)",
            site, worldId, wet);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "SELECT ?,?,ws.species_key,ws.ecological_role,ws.activity_cycle,1,4,'FORAGING',? FROM wildlife_species ws WHERE ws.species_key=?",
            pop, site, ts, hoofed);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
            "draft_hunger,draft_thirst,draft_fatigue,sickness,hoof_condition) VALUES (?,?,?,'TAMED',95,12,?,0,0,0,0,80)", bond, chronicle, pop, ts);

        // 1. UNDRAINED: the wet takes more off the foot than the horn's own growing would.
        int onWetGround = wearOverOneTurn(bond, now);
        assertTrue(onWetGround > 1,
            () -> "standing wet must cost the foot more than growing out alone (took " + onWetGround + ")");

        // 2. A DITCH, sound: the wet stops telling, and only the growing is left.
        UUID ditch = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Drainage ditch','ACTIVE',?)", ditch, wet);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'DRAINAGE_DITCH','COMPLETED',100,?,100,?)", ditch, ts, ts);
        int drained = wearOverOneTurn(bond, now);
        assertTrue(drained < onWetGround,
            () -> "a ditch must take the standing wet off their feet (" + onWetGround + " undrained against " + drained + " drained)");
        assertTrue(drained >= 1, () -> "the horn still grows out on dry ground, so something must come off: " + drained);

        // 3. THE SAME DITCH COMING APART drains nothing, which is the condition rule the rest of #108 keeps.
        jdbc.update("UPDATE construction_project SET integrity_percent=? WHERE object_id=?",
            ConstructionService.COMING_APART_BELOW - 1, ditch);
        int silted = wearOverOneTurn(bond, now);
        assertEquals(onWetGround, silted,
            "a ditch coming apart drains nothing, so the ground is as wet as it was before it was cut");

        // 4. AND THE KEEPER IS TOLD, both ways, because a cost they cannot see is a cost they cannot answer.
        String neglected = items.stockWelfare(chronicle, wet, "check on the stock")[1];
        assertTrue(neglected.contains("ditch cut to take the water off"),
            () -> "the stock-check must name the wet ground and what answers it: " + neglected);
        jdbc.update("UPDATE construction_project SET integrity_percent=100 WHERE object_id=?", ditch);
        String kept = items.stockWelfare(chronicle, wet, "check on the stock")[1];
        assertTrue(kept.contains("keeping it off"),
            () -> "and say the ditch is doing its work once it is sound: " + kept);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
