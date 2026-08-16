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
 * Stone-whetstone usefulness regression (dead-craft audit #257, EPIC #123). A dressed stone_whetstone is a
 * whetstone, but honing read only the found "whetstone" and the grit-stones — so a crafted stone_whetstone
 * sharpened nothing. Honing now accepts it. Proven end to end: a dull knife cannot be honed bare-handed but
 * can with a stone_whetstone to draw it against.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class StoneWhetstoneIntegrationTest {

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

    @Test
    void aStoneWhetstoneCanHoneADullEdge() {
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

        // A worn (dull) stone knife to put an edge back on.
        UUID knife = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Stone knife',?)", knife, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'stone_knife','WORN')", knife);

        // With no whetstone or grit-stone, honing must fail.
        ChronicleActionService.ActionResult bare = actions.resolve("I sharpen the stone knife.");
        assertEquals("FAILED", bare.outcome(), () -> "honing with no stone to draw against must fail: " + bare.perception());
        assertEquals("WORN", jdbc.queryForObject("SELECT condition_state FROM item_instance WHERE object_id=?", String.class, knife),
                "the edge stays dull with nothing to hone it on");

        // A crafted stone whetstone must serve — the edge comes back.
        items.createCarriedItem(chronicle, "stone_whetstone", "Stone whetstone", now, "TEST_SEED");
        ChronicleActionService.ActionResult honed = actions.resolve("I sharpen the stone knife.");
        assertEquals("SUCCEEDED", honed.outcome(), () -> "honing against a stone whetstone must succeed: " + honed.perception());
        assertEquals("SOUND", jdbc.queryForObject("SELECT condition_state FROM item_instance WHERE object_id=?", String.class, knife),
                "the honed edge returns to sound (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
