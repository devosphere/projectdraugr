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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Structures you raised stand here." (#37)
 *
 * <p>One sentence for a windbreak, and the same sentence for a byre, a well, a latrine and a drying rack standing
 * together. {@code construction_kind} has carried every one of their display names since the table existed, and
 * perception named none of them; nor did it ever say what state any of them was in, though
 * {@code integrity_percent} decides whether a shelter still shelters, a pen still holds and a latrine still takes
 * anything.
 *
 * <p>It also asked for {@code state='COMPLETED'} and never for integrity, which it now does. That part is
 * DEFENCE rather than a bug fixed: the Auditor treats a completed construction at zero integrity while still
 * active as an inconsistency, and the tick takes such a thing down in the same pass that wears it out, so the
 * state is one the world is not allowed to be in. Worth asking for all the same, and worth not writing prose
 * about.
 *
 * <p>And "take stock of the camp", which is exactly what a person says when they want this, reached nothing at
 * all. Skips without Docker.
 */
@SpringBootTest
class TheCampTheWorldWouldNotNameIntegrationTest {

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

    /**
     * The clock this class leaves behind.
     *
     * <p>Every action resolved here moves simulated time, and the suite shares one clock. A people whose store is
     * empty argue about leaving and their ELDERS are the voices to stay — so a class that quietly pushes the world
     * forward can starve the elders out of a community another class is about to ask a question of, and the
     * failure surfaces over there, in a test nobody touched. This class pins the clock and puts it back.
     */
    private java.sql.Timestamp clockWas;

    @org.junit.jupiter.api.BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", java.sql.Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
    }

    @org.junit.jupiter.api.AfterEach
    void unpinTheClock() {
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
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

    /**
     * Take down whatever stands here, the way the world takes a thing down.
     *
     * <p>This set {@code lifecycle_state='DESTROYED'} and left the location on the row, and the Auditor failed the
     * whole suite for it: <i>"4 destroyed object(s) still have an active location"</i>. That rule is one of this
     * project's oldest — an object is never deleted, but a destroyed one records where and why it ended and holds
     * no live location — and a fixture is not exempt from it.
     */
    private void clearGround(UUID chronicle) {
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=now(), " +
            "destroyed_location_id=current_location_id, destroyed_cause='TEST_TEARDOWN', " +
            "current_location_id=NULL, current_owner_id=NULL " +
            "WHERE current_location_id=? AND object_type='CONSTRUCTION'", where(chronicle));
    }

    /**
     * Raise a structure here, dated by the SIMULATED clock.
     *
     * <p>{@code construction_project.last_structural_update} defaults to {@code now()} — the wall clock — and the
     * decay pass wears a structure down by the days between that and simulated time. This class pins the world to
     * June 2031, so a fixture stamped with the wall clock arrives FIVE YEARS OLD and is driven straight to zero
     * integrity before it is ever looked at: the survey then correctly reports nothing standing, and the test
     * fails on its own assertions. Which is what it did, the moment the clock pin was added.
     */
    private void raise(UUID chronicle, String kind, String name, String state, int progress, int integrity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)", id, name, where(chronicle));
        if ("COMPLETED".equals(state))
            jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,integrity_percent,completed_at,last_structural_update) " +
                "SELECT ?,?,?,?,?,simulated_at,simulated_at FROM simulation_clock WHERE id=1",
                id, kind, state, progress, integrity);
        else
            jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,integrity_percent,last_structural_update) " +
                "SELECT ?,?,?,?,?,simulated_at FROM simulation_clock WHERE id=1",
                id, kind, state, progress, integrity);
    }

    @Test
    void theSurveyNamesWhatStandsAndSaysWhatStateItIsIn() {
        UUID chronicle = awaken();
        clearGround(chronicle);

        ChronicleActionService.ActionResult bare = actions.resolve("look around");
        assertEquals("SUCCEEDED", bare.outcome());
        assertFalse(bare.perception().contains("Your own work stands here"),
            () -> "empty ground claims no work: " + bare.perception());

        raise(chronicle, "LATRINE", "Camp latrine", "COMPLETED", 100, 100);
        raise(chronicle, "WELL", "Well", "COMPLETED", 100, 55);
        raise(chronicle, "DRYING_RACK", "Drying rack", "COMPLETED", 100, 20);

        ChronicleActionService.ActionResult built = actions.resolve("look around");
        assertEquals("SUCCEEDED", built.outcome());
        String said = built.perception().toLowerCase(java.util.Locale.ROOT);
        assertTrue(said.contains("camp latrine"), () -> "the latrine is named: " + built.perception());
        assertTrue(said.contains("well"), () -> "the well is named: " + built.perception());
        assertTrue(said.contains("drying rack"), () -> "the rack is named: " + built.perception());
        assertTrue(said.contains("weathered well"), () -> "and a weathered thing is said to be weathered: " + built.perception());
        assertTrue(said.contains("coming apart"), () -> "and one near failing is said to be: " + built.perception());
        assertFalse(built.perception().contains("Structures you raised stand here"),
            () -> "the blind sentence is gone: " + built.perception());

        // The survey also asks for integrity>0, so a ruin would not read as standing. That is defence rather
        // than a bug fixed: the Auditor treats a completed construction at zero integrity while still ACTIVE as
        // an inconsistency, and the tick takes such a thing down in the same pass that wears it out — so the
        // state is one the world is not allowed to be in, and this test does not manufacture one to prove a
        // sentence about it.
    }

    @Test
    void takingStockOfTheCampAccountsForEveryPartOfIt() {
        UUID chronicle = awaken();
        clearGround(chronicle);

        ChronicleActionService.ActionResult nothing = actions.resolve("take stock of the camp");
        assertEquals("SUCCEEDED", nothing.outcome(), () -> "it is a question, and it has an answer: " + nothing.perception());
        assertTrue(nothing.perception().contains("nothing of yours on it"),
            () -> "on bare ground the honest answer is that there is nothing: " + nothing.perception());

        raise(chronicle, "LATRINE", "Camp latrine", "COMPLETED", 100, 100);
        raise(chronicle, "WELL", "Well", "COMPLETED", 100, 55);
        raise(chronicle, "LOG_CABIN", "Log cabin", "IN_PROGRESS", 40, 100);

        ChronicleActionService.ActionResult stock = actions.resolve("take stock of the camp");
        assertEquals("SUCCEEDED", stock.outcome());
        String said = stock.perception().toLowerCase(java.util.Locale.ROOT);
        assertTrue(said.contains("camp latrine"), () -> "sound work is named: " + stock.perception());
        assertTrue(said.contains("well (weathered)"), () -> "weathered work carries its state: " + stock.perception());
        assertTrue(said.contains("still unfinished: log cabin (40"), () -> "and unfinished work with how far along: " + stock.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void takingStockIsNotTidyingAndTheStockIsNotTheAnimals() {
        awaken();
        // MAINTAIN_CAMP claims the camp with a tidying verb; this must not answer a stocktake, nor be answered by
        // one. And "stock" is the word for animals, so the two phrases that were already spoken for are asserted.
        assertEquals("MAINTAIN_CAMP", actions.resolve("tidy the camp").intent(), "tidying is still tidying");
        assertEquals("FEED_ANIMAL", actions.resolve("water the stock").intent(), "watering the stock is the beasts");
        assertEquals("FEED_ANIMAL", actions.resolve("feed the stock").intent(), "feeding the stock is the beasts");
        assertEquals("TAKE_STOCK_OF_CAMP", actions.resolve("take stock of the camp").intent(), "and taking stock is the camp");
        assertEquals("TAKE_STOCK_OF_CAMP", actions.resolve("what have I built here").intent(), "asked the other way round too");
    }
}
