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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #77 (key 97) - a completed fishing weir does the work. Proves the differential: with one fixed action whose
 * roll would fail bare-handed (chance 20) but pass at the weir's high floor (chance 85), the same attempt on the same
 * water FAILS with no weir and SUCCEEDS once a completed weir stands there. Skips without Docker.
 */
@SpringBootTest
class FishingWeirIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aCompletedWeirTurnsAFailedCastIntoACatch() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();

        // Stand at wetland water that holds fish; make every drop certain so a successful roll always lands a catch.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        String biome = "WETLAND";
        Integer aquatic = jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ?", Integer.class, "%"+biome+"%");
        assertTrue(aquatic != null && aquatic > 0, "the wetland must hold aquatic species to fish");
        jdbc.update("UPDATE wildlife_drop SET rarity=1.0 WHERE species_key IN (SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ?)", "%"+biome+"%");

        Instant now = Instant.now();

        // A fixed action whose roll (hashCode mod 100) is 50: fails at the bare-hand chance of 20, passes at the
        // weir's floor of 85. Same action UUID for both attempts, so only the weir differs between them.
        UUID cast;
        do { cast = UUID.randomUUID(); } while (Math.floorMod(cast.hashCode(), 100) != 50);

        var bare = wildlife.fish(chronicle, chunk, cast, now, "fish here");
        assertEquals("FAILED", bare.outcome(), () -> "bare-handed with a middling roll, the cast must fail: " + bare.narration());

        // Raise a completed fishing weir on this water.
        UUID weir = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Fishing weir','ACTIVE',?)", weir, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'FISHING_WEIR','COMPLETED',100,?,100)", weir, Timestamp.from(now));

        var withWeir = wildlife.fish(chronicle, chunk, cast, now, "fish here");
        assertEquals("SUCCEEDED", withWeir.outcome(), () -> "the same cast must land a catch once a completed weir funnels the fish: " + withWeir.narration());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
