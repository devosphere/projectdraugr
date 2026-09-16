package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The going of the ground (#77/#155, V335).
 *
 * <p>A journey used to cost eighteen minutes a chunk whatever it crossed: a meadow, a fen and a mountainside were
 * the same walk, and no laid way could ever make a route quicker because there was nothing to make quicker.
 *
 * <p>The same Chronicle walks the same three chunks twice, to the same named place, and the only thing that changes
 * between the two journeys is what the ground is made of. Every biome is put back afterwards, because the other
 * journeys in this suite share the world.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheGoingOfTheGroundIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aMountainIsNotWalkedAsQuicklyAsAMeadow() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        // A straight line of four chunks — where the Chronicle stands and three more due east — so the distance is
        // three and every chunk on the way is one this test controls.
        Map<String,Object> here = jdbc.queryForMap(
            "SELECT c.id, c.world_id, c.grid_x, c.grid_y FROM world_chunk c " +
            "WHERE EXISTS (SELECT 1 FROM world_chunk e WHERE e.world_id=c.world_id AND e.grid_y=c.grid_y AND e.grid_x=c.grid_x+3) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID origin = (UUID) here.get("id");
        UUID world = (UUID) here.get("world_id");
        int x = (int) here.get("grid_x"), y = (int) here.get("grid_y");
        List<Map<String,Object>> line = jdbc.queryForList(
            "SELECT id, biome FROM world_chunk WHERE world_id=? AND grid_y=? AND grid_x BETWEEN ? AND ? ORDER BY grid_x",
            world, y, x, x + 3);
        assertEquals(4, line.size(), "the fixture needs four chunks in a row to walk along");
        UUID destination = (UUID) line.get(3).get("id");

        jdbc.update("INSERT INTO chronicle_named_location (chronicle_id,chunk_id,name,designated_at,memorized,last_visited_at) " +
            "VALUES (?,?,'Stonewell',?,TRUE,?) ON CONFLICT (chronicle_id,chunk_id,name) DO UPDATE " +
            "SET memorized=TRUE, last_visited_at=EXCLUDED.last_visited_at",
            chronicle, destination, Timestamp.from(ticks.current().simulatedAt()), Timestamp.from(ticks.current().simulatedAt()));

        try {
            long overMeadow = journey(chronicle, origin, destination, line, "GRASSLAND");
            long overMountain = journey(chronicle, origin, destination, line, "MOUNTAIN");

            assertTrue(overMountain > overMeadow,
                () -> "three chunks of mountain must take longer than three chunks of meadow — this is the defect: "
                    + "every kind of ground cost the same eighteen minutes (meadow " + overMeadow + " min, mountain "
                    + overMountain + " min)");
            // 3 x 15 and 3 x 40, with a floor of fifteen minutes on any journey. Asserted as bands rather than to
            // the minute, so tuning the table is not a test failure, but tight enough that a flat rate cannot pass.
            assertTrue(overMeadow <= 60, () -> "open grass must be quicker than the old flat rate allowed: " + overMeadow);
            assertTrue(overMountain >= 100, () -> "a mountainside must cost hours, not the flat rate: " + overMountain);
        } finally {
            for (Map<String,Object> chunk : line)
                jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", chunk.get("biome"), chunk.get("id"));
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Lay the whole line down as one kind of country, walk it, and answer how many simulated minutes it took. */
    private long journey(UUID chronicle, UUID origin, UUID destination, List<Map<String,Object>> line, String biome) {
        for (Map<String,Object> chunk : line) jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", biome, chunk.get("id"));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", origin, chronicle);

        Instant before = ticks.current().simulatedAt();
        var travelled = actions.resolve("travel to Stonewell");
        assertEquals("SUCCEEDED", travelled.outcome(), () -> "the journey must be made: " + travelled.perception());
        assertEquals(destination, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle),
            "and it must end where it was aimed");
        return Duration.between(before, ticks.current().simulatedAt()).toMinutes();
    }
}
