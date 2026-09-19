package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Notes on an isle (#112): recording a people's custom writes a real document, and keeps adding to it.
 *
 * <p>With charcoal and a bark sheet, "record their custom" makes "Notes on" the isle, a literature document on that
 * sheet; a second visit appends to the same document rather than starting another. Without charcoal nothing is
 * written, and the narration says the Chronicle keeps it in their head. Skips without Docker.
 */
@SpringBootTest
class NotesOnAnIsleIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockBefore;

    @BeforeEach
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    private void at(String iso) {
        Timestamp t = Timestamp.from(Instant.parse(iso));
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", t);
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", t);
    }

    @Test
    void recordingACustomWritesItDownAndKeepsAddingToTheSameNotes() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        at("2031-06-10T12:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        String isle = jdbc.queryForObject("SELECT name FROM native_community WHERE id=?", String.class, community);
        UUID home = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T12:00:00Z")), community);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", home, chronicle);

        // Without anything to write with, it is kept in memory, and said so.
        var remembered = actions.resolve("record their custom");
        assertTrue(remembered.perception().contains("in your head"), remembered::perception);
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM literature_document WHERE title=?", Integer.class, "Notes on " + isle));

        // With charcoal and a bark sheet, it becomes a document on that sheet.
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", Instant.now(), "TEST_FIXTURE");
        UUID bark = items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", Instant.now(), "TEST_FIXTURE");
        at("2031-06-11T12:00:00Z");
        actions.resolve("record their custom");
        List<UUID> notes = jdbc.queryForList("SELECT object_id FROM literature_document WHERE title=?", UUID.class, "Notes on " + isle);
        assertEquals(List.of(bark), notes, "the notes are written on the bark sheet the Chronicle carried");

        // Another visit adds to the same notes rather than starting new ones.
        at("2031-06-13T12:00:00Z");
        actions.resolve("record their custom");
        assertEquals(2, (int) jdbc.queryForObject(
            "SELECT r.revision_number FROM literature_document d JOIN literature_revision r ON r.id=d.current_revision_id WHERE d.object_id=?",
            Integer.class, bark), "a second visit appends a second revision");
        String content = jdbc.queryForObject(
            "SELECT r.content FROM literature_document d JOIN literature_revision r ON r.id=d.current_revision_id WHERE d.object_id=?", String.class, bark);
        assertTrue(content.contains("announced") || content.contains("call out"), () -> "what is written is what was witnessed: " + content);
    }
}
