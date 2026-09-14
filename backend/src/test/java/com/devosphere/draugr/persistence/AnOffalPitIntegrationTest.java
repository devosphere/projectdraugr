package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #108 V331 — an offal pit. Butchering at camp fouls the ground with 15 refuse; where an offal pit stands the guts
 * go into it and the ground takes 3. A pit that has caved in takes nothing. Skips without Docker.
 */
@SpringBootTest
class AnOffalPitIntegrationTest {

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

    private int refuse(UUID chunk) {
        Integer v = jdbc.query("SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?", rs -> rs.next() ? rs.getInt(1) : null, chunk);
        return v == null ? 0 : v;
    }

    private void clean(UUID chunk) {
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
    }

    /**
     * A red deer carcass lying here with meat enough for several butcherings, so it is never left spent and active.
     * The herd it came from is made here too: a fresh world seeds only a handful of populations, and which ones is
     * not something a test should lean on.
     */
    private void carcassAt(UUID chunk, Timestamp at) {
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Deer range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Deer range',30)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'red_deer','HERBIVORE','DIURNAL',4,4,'FORAGING',?)", pop, site, at);
        UUID carcass = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CARCASS','Red deer carcass',?)", carcass, chunk);
        jdbc.update("INSERT INTO wildlife_carcass (object_id,source_population_id,species_key,remaining_meat_units,hide_available,killed_by_action_id,died_at) VALUES (?,?,'red_deer',6,true,?,?)",
            carcass, pop, UUID.randomUUID(), at);
    }

    private UUID pitAt(UUID chunk, Timestamp at) {
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Offal pit','ACTIVE',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) VALUES (?,'CARCASS_PIT','COMPLETED',100,?,100,?)",
            pit, at, at);
        return pit;
    }

    @Test
    void butcheryGoesIntoThePitAndNotOntoTheGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        // Stamped at simulation time, so nothing here looks old to the first tick.
        Timestamp at = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("DELETE FROM construction_project cp USING world_object w, construction_kind ck " +
                    "WHERE w.id=cp.object_id AND ck.project_kind=cp.project_kind AND w.current_location_id=? AND ck.takes_offal", chunk);
        carcassAt(chunk, at);

        // Butchered on the bare camp ground: the offal goes onto it.
        clean(chunk);
        var bare = actions.resolve("butcher the carcass");
        assertEquals("SUCCEEDED", bare.outcome(), () -> "there is a carcass here to butcher: " + bare.perception());
        assertEquals(WildlifeEncounterService.BUTCHERY_REFUSE, refuse(chunk), "butchering on bare ground fouls it as before");

        // With an offal pit dug, the same work leaves only what the pit does not take.
        UUID pit = pitAt(chunk, at);
        clean(chunk);
        var pitted = actions.resolve("butcher the carcass");
        assertEquals("SUCCEEDED", pitted.outcome(), () -> "the carcass is still there: " + pitted.perception());
        assertEquals(WildlifeEncounterService.BUTCHERY_REFUSE_INTO_A_PIT, refuse(chunk), "the offal goes into the pit, not onto the camp");
        assertTrue(WildlifeEncounterService.BUTCHERY_REFUSE_INTO_A_PIT < WildlifeEncounterService.BUTCHERY_REFUSE, "a pit must take something");

        // A pit that has caved in takes nothing.
        jdbc.update("UPDATE construction_project SET integrity_percent=0 WHERE object_id=?", pit);
        clean(chunk);
        var caved = actions.resolve("butcher the carcass");
        assertEquals("SUCCEEDED", caved.outcome(), () -> "the carcass is still there: " + caved.perception());
        assertEquals(WildlifeEncounterService.BUTCHERY_REFUSE, refuse(chunk), "a caved-in pit is no pit");

        jdbc.update("UPDATE construction_project SET integrity_percent=100 WHERE object_id=?", pit);
        clean(chunk);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
