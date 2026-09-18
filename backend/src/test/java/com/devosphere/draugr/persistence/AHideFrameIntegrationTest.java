package com.devosphere.draugr.persistence;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A frame to stretch a hide on (#77, V340).
 *
 * <p>Fleshing and dehairing are the two most skilled steps between a carcass and leather, and every people who has
 * worked hides has done them on something — a beam, or a frame the hide is laced into under tension — so the scraper
 * meets a taut surface instead of a sliding, bunching skin. Neither process declared a station, so a Chronicle
 * worked a hide on bare ground exactly as well as anyone ever could.
 *
 * <p>The station rule already in the process engine lifts the work one workmanship grade, capped by the grade of
 * what goes in. So the test hands the Chronicle a FINE hide — the only way the lift can show — and a careful hand
 * that on its own would produce SOUND work. The grade is deterministic, so this is exact rather than statistical.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AHideFrameIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aHideFleshedOnAFrameComesOutTruerAndOneFleshedOnTheGroundStillComesOut() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        items.createCarriedItem(chronicle, "bone_knife", "Bone knife", now, "TEST_FIXTURE");

        // On the bare ground: a fine hide, careful work, and the result is only as good as the hand alone.
        String onTheGround = flesh(chronicle, chunk, now);
        assertEquals("SOUND", onTheGround,
            "without a frame a careful hand gives sound work — a station eases, it never gates, and the hide is still fleshed");

        UUID frame = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) " +
            "VALUES (?,'CONSTRUCTION','Hide frame','ACTIVE',?)", frame, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'HIDE_FRAME','COMPLETED',100,?,90,?)", frame, Timestamp.from(now), Timestamp.from(now));

        String onTheFrame = flesh(chronicle, chunk, now);
        assertEquals("FINE", onTheFrame,
            "laced into a frame and worked under tension, the same hide and the same hand come out one grade truer");

        // The data contract: both hide steps ask for the frame, and the frame can be raised.
        Integer asking = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process WHERE station_kind='HIDE_FRAME' AND process_key IN ('flesh_hide','dehair_hide')",
            Integer.class);
        assertEquals(2, asking, "fleshing and dehairing must both ask for the frame");
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM assembly_definition WHERE construction_kind='HIDE_FRAME')", Boolean.class)),
            "a station nobody can build is a station nobody can use");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Flesh one FINE deer hide carefully, and answer the grade of what came off. */
    private String flesh(UUID chronicle, UUID chunk, Instant now) {
        UUID hide = items.createCarriedItem(chronicle, "deer_hide", "Deer hide", now, "TEST_FIXTURE");
        jdbc.update("UPDATE item_instance SET quality_grade='FINE' WHERE object_id=?", hide);
        String[] done = items.runProcess(chronicle, chunk, "carefully flesh the hide", now);
        assertEquals("SUCCEEDED", done[0], () -> "the hide must be fleshed: " + done[1]);
        return jdbc.queryForObject(
            "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            " WHERE i.item_key='fleshed_hide' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            " ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }
}
