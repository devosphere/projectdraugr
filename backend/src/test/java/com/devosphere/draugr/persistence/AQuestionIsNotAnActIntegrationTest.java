package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A question is not an act (#37).
 *
 * <p>Act ten:
 *
 * <pre>
 *   is the water safe to drink  ->  DRINK / SUCCEEDED
 *   "You drink from the marsh water. It eases the dryness, but it is not clean, and the gut will know it."
 * </pre>
 *
 * <p>The Chronicle asked <b>whether</b>, and the world answered by taking the risk. That is the worst shape a
 * wrong answer can take: the question is precisely an attempt to avoid the thing, and asking it incurred the
 * thing.
 *
 * <p>Every part of the honest answer already existed — whether the water moves, whether the ground above it is
 * fouled, whether a spring head is walled, whether a structure clears the draw, and what the water is called —
 * and none of it could be asked for without swallowing a mouthful first.
 *
 * <p>The assertion that matters is not the prose. It is that <b>asking changes nothing</b>. Skips without Docker.
 */
@SpringBootTest
class AQuestionIsNotAnActIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    private UUID where(UUID chronicle) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
    }

    private BigDecimal dryness(UUID chronicle) {
        return jdbc.queryForObject("SELECT hours_without_water FROM chronicle_physiology WHERE chronicle_id=?", BigDecimal.class, chronicle);
    }

    private int illness(UUID chronicle) {
        Integer n = jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void askingWhetherTheWaterIsSafeDrinksNothing() {
        UUID chronicle = awaken();
        jdbc.update("UPDATE world_chunk SET biome='WETLAND' WHERE id=?", where(chronicle));
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", where(chronicle));

        // Made properly thirsty first, or the assertion below is vacuous: a Chronicle who has just woken is
        // already at nothing, and "it did not go down" is true whether they drank or not.
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=5 WHERE chronicle_id=?", chronicle);
        BigDecimal dryBefore = dryness(chronicle);
        int illBefore = illness(chronicle);
        assertTrue(dryBefore.compareTo(BigDecimal.ZERO) > 0, "the body must be thirsty for this to prove anything");

        ChronicleActionService.ActionResult asked = actions.resolve("is the water safe to drink");
        assertEquals("JUDGE_WATER", asked.intent(), () -> "asking is its own act: " + asked.perception());
        assertEquals("SUCCEEDED", asked.outcome(), () -> "and it has an answer: " + asked.perception());

        // THE point. Before this, asking drank the marsh water and took the waterborne risk with it.
        //
        // Asserted as "did not SLAKE", not "did not change": the act takes three minutes of simulated time and
        // a body goes on drying out while it stands there, so hours_without_water creeps UP. Drinking is the
        // thing that sets it to zero, and that is what must not have happened.
        assertTrue(dryness(chronicle).compareTo(dryBefore) >= 0,
            () -> "asking whether water is safe must not slake thirst — it drank it before (was " + dryBefore
                + ", now " + dryness(chronicle) + ")");
        assertEquals(illBefore, illness(chronicle),
            "and must not take the risk the asker was trying to avoid");

        // Standing water is judged as standing water.
        assertTrue(asked.perception().contains("lies still"),
            () -> "still water is described as still: " + asked.perception());
        assertTrue(asked.perception().contains("Boiled"),
            () -> "and the answer ends where certainty does, which is at boiling: " + asked.perception());
    }

    @Test
    void runningWaterFouledGroundAndDryGroundAreThreeDifferentAnswers() {
        UUID chronicle = awaken();
        UUID chunk = where(chronicle);

        jdbc.update("UPDATE world_chunk SET biome='RIVER_BANK' WHERE id=?", chunk);
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        ChronicleActionService.ActionResult running = actions.resolve("is the water safe to drink");
        assertTrue(running.perception().contains("It runs"),
            () -> "running water is the best draw this ground offers: " + running.perception());

        // Refuse is dated by the SIMULATION clock, not by now(): the tick drains it by the hours elapsed between
        // last_updated_at and simulated time, so a row stamped with the wall clock is drained to nothing the
        // moment the world turns. That is how this fixture first failed to foul anything at all.
        jdbc.update("UPDATE world_chunk SET biome='WETLAND' WHERE id=?", chunk);
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) " +
            "SELECT ?, 70, simulated_at FROM simulation_clock WHERE id=1", chunk);
        ChronicleActionService.ActionResult fouled = actions.resolve("is the water safe to drink");
        assertTrue(fouled.perception().contains("camp above it is in a state"),
            () -> "a fouled camp is named as the reason: " + fouled.perception());

        // And where there is no water, it says that rather than judging nothing.
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk);
        // Only the WATER sites, and only those nothing lives on: a wildlife population holds a foreign key to
        // its range, so clearing every site on a chunk fails outright wherever the world put animals.
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND site_category='RESOURCE' " +
            "AND NOT EXISTS (SELECT 1 FROM wildlife_population wp WHERE wp.site_id=ecology_site.id)", chunk);
        jdbc.update("UPDATE construction_project SET integrity_percent=0 WHERE object_id IN " +
            "(SELECT id FROM world_object WHERE current_location_id=?)", chunk);
        ChronicleActionService.ActionResult dry = actions.resolve("is the water safe to drink");
        assertEquals("FAILED", dry.outcome(), () -> "there is nothing here to judge: " + dry.perception());
        assertTrue(dry.perception().contains("no water here to judge"),
            () -> "and it says so plainly: " + dry.perception());
    }

    @Test
    void theQuestionTakesNothingFromDrinkingOrBoiling() {
        UUID chronicle = awaken();
        jdbc.update("UPDATE world_chunk SET biome='RIVER_BANK' WHERE id=?", where(chronicle));

        assertEquals("JUDGE_WATER", actions.resolve("is the water safe to drink").intent());
        assertEquals("JUDGE_WATER", actions.resolve("can I drink this water").intent());
        assertEquals("JUDGE_WATER", actions.resolve("is this water clean").intent());

        // The acts they sit beside are untouched: a question about drinking is not a refusal to drink.
        ChronicleActionService.ActionResult drank = actions.resolve("drink");
        assertEquals("DRINK", drank.intent(), () -> "drinking still drinks: " + drank.perception());
        assertEquals("SUCCEEDED", drank.outcome(), () -> "at a river: " + drank.perception());
        assertEquals(0, dryness(chronicle).compareTo(BigDecimal.ZERO), "and it slakes the thirst it always did");

        assertEquals("BOIL_WATER", actions.resolve("boil some water").intent(), "boiling is still boiling");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
