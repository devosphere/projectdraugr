package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.BackdropResolver;
import com.devosphere.draugr.world.VisualContextService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A people's village is part of what a place looks like (#115, #224), through the real visual context.
 *
 * <p>Standing on the isle, the village is reported and the scene is the settlement; standing beside it, it is not
 * reported at all (only the lie of the land is seen beyond this ground); and once the village is gone, the place is
 * a marsh again and the fingerprint says the place has changed. Skips without Docker.
 */
@SpringBootTest
class AVillageIsSeenIntegrationTest {

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
    @Autowired VisualContextService visual;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theVillageIsSeenOnlyWhereItStandsAndOnlyWhileItStands() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant noon = Instant.parse("2031-06-10T12:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);

        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        var onIsle = visual.active(noon);
        assertTrue(onIsle.features().stream().anyMatch(f -> "SETTLEMENT:reedkin".equals(f.kind())), () -> "the village is seen: " + onIsle.features());
        assertEquals("settlement.reedkin", BackdropResolver.resolve(onIsle).key());

        UUID beside = jdbc.queryForObject("SELECT n.id FROM world_chunk i JOIN world_chunk n ON n.world_id=i.world_id " +
            "AND abs(n.grid_x-i.grid_x)+abs(n.grid_y-i.grid_y)=1 WHERE i.id=? ORDER BY n.grid_y, n.grid_x LIMIT 1", UUID.class, isle);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", beside, chronicle);
        var nextDoor = visual.active(noon);
        assertFalse(nextDoor.features().stream().anyMatch(f -> f.kind().startsWith("SETTLEMENT:")),
            "from the next ground only the lie of the land is seen, never what stands on it");

        // Gone: the place is a marsh again, and the fingerprint says it changed.
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        UUID village = jdbc.queryForObject("SELECT object_id FROM native_settlement_site WHERE community_id=? AND site_kind='VILLAGE'", UUID.class, community);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=now(), destroyed_location_id=?, destroyed_cause='BURNED', " +
            "current_location_id=NULL WHERE id=?", isle, village);
        var afterFire = visual.active(noon);
        assertFalse(afterFire.features().stream().anyMatch(f -> f.kind().startsWith("SETTLEMENT:")), "a burnt village is no longer scenery");
        assertNotEquals(onIsle.fingerprint(), afterFire.fingerprint());
        jdbc.update("UPDATE world_object SET lifecycle_state='ACTIVE', destroyed_at=NULL, destroyed_location_id=NULL, destroyed_cause=NULL, " +
            "current_location_id=? WHERE id=?", isle, village);
        assertEquals(onIsle.fingerprint(), visual.active(noon).fingerprint(), "the same place, standing again, looks the same");
    }
}
