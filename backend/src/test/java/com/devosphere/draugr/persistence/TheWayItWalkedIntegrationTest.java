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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The way the ground felt to walk (#30).
 *
 * <p>Every step in the world answered with the same eleven words — "the ground shifting under you as you go" —
 * whether the Chronicle had walked into a meadow, a fen or a cave. Repeated through a playthrough it is the
 * most-read sentence in the game and the emptiest, which is the robotic narration #30 is about.
 *
 * <p>What replaces it is not invented per biome: it is {@code terrain_going.note}, the row that already decides
 * what the country COSTS to cross, so the sentence a player reads and the time the clock charged them can never
 * disagree. This test walks into several kinds of country and asserts the line actually changes with the ground —
 * and that each line is the note for the ground the Chronicle ended up on, not the one they left.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheWayItWalkedIntegrationTest {

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
    void aStepSaysWhatTheGroundWasLikeToWalkOn() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        // Far from any carry limit, so the fen below refuses nothing: this test is about what the step SAYS, and a
        // laden Chronicle turned back at the marsh would be testing the load rule instead (#156/#157).
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Two chunks side by side: the Chronicle stands on the western one and steps east into ground this test
        // decides the kind of. Nothing else about the world changes between the walks.
        Map<String,Object> pair = jdbc.queryForMap(
            "SELECT w.id AS from_id, e.id AS to_id, w.biome AS from_biome, e.biome AS to_biome " +
            "FROM world_chunk w JOIN world_chunk e ON e.world_id=w.world_id AND e.grid_y=w.grid_y AND e.grid_x=w.grid_x+1 " +
            "ORDER BY w.grid_y, w.grid_x LIMIT 1");
        UUID from = (UUID) pair.get("from_id"), into = (UUID) pair.get("to_id");

        Set<String> lines = new HashSet<>();
        try {
            for (String biome : List.of("GRASSLAND", "WETLAND", "MOUNTAIN", "TEMPERATE_FOREST")) {
                jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", biome, into);
                jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", from, chronicle);

                var stepped = actions.resolve("walk east");
                assertEquals("SUCCEEDED", stepped.outcome(), () -> "the step must be taken: " + stepped.perception());
                String line = stepped.perception();

                String note = jdbc.queryForObject("SELECT note FROM terrain_going WHERE biome=?", String.class, biome);
                String withoutStop = note.endsWith(".") ? note.substring(0, note.length() - 1) : note;
                String expected = Character.toLowerCase(withoutStop.charAt(0)) + withoutStop.substring(1);
                assertTrue(line.contains(expected),
                    () -> "walking into " + biome + " must say what that ground is like to walk on — the note the "
                        + "clock charges the journey by — and instead said: " + line);
                assertTrue(!line.contains("the ground shifting under you as you go"),
                    () -> "the one empty line for every kind of country in the world is the defect: " + line);
                lines.add(line);
            }

            assertTrue(lines.size() >= 4,
                () -> "four kinds of ground must read four ways, or the line is still the same sentence wearing "
                    + "different hats: " + lines);
        } finally {
            jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", pair.get("to_biome"), into);
            jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", pair.get("from_biome"), from);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
