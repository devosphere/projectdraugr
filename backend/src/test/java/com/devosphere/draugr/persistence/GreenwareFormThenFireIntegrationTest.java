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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #59 - the greenware stage. A vessel is formed wet and fired later, not both at once: forming yields soft
 * unfired ware that holds nothing, and only the fire makes it permanent. Greenware is water-sensitive — left out on
 * wet ground it takes up water and slumps away. Skips without Docker.
 */
@SpringBootTest
class GreenwareFormThenFireIntegrationTest {

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

    @Test
    void aVesselIsFormedWetThenFired_andGreenwareSlakesInTheWet() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must hold wetland ground");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // Forming is wet work: no fire needed, and what comes off the hands is soft ware, not a usable vessel.
        items.createCarriedItem(chronicle, "tempered_clay", "Tempered clay", now, "TEST_FIXTURE");
        var formed = actions.resolve("form a clay bowl");
        assertEquals("SUCCEEDED", formed.outcome(), () -> "forming a bowl must succeed without any fire: " + formed.perception());
        assertTrue(items.hasAtLeast(chronicle, "unfired_bowl", 1), "forming must yield soft, unfired ware");
        assertFalse(items.hasAtLeast(chronicle, "fired_bowl", 1), "forming alone must NOT produce a fired vessel");

        // Only the fire makes it permanent.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)", pit, ts);

        var fired = actions.resolve("fire the clay bowl");
        assertEquals("SUCCEEDED", fired.outcome(), () -> "firing the greenware must succeed over a live fire: " + fired.perception());
        assertTrue(items.hasAtLeast(chronicle, "fired_bowl", 1), "the fired bowl must be in hand");

        // Greenware is water-sensitive: left out on wet ground it slumps away. A carried piece is kept dry.
        UUID left = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ITEM','Unfired bowl',?)", left, chunk);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,weathered_at) VALUES (?,'unfired_bowl',?)",
            left, Timestamp.from(now.minus(Duration.ofHours(24))));
        items.slakeExposedGreenware(now);

        String lifecycle = jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, left);
        assertEquals("DESTROYED", lifecycle, "unfired ware left in the wet must slake away");
        assertEquals("SLAKED", jdbc.queryForObject("SELECT destroyed_cause FROM world_object WHERE id=?", String.class, left),
            "its loss must be recorded as slaking");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
