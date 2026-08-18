package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.construction.ConstructionService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Construction-weathering regression (EPIC #215, story #220). The field structures a Chronicle raises — fences,
 * lookouts, fuel racks, latrines, alarms — are marked as decaying and carry mend/refresh paths, but nothing wore
 * them down, so those flags and repairs read against nothing. Now every decaying non-shelter, non-workstation
 * construction loses integrity slowly over time (faster in foul weather) and, left unmended, eventually collapses
 * with history. Proven end to end: a standing fence weathers over three weeks, and a near-worn one collapses.
 *
 * <p>Shelters keep their own upkeep path and are untouched here. Skips gracefully without Docker.
 */
@SpringBootTest
class ConstructionWeatheringIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID fabricateFence(UUID chunk, int integrity, Timestamp at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Wattle fence',?)", id, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
                "VALUES (?,'WATTLE_FENCE','COMPLETED',100,?,?,?)", id, at, integrity, at);
        return id;
    }

    @Test
    void fieldStructuresWeatherOverTimeAndCollapseUnmended() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
        }
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE world_weather SET weather_kind='CLEAR' WHERE world_id=?", world); // fair weather → 1 point/day
        Instant base = Instant.parse("2026-06-01T12:00:00Z");
        Timestamp baseTs = Timestamp.from(base);

        UUID standing = fabricateFence(chunk, 100, baseTs);  // a whole fence
        UUID worn = fabricateFence(chunk, 2, baseTs);         // one all but gone

        // Three weeks pass with no mending, in fair weather.
        construction.advanceTo(base.plus(Duration.ofDays(21)));

        int standingIntegrity = jdbc.queryForObject("SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, standing);
        assertTrue(standingIntegrity < 100 && standingIntegrity > 0,
                () -> "a standing field structure must weather over time but not vanish in three fair weeks (was " + standingIntegrity + ") (#220)");

        // The worn fence has weathered away entirely and collapsed — with history, not a silent disappearance.
        assertEquals("DESTROYED", jdbc.queryForObject("SELECT state FROM construction_project WHERE object_id=?", String.class, worn),
                "an unmended field structure that reaches nought integrity must collapse (#220)");
        assertEquals("DESTROYED", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, worn),
                "the collapsed structure's world_object must be retired");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='CONSTRUCTION_COLLAPSED'", Integer.class, worn) >= 1,
                "the collapse must be kept in history");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
