package com.devosphere.draugr.persistence;

import com.devosphere.draugr.assembly.AssemblyService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.Compass;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A part-built structure stands where it was begun (#77).
 *
 * <p>#77's closing comment left this on the record: the progress lookup was not location-scoped. One of a kind may
 * be under way per Chronicle, and the instance was found by Chronicle and kind alone — so posts set on one piece of
 * ground took their bars on another, and the finished rack went up wherever the last stage was worked. Looking the
 * work over and stripping a flawed stage back out reached it from anywhere too.
 *
 * <p>And moving the "naming a thing is using it" rule ahead of the place conditions closed a second face: with a
 * poultry run standing, "feed the hens in the poultry run" was refused because a poultry run already takes this
 * ground.
 *
 * <p>Every fixture here is this class's own — its chunks are picked at the far edge of the world, its instance and
 * standing run are inserted and torn down, and any drying rack the Chronicle already had under way is set aside
 * and handed back — so nothing another test left behind can decide an assertion. Skips without Docker.
 */
@SpringBootTest
class AWorkStandsWhereItWasBegunIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final Instant AT = Instant.parse("2026-06-15T12:00:00Z");

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
    @Autowired AssemblyService assembly;
    @Autowired JdbcTemplate jdbc;

    private UUID chronicle;
    /** Where the rack was begun, the next ground east of it, and ground well away to the east. */
    private UUID begun, next, farOff;
    private final List<UUID> setAside = new ArrayList<>();
    private final List<UUID> instances = new ArrayList<>();
    private final List<UUID> raised = new ArrayList<>();

    @BeforeEach
    void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var c = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(c, "a living Chronicle is needed");
        chronicle = c.id();

        // The western end of the world's southernmost row, and two chunks east along it. Nobody else builds there.
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, c.locationId());
        Map<String, Object> corner = jdbc.queryForMap(
            "SELECT grid_x, grid_y FROM world_chunk WHERE world_id=? ORDER BY grid_y DESC, grid_x LIMIT 1", world);
        int x = (int) corner.get("grid_x"), y = (int) corner.get("grid_y");
        begun = chunkAt(world, x, y);
        next = chunkAt(world, x + 1, y);
        farOff = chunkAt(world, x + 4, y);

        // Any rack or run this Chronicle already had under way is set aside, not destroyed, and handed back after —
        // one half-built anywhere else would answer every assertion here with "half-raised".
        setAside.addAll(jdbc.queryForList(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND assembly_key IN ('drying_rack','poultry_run') AND state='IN_PROGRESS'",
            UUID.class, chronicle));
        for (UUID id : setAside) jdbc.update("UPDATE assembly_instance SET state='ABANDONED' WHERE id=?", id);
    }

    @AfterEach
    void handBack() {
        for (UUID id : instances) {
            jdbc.update("DELETE FROM assembly_stage_completion WHERE instance_id=?", id);
            jdbc.update("DELETE FROM assembly_instance WHERE id=?", id);
        }
        for (UUID id : raised) {
            jdbc.update("DELETE FROM construction_project WHERE object_id=?", id);
            jdbc.update("DELETE FROM world_object WHERE id=?", id);
        }
        for (UUID id : setAside) jdbc.update("UPDATE assembly_instance SET state='IN_PROGRESS' WHERE id=?", id);
        instances.clear(); raised.clear(); setAside.clear();
    }

    private UUID chunkAt(UUID world, int x, int y) {
        UUID id = jdbc.query("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, world, x, y);
        assertNotNull(id, () -> "the world must hold a chunk at " + x + "," + y);
        return id;
    }

    /** A drying rack with its posts set on {@code begun} — the first of its two stages done, the bars to come. */
    private UUID postsSetOnTheBegunGround() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assembly_instance (id, assembly_key, chronicle_id, location_id) VALUES (?,'drying_rack',?,?)",
            id, chronicle, begun);
        instances.add(id);
        String first = jdbc.queryForObject(
            "SELECT stage_key FROM assembly_stage WHERE assembly_key='drying_rack' ORDER BY stage_order LIMIT 1", String.class);
        jdbc.update("INSERT INTO assembly_stage_completion (instance_id, stage_key, completed_at, quality_grade) VALUES (?,?,?,'DEFECTIVE')",
            id, first, Timestamp.from(AT.minusSeconds(3600)));
        return id;
    }

    private int stagesDone(UUID instance) {
        return jdbc.queryForObject("SELECT count(*) FROM assembly_stage_completion WHERE instance_id=?", Integer.class, instance);
    }

    @Test
    void theWorkIsNotCarriedOnFromOtherGround() {
        UUID rack = postsSetOnTheBegunGround();

        String[] fromNext = assembly.advance(chronicle, next, "build a drying rack", AT);
        assertNotNull(fromNext);
        assertEquals("FAILED", fromNext[0], () -> "work went on away from the rack: " + fromNext[1]);
        assertTrue(fromNext[1].contains("half-raised") && fromNext[1].contains("next ground to the " + Compass.of(-1, 0)),
            () -> "the refusal must say the rack lies on the next ground west: " + fromNext[1]);

        String[] fromFar = assembly.advance(chronicle, farOff, "build a drying rack", AT);
        assertTrue(fromFar[1].contains("some way off to the " + Compass.of(-4, 0)),
            () -> "four chunks off is not the next ground: " + fromFar[1]);

        assertEquals(1, stagesDone(rack), "no stage may be added to a rack from ground it does not stand on");
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT state FROM assembly_instance WHERE id=?", String.class, rack));
    }

    @Test
    void onItsOwnGroundTheWorkGoesOn() {
        // THE POSITIVE CASE. The posts are DEFECTIVE, so on its own ground the rack is reached and its flaw gates
        // the bars — a sentence only the instance itself can produce, whatever the Chronicle happens to carry.
        postsSetOnTheBegunGround();
        String[] here = assembly.advance(chronicle, begun, "build a drying rack", AT);
        assertNotNull(here);
        assertFalse(here[1].contains("half-raised"), () -> "on its own ground it must not be sent elsewhere: " + here[1]);
        assertTrue(here[1].contains("reworked"), () -> "the rack on this ground was not the one reached: " + here[1]);
    }

    @Test
    void itIsLookedOverAndReworkedWhereItStands() {
        UUID rack = postsSetOnTheBegunGround();

        String[] lookFromNext = assembly.inspect(chronicle, next, "inspect the drying rack");
        assertTrue(lookFromNext[1].contains("half-raised"), () -> "it was looked over from the next ground: " + lookFromNext[1]);
        String[] lookHere = assembly.inspect(chronicle, begun, "inspect the drying rack");
        assertTrue(lookHere[1].contains("defective"), () -> "standing at it, the flawed posts must show: " + lookHere[1]);

        String[] reworkFromNext = assembly.rework(chronicle, next, "rework the drying rack", AT);
        assertEquals("FAILED", reworkFromNext[0]);
        assertEquals(1, stagesDone(rack), "the posts were stripped out from a chunk away");

        String[] reworkHere = assembly.rework(chronicle, begun, "rework the drying rack", AT);
        assertEquals("SUCCEEDED", reworkHere[0], () -> reworkHere[1]);
        assertEquals(0, stagesDone(rack), "on its own ground the flawed posts come back out");
    }

    @Test
    void namingTheRunTheHensAreInIsNotBuildingAnother() {
        // A poultry run takes the whole ground (V432). Naming it while it stands is using it, and the footprint
        // rule used to answer first: "A poultry run already takes this ground".
        assertTrue(jdbc.queryForObject(
            "SELECT takes_the_whole_ground FROM construction_kind WHERE project_kind=" +
            "(SELECT construction_kind FROM assembly_definition WHERE assembly_key='poultry_run')", Boolean.class),
            "the premise: a poultry run takes its ground whole");
        assertEquals(0, jdbc.queryForObject(
            "SELECT count(*) FROM assembly_instance WHERE chronicle_id=? AND assembly_key='poultry_run' AND state='IN_PROGRESS'",
            Integer.class, chronicle), "the premise: no run is under way anywhere");

        UUID run = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Poultry run',?)",
            run, farOff);
        raised.add(run);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) " +
            "SELECT ?, construction_kind, 'COMPLETED', 100, ? FROM assembly_definition WHERE assembly_key='poultry_run'",
            run, Timestamp.from(AT));

        assertNull(assembly.advance(chronicle, farOff, "feed the hens in the poultry run", AT),
            "a run standing here, named by its bare name, is a reference and builds nothing");

        String[] another = assembly.advance(chronicle, farOff, "build a poultry run", AT);
        assertNotNull(another);
        assertTrue(another[1].contains("already takes this ground"),
            () -> "asking to BUILD one is still refused by the footprint rule: " + another[1]);
    }
}
