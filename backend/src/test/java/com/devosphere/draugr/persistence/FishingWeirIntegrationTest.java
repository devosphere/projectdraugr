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

        // Stand at wetland water. fish() selects a species by species_key order and rolls the catch on the action's
        // hashCode, so we pick a species that actually drops raw_fish, make that drop certain, and choose an action
        // that selects it — leaving only the chance to vary between the two attempts.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        String affinity = "%WETLAND%";
        java.util.List<String> all = jdbc.queryForList("SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ? ORDER BY species_key", String.class, affinity);
        assertTrue(!all.isEmpty(), "the wetland must hold aquatic species to fish");
        java.util.List<String> withFish = jdbc.queryForList("SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ? AND species_key IN (SELECT species_key FROM wildlife_drop WHERE item_key='raw_fish') ORDER BY species_key", String.class, affinity);
        assertTrue(!withFish.isEmpty(), "the wetland must hold an aquatic species that drops raw fish");
        String target = withFish.get(0);
        int idx = all.indexOf(target);
        int size = all.size();
        jdbc.update("UPDATE wildlife_drop SET rarity=1.0, yield_min=1, yield_max=1 WHERE species_key=? AND item_key='raw_fish'", target);

        Instant now = Instant.now();

        // An action whose roll (hashCode mod 100) lands in [20,84]: at or above the bare-hand chance of 20 (so it
        // fails bare-handed) but below the weir's floor of 85 (so the weir lands it), and which selects the target
        // species. A range, not a fixed value, so a solution always exists whatever the species count is.
        UUID cast;
        while (true) {
            cast = UUID.randomUUID();
            int h = cast.hashCode();
            int roll = Math.floorMod(h, 100);
            if (roll >= 20 && roll < 85 && Math.floorMod(h, size) == idx) break;
        }

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
