package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #879: a wetland survey reported "a full stand of timber" of willow, and the willow gave nothing to any act. Gathering
 * its branches answered "the wrong ground for it", felling it with a sound hatchet answered that it "offers nothing
 * your axe can shape", and stripping bark found no tree with workable bark. Each of the three is taken here, on a
 * wetland tile this test stocks with its own willow stand, through the same sentences the report used.
 */
@SpringBootTest
class TheWillowGivesItsWoodIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private static final Instant NOON = Instant.parse("2026-06-18T12:00:00Z");

    private UUID chunk;

    /** A fed, rested Chronicle at June midday on a wetland tile holding a full, uncut willow stand of its own. */
    private UUID awakenAmongTheWillows() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        assertNotNull(chunk, "the approved world must contain a WETLAND chunk");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        jdbc.update("UPDATE simulation_clock SET simulated_at=?", Timestamp.from(NOON));
        rest(summary.id());
        // The stand is this test's, not whatever other tests left: full, mature, never coppiced.
        // chunk_flora has no unique (chunk_id, flora_key) index, so this is an update, then an insert if none was there.
        int stocked = jdbc.update("UPDATE chunk_flora SET quantity=20, capacity=GREATEST(capacity,20), last_coppiced_at=NULL, "
                  + "established_at=NULL WHERE chunk_id=? AND flora_key='willow'", chunk);
        if (stocked == 0)
            jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity, last_harvested_at) VALUES (?,'willow',20,20,NULL)", chunk);
        items.createCarriedItem(summary.id(), "stone_hatchet", "Stone hatchet", NOON, "TEST_SEED");
        return summary.id();
    }

    private void rest(UUID chronicle) {
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, "
                  + "sleep_debt_hours=0, illness_severity=0, injury_severity=0, blood_loss_ml=0, core_temperature_c=37.0 WHERE chronicle_id=?",
            Timestamp.from(NOON), chronicle);
    }

    private int carried(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id "
            + "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, chronicle, itemKey);
        return n == null ? 0 : n;
    }

    private int lyingHere(String itemKey) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id "
            + "WHERE w.current_location_id=? AND w.current_owner_id IS NULL AND w.lifecycle_state='ACTIVE' AND i.item_key=?",
            Integer.class, chunk, itemKey);
        return n == null ? 0 : n;
    }

    @Test
    void theWillowGivesBranchesLogsAndBark() {
        UUID chronicle = awakenAmongTheWillows();

        int branchesBefore = carried(chronicle, "willow_branch");
        ChronicleActionService.ActionResult gathered = actions.resolve("gather willow branches");
        assertEquals("SUCCEEDED", gathered.outcome(), gathered.perception());
        assertTrue(carried(chronicle, "willow_branch") > branchesBefore, "withies were cut and carried: " + gathered.perception());

        rest(chronicle);
        int logsBefore = lyingHere("willow_log");
        ChronicleActionService.ActionResult felled = actions.resolve("fell a willow tree");
        assertEquals("SUCCEEDED", felled.outcome(), felled.perception());
        assertTrue(lyingHere("willow_log") > logsBefore, "a willow log lies where it fell: " + felled.perception());

        rest(chronicle);
        int barkBefore = carried(chronicle, "bark_sheet");
        ChronicleActionService.ActionResult stripped = actions.resolve("strip bark from a tree");
        assertEquals("SUCCEEDED", stripped.outcome(), stripped.perception());
        assertTrue(carried(chronicle, "bark_sheet") > barkBefore, "a sheet of bark was taken: " + stripped.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aWillowLogIsMilledLikeEveryOtherLog() {
        awakenAmongTheWillows();
        List<String> birch = jdbc.queryForList(
            "SELECT process_key || '/' || group_name FROM material_process_input_group WHERE item_key='birch_log' ORDER BY 1", String.class);
        List<String> willow = jdbc.queryForList(
            "SELECT process_key || '/' || group_name FROM material_process_input_group WHERE item_key='willow_log' ORDER BY 1", String.class);
        assertTrue(!birch.isEmpty(), "birch is milled somewhere");
        assertEquals(birch, willow, "a felled willow must be worked wherever a felled birch is, or it is a dead end");
    }
}
