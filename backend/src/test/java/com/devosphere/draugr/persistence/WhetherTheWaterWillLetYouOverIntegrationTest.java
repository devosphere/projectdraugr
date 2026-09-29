package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Whether the water will let you over (#37).
 *
 * <p>Act thirteen, standing on a river bank:
 *
 * <pre>
 *   how deep is it    ->  "You sound the river with a stick — it shelves off gradually, past a safe wade
 *                          before long."
 *   can I cross here  ->  UNKNOWN
 * </pre>
 *
 * <p>The depth answer is one of the better lines in the game, and it describes a wade the player then cannot ask
 * about. Crossing <b>exists</b>: {@code waterCrossing} refuses a Chronicle carrying more than a quarter of their
 * capacity into the sea or three quarters into a fen, a ford lifts the refusal outright, and a laid timber way
 * lifts it over peat. All of it was reachable only by walking into the water and being told no.
 *
 * <p>Answered from those same readers, as a judgement that never moves anybody — the rule V392 set when asking
 * whether water was safe had been answered by drinking it. Skips without Docker.
 */
@SpringBootTest
class WhetherTheWaterWillLetYouOverIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockWas;

    /** Pin the clock, date nothing from the wall clock, and let the body keep pace with it — all three, together,
     *  because doing only the first is what broke two other classes of mine this week. */
    @BeforeEach
    void pinTheWorld() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "pain_level=0, blood_loss_ml=0, energy_level=100");
    }

    @AfterEach
    void unpinTheWorld() {
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
    }

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /** Dry ground with a fen next to it, and nothing carried. */
    private UUID standBesideAFen(UUID chronicle) {
        UUID beside = jdbc.queryForObject(
            "SELECT here.id FROM world_chunk here JOIN world_chunk next ON next.world_id=here.world_id " +
            "  AND abs(next.grid_x-here.grid_x) + abs(next.grid_y-here.grid_y) = 1 AND next.biome='WETLAND' " +
            "WHERE here.biome NOT IN ('WETLAND','OCEAN') ORDER BY here.grid_y, here.grid_x LIMIT 1", UUID.class);
        assertNotNull(beside, "the world must have dry ground beside a fen");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", beside, chronicle);
        // Put down everything, the way a destroyed object is put down: no live owner, and a recorded end.
        // The attachments go FIRST -- an equipment_attachment pointing at a destroyed item is an Auditor
        // violation ("N equipment attachment(s) reference an inactive item"), and it failed the whole suite.
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, lifecycle_state='DESTROYED', destroyed_at=now(), " +
            "destroyed_location_id=?, destroyed_cause='TEST_TEARDOWN' WHERE current_owner_id=? AND object_type='ITEM'",
            beside, chronicle);
        return beside;
    }

    @Test
    void theAnswerTurnsOnWhatIsOnYourBack() {
        UUID chronicle = awaken();
        standBesideAFen(chronicle);
        Instant now = ticks.current().simulatedAt();

        ChronicleActionService.ActionResult light = actions.resolve("can I cross here");
        assertEquals("JUDGE_CROSSING", light.intent(), () -> "the question is its own act: " + light.perception());
        assertEquals("SUCCEEDED", light.outcome(), () -> light.perception());
        assertTrue(light.perception().contains("could wade"),
            () -> "carrying nothing, the fen is wadeable and says so: " + light.perception());

        // Now load past three quarters of capacity, which is the fen's own limit.
        // The EFFECTIVE capacity, read exactly as the judgement reads it. The chronicle_carry_capacity column
        // is only the base: load conditioning, worn carry aids and a hitched draft beast all raise it, so a load
        // computed from the base could sit under three quarters of the real thing and prove nothing.
        int capacity = items.currentLoad(chronicle).sustainedMassCapacityGrams();
        assertTrue(capacity > 0, "a living Chronicle can carry something");
        // Past the fen's three quarters, but still inside what a body can carry: createCarriedItem asserts
        // the Chronicle can physically take what it is handed, and handing it more than capacity throws
        // "The Chronicle cannot physically carry that load" -- which is the assert doing its job, and was my
        // test asking for the impossible.
        int stones = (int) (capacity * 0.92 / 750);   // over the fen's three quarters, under the body's all
        for (int i = 0; i < stones; i++)
            items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");

        ChronicleActionService.ActionResult loaded = actions.resolve("can I cross here");
        assertEquals("SUCCEEDED", loaded.outcome(), () -> loaded.perception());
        assertTrue(loaded.perception().contains("Not with this load"),
            () -> "loaded, the same fen refuses and says why: " + loaded.perception());
        assertTrue(loaded.perception().contains("kg too much"),
            () -> "and names how much too much, which is the part a player can act on: " + loaded.perception());
    }

    @Test
    void askingNeverWadesAndTheActsBesideItAreUntouched() {
        UUID chronicle = awaken();
        UUID stood = standBesideAFen(chronicle);

        ChronicleActionService.ActionResult asked = actions.resolve("is it safe to cross");
        assertEquals("JUDGE_CROSSING", asked.intent(), () -> asked.perception());
        assertEquals(stood, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle),
            "asking whether you can cross must not cross — the same rule as asking whether water is safe (V392)");

        // Wading somewhere is still movement, and sounding a depth is still measuring.
        assertEquals("MOVE", actions.resolve("wade north").intent(), "a wade with a direction is still a move");
        assertEquals("MEASURE", actions.resolve("how deep is it").intent(), "sounding a depth is still measuring");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
