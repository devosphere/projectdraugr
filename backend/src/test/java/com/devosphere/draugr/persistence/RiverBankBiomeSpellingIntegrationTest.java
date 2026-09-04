package com.devosphere.draugr.persistence;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wet-ground effects must actually fire on riverbank ground. The codebase spelled that biome two ways — the data and
 * several checks use RIVER_BANK, while the rot, slaking, willow and resource-ecology paths said RIVERBANK. Nothing
 * caught it because the generator produces no riverbank at all yet (#156), so both spellings were dead. This pins the
 * canonical spelling by putting a Chronicle's greenware on riverbank ground and proving it slakes. Skips without Docker.
 */
@SpringBootTest
class RiverBankBiomeSpellingIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void wetGroundEffectsFireOnRiverBank() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertNotNull(chronicles.awaken());

        // Riverbank ground. The generator does not make any yet (#156), so one is set up here to pin the spelling.
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have ground to convert for this fixture");
        String original = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);
        jdbc.update("UPDATE world_chunk SET biome='RIVER_BANK' WHERE id=?", chunk);
        try {
            Instant now = Instant.now();
            UUID left = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'ITEM','Unfired bowl','ACTIVE',?)", left, chunk);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,weathered_at) VALUES (?,'unfired_bowl',?)",
                left, Timestamp.from(now.minus(Duration.ofHours(24))));

            items.slakeExposedGreenware(now);

            assertEquals("DESTROYED", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, left),
                "unfired ware left on riverbank ground must slake — the wet-ground check must match the canonical RIVER_BANK spelling");
            assertEquals("SLAKED", jdbc.queryForObject("SELECT destroyed_cause FROM world_object WHERE id=?", String.class, left),
                "its loss must be recorded as slaking");
        } finally {
            jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", original, chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
