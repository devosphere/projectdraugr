package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.FireService;
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
 * A roaring fire must reach everything beside it that burns (#219).
 *
 * <p>What could catch was three hardcoded project kinds in Java — LEAN_TO, FUEL_RACK, BRUSH_FENCE — while the
 * catalogue grew to 45 kinds, twenty-odd of them built out of thatch, reed, brush, bark, hide and untrimmed
 * timber. A reed hut, a bark cabin, a hay rack or a wattle fence stood beside a roaring hearth in dry weather
 * completely fireproof, because its name was not on that list. Flammability is a property of the kind now.
 *
 * <p>Proves a reed hut scorches where it used to be immune, that stone and earth do not, and that the wet-sky
 * and small-fire guards still hold. Skips without Docker.
 */
@SpringBootTest
class FireReachesWhatBurnsIntegrationTest {

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
    @Autowired FireService fire;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID chunk;

    private void ground() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        jdbc.update("DELETE FROM fire_state fs USING world_object w JOIN construction_project cp ON cp.object_id=w.id " +
                    "WHERE fs.construction_id=w.id AND w.current_location_id=?", chunk);
        jdbc.update("UPDATE world_weather SET weather_kind='CLEAR'");
    }

    /** Something standing here, whole. */
    private UUID standing(String kind) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION',?,'ACTIVE',?)",
            id, kind.toLowerCase().replace('_', ' '), chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,?,'COMPLETED',100,now(),100,now())", id, kind);
        return id;
    }

    /** A big fire that has been roaring unattended for hours. */
    private void roaringFireLeftFor(Duration hours) {
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit','ACTIVE',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,now(),100,now())", pit);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,240,?)",
            pit, Timestamp.from(Instant.now().minus(hours)));
    }

    private int integrityOf(UUID id) {
        Integer n = jdbc.queryForObject("SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, id);
        return n == null ? -1 : n;
    }

    @Test
    void aReedHutBesideARoaringFireScorchesAndStoneDoesNot() {
        ground();
        UUID reedHut = standing("REED_HUT");        // thatch and reed — it burns
        UUID stoneWall = standing("STONE_WALL_LOW"); // it does not
        roaringFireLeftFor(Duration.ofHours(6));

        fire.advanceTo(Instant.now());

        assertTrue(integrityOf(reedHut) < 100,
            "a reed hut left beside a roaring fire in dry weather must scorch — it was fireproof only because its name was not on a list");
        assertEquals(100, integrityOf(stoneWall), "stone does not catch");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void rainKeepsTheEmbersFromCatching() {
        ground();
        UUID hut = standing("BARK_CABIN");
        roaringFireLeftFor(Duration.ofHours(6));
        jdbc.update("UPDATE world_weather SET weather_kind='RAIN'");

        fire.advanceTo(Instant.now());

        assertEquals(100, integrityOf(hut), "a wet sky keeps the embers from catching, whatever the fire is doing");
    }

    @Test
    void aSmallFireIsNotAHazard() {
        ground();
        UUID hut = standing("HAY_RACK");
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit','ACTIVE',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,now(),100,now())", pit);
        // A modest, tended fire — well under the roaring threshold.
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,45,?)",
            pit, Timestamp.from(Instant.now().minus(Duration.ofHours(6))));

        fire.advanceTo(Instant.now());

        assertEquals(100, integrityOf(hut),
            "a hearth tended and banked in good order never bites; only a big fire left alone does");
    }
}
