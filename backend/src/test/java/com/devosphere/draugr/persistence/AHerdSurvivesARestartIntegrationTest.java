package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
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
 * A herd survives a restart (#108, V434–V439).
 *
 * <p>#108's regression list names <b>restart</b> among the lifecycles that must be covered.
 * {@code SaveResumeDurabilityIntegrationTest} proves the general case — the Chronicle, the clock and the
 * carried items resume — and nothing proved it of the husbandry: the stock, what they are kept in, how many a
 * pen holds, whether the ground is drained, and the condition every one of those readings turns on.
 *
 * <p>It matters more here than it looks. This epic's work added five columns to four tables, and three of them
 * are read by <em>live rules every turn</em>: {@code holds_how_many} decides whether stock are crowded,
 * {@code drains_the_ground} whether their feet rot, {@code released_at} whether a keeping has ended. A column
 * that did not survive a restart, or a Flyway history that no longer validated against the schema, would take
 * all of that with it — and the way a player meets that failure is a launcher that hangs on the splash screen
 * (#648 was exactly that).
 *
 * <p>The mechanism is the one the save/resume test established: the container is a static field, not managed by
 * the Spring context, so the same database survives while {@code @DirtiesContext} rebuilds every bean, re-runs
 * Flyway and re-validates the schema against the entities. Skips without Docker.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AHerdSurvivesARestartIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // What was true before the restart, captured so the second half can hold the world to it.
    private static UUID keeperId;
    private static UUID bondId;
    private static UUID populationId;
    private static UUID byreId;
    private static UUID groundId;
    private static int herdSize;
    private static int thirstBefore;
    private static int roomBefore;
    private static int headBefore;

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

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void keepStockAndThenCaptureWhatIsTrueOfThem() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        keeperId = summary.id();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);
        groundId = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, keeperId);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, groundId);

        populationId = UUID.randomUUID();
        bondId = UUID.randomUUID();
        byreId = UUID.randomUUID();
        UUID site = UUID.randomUUID();
        herdSize = 3;

        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, groundId);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)",
            site, worldId, groundId);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "SELECT ?,?,ws.species_key,ws.ecological_role,ws.activity_cycle,?,8,'FORAGING',? FROM wildlife_species ws WHERE ws.species_key='ox'",
            populationId, site, herdSize, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
            "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',88,9,?,0,37,0,0)", bondId, keeperId, populationId, ts);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Cattle byre','ACTIVE',?)", byreId, groundId);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'CATTLE_BYRE','COMPLETED',100,?,64,?)", byreId, ts, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','CATTLE_BYRE'))", byreId, ts);

        thirstBefore = jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bondId);
        int[] room = items.theRoomTheStockHave(keeperId);
        headBefore = room[0];
        roomBefore = room[1];

        assertEquals(37, thirstBefore, "the fixture's thirst is what the restart has to carry");
        assertTrue(roomBefore > 0, "the byre must count as room before the restart, or the reading proves nothing");
        assertTrue(headBefore >= herdSize, "and the head count must include this herd");
    }

    @Test
    @Order(2)
    void afterTheRebuildTheHerdTheirHouseAndEveryReadingResume() {
        // A fresh Spring context — new beans, Flyway re-run, ddl-auto=validate — against the same database.

        // ---- THE STOCK, by identity and by number.
        assertEquals("TAMED", jdbc.queryForObject("SELECT bond_stage FROM wildlife_bond WHERE id=?", String.class, bondId),
            "the keeping survives a restart");
        assertEquals(thirstBefore, (int) jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bondId),
            "and so does every number on it, to the digit");
        assertEquals(88, (int) jdbc.queryForObject("SELECT trust_level FROM wildlife_bond WHERE id=?", Integer.class, bondId));
        assertEquals(herdSize, (int) jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, populationId),
            "the herd is the same herd, not a re-seeded one");
        assertEquals(groundId, jdbc.queryForObject(
            "SELECT es.chunk_id FROM ecology_site es JOIN wildlife_population wp ON wp.site_id=es.id WHERE wp.id=?", UUID.class, populationId),
            "on the same ground");

        // ---- THE HOUSE, with the condition every husbandry reading now turns on. 64 is weathered but above
        // COMING_APART_BELOW, and a restart that rounded it to 100 or to 0 would change what the byre DOES.
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, byreId));
        assertEquals(64, (int) jdbc.queryForObject("SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, byreId),
            "the state of repair is state, and must resume exactly");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='CONSTRUCTED'", Integer.class, byreId),
            "and its history is immutable across a restart");

        // ---- THE COLUMNS THIS EPIC ADDED ARE STILL THERE AND STILL READ. This is the half that would have been
        // missed by asserting rows alone: a migration that did not survive, or a Flyway history that no longer
        // validated, would leave the rules that read these columns silently inert.
        int[] room = items.theRoomTheStockHave(keeperId);
        assertEquals(headBefore, room[0], "the head count resumes");
        assertEquals(roomBefore, room[1], "and so does the room the pens hold (V434's holds_how_many)");
        assertNotNull(jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE holds_how_many IS NOT NULL", Integer.class),
            "holds_how_many survives the restart");
        assertTrue(jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE drains_the_ground", Integer.class) >= 1,
            "and V435's drains_the_ground, which decides whether their feet rot");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_bond WHERE id=? AND released_at IS NOT NULL", Integer.class, bondId),
            "and V439's released_at, which is null for a keeping that has not ended");

        // ---- AND THE RULES STILL RUN on the resumed state rather than merely reading it. The thirst pass is the
        // cheapest to prove: on dry ground with no trough it must move the same bond it moved before.
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id " +
                    "AND w.current_location_id=? AND cp.project_kind IN ('WATERING_STATION','RAINWATER_CATCHMENT')", groundId);
        boolean wetGround = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_chunk ch WHERE ch.id=? AND ch.biome IN ('WETLAND','RIVER_BANK'))", Boolean.class, groundId));
        items.advanceDraftThirst(Instant.now());
        int after = jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bondId);
        if (wetGround) assertTrue(after <= thirstBefore,
            () -> "on wet ground the resumed herd drinks where it stands (" + thirstBefore + " -> " + after + ")");
        else assertTrue(after != thirstBefore,
            () -> "the resumed herd must still be simulated, not frozen at the number it restarted with ("
                + thirstBefore + " -> " + after + ")");

        assertTrue(auditor.inspect().consistent(),
            () -> "and the world is sound after the rebuild: " + auditor.inspect().violations());
    }
}
