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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #77 - a completed rainwater catchment is itself a source to fill from. Proves that on dry ground with no
 * stream a Chronicle cannot collect water, but once a rainwater catchment stands there the same action fills the
 * vessel with raw water (still best boiled). Skips without Docker.
 */
@SpringBootTest
class RainwaterCatchmentWaterIntegrationTest {

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
    void aCatchmentBecomesAWaterSourceOnDryGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();

        // Stand on dry ground: a forest chunk with every natural water source removed.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome NOT IN ('WETLAND','RIVER_BANK') ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND (site_kind ILIKE '%spring%' OR site_kind ILIKE '%stream%' OR site_kind ILIKE '%river%' OR site_kind ILIKE '%freshwater%')", chunk);

        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "waterskin", "Waterskin", now, "TEST_FIXTURE");

        // No water in reach yet: collection fails on the dry ground.
        var dry = actions.resolve("collect water");
        assertEquals("FAILED", dry.outcome(), () -> "there must be no water to collect on dry ground: " + dry.perception());

        // Raise a completed rainwater catchment on this ground.
        UUID catchment = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Rainwater catchment','ACTIVE',?)", catchment, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'RAINWATER_CATCHMENT','COMPLETED',100,?,100)", catchment, java.sql.Timestamp.from(now));

        // Now the catchment is the source: the same action fills the vessel with raw water.
        var wet = actions.resolve("collect water");
        assertEquals("SUCCEEDED", wet.outcome(), () -> "a completed rainwater catchment must be a source to fill from: " + wet.perception());
        assertTrue(items.hasAtLeast(chronicle, "raw_water", 1), "raw water must now be in reach from the catchment");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
