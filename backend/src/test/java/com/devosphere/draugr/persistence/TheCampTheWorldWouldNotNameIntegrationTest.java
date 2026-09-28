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
 * <p>It also counted a RUIN as standing — it asked for {@code state='COMPLETED'} and never for integrity — so a
 * Chronicle could walk into their own collapsed camp and be told everything was fine.
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

    private void clearGround(UUID chronicle) {
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED' WHERE current_location_id=? AND object_type='CONSTRUCTION'", where(chronicle));
    }

    private void raise(UUID chronicle, String kind, String name, String state, int progress, int integrity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)", id, name, where(chronicle));
        if ("COMPLETED".equals(state))
            jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,integrity_percent,completed_at) VALUES (?,?,?,?,?,now())",
                id, kind, state, progress, integrity);
        else
            jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,integrity_percent) VALUES (?,?,?,?,?)",
                id, kind, state, progress, integrity);
    }

    @Test
    void theSurveyNamesWhatStandsAndDoesNotCountARuinAsStanding() {
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

        // A ruin is not standing. HIDE_FRAME is a workstation, which is a kind the tick does NOT sweep away at
        // zero integrity — the others collapse and are destroyed outright, so a lingering ruin can only be one of
        // these. Chosen deliberately, so this asserts a state the world can actually be in.
        clearGround(chronicle);
        raise(chronicle, "HIDE_FRAME", "Hide frame", "COMPLETED", 100, 0);
        ChronicleActionService.ActionResult ruined = actions.resolve("look around");
        assertFalse(ruined.perception().contains("Your own work stands here"),
            () -> "a collapsed frame does not stand: " + ruined.perception());
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
        raise(chronicle, "HIDE_FRAME", "Hide frame", "COMPLETED", 100, 0);
        raise(chronicle, "LOG_CABIN", "Log cabin", "IN_PROGRESS", 40, 100);

        ChronicleActionService.ActionResult stock = actions.resolve("take stock of the camp");
        assertEquals("SUCCEEDED", stock.outcome());
        String said = stock.perception().toLowerCase(java.util.Locale.ROOT);
        assertTrue(said.contains("camp latrine"), () -> "sound work is named: " + stock.perception());
        assertTrue(said.contains("well (weathered)"), () -> "weathered work carries its state: " + stock.perception());
        assertTrue(said.contains("past mending: hide frame"), () -> "a ruin is named as a ruin: " + stock.perception());
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
